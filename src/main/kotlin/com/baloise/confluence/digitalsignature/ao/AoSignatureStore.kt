package com.baloise.confluence.digitalsignature.ao

import com.atlassian.activeobjects.external.ActiveObjects
import com.baloise.confluence.digitalsignature.Signature2
import net.java.ao.DBParam
import net.java.ao.Query
import org.apache.logging.log4j.LogManager

/**
 * Thrown when an AO row exists for [key] but its payload cannot be deserialized.
 * Callers must not treat this as a missing key (that would re-seed and wipe history).
 */
class CorruptSignaturePayloadException(
    val key: String,
    cause: Throwable? = null,
) : IllegalStateException("Could not deserialize AO payload for key '$key'", cause)

/**
 * [SignatureStore] backed by Active Objects, with optional Bandana read fallback.
 *
 * The lambda constructor is for unit tests (in-memory map). Production uses [ActiveObjects].
 */
class AoSignatureStore internal constructor(
    private val findInAo: (String) -> Signature2?,
    private val upsertAo: (String, Signature2) -> Unit,
    private val bandanaFallback: BandanaFallback? = null,
) : SignatureStore {

    /**
     * @param ao plugin-scoped Active Objects
     * @param bandanaFallback C10 Bandana reads; `null` on Confluence 11
     */
    constructor(ao: ActiveObjects, bandanaFallback: BandanaFallback? = null) : this(
        findInAo = { key -> findEntity(ao, key) },
        upsertAo = { key, sig -> upsertEntity(ao, key, sig) },
        bandanaFallback = bandanaFallback,
    )

    /**
     * @param key Bandana / [Signature2.key] value
     * @return AO row, or a Bandana value copied into AO on miss, or `null` if neither has data
     * @throws CorruptSignaturePayloadException if an AO row exists but cannot be deserialized
     */
    override fun get(key: String): Signature2? {
        findInAo(key)?.let { return it }
        return copyFromBandana(key)
    }

    /**
     * @param key Bandana / [Signature2.key] value
     * @param sig value to persist
     */
    override fun put(key: String, sig: Signature2) {
        upsertAo(key, sig)
    }

    /**
     * @param key Bandana / [Signature2.key] value
     * @return the AO row as [Signature2], or `null` if missing
     * @throws CorruptSignaturePayloadException if the AO row exists but cannot be deserialized
     */
    override fun getFromAo(key: String): Signature2? = findInAo(key)

    /**
     * Reads Bandana for [key] and, on success, copies into AO. Null Bandana is a miss (no warn).
     */
    private fun copyFromBandana(key: String): Signature2? {
        val fallback = bandanaFallback ?: return null
        val value = fallback.getValue(key) ?: return null
        val (sig, _) = Signature2.fromPersistedValue(value)
        if (sig != null) {
            upsertAo(key, sig)
        }
        return sig
    }

    companion object {
        private val log = LogManager.getLogger(AoSignatureStore::class.java)

        private const val COLUMN_SIGNATURE_KEY = "SIGNATURE_KEY"
        private const val COLUMN_PAGE_ID = "PAGE_ID"
        private const val COLUMN_PAYLOAD = "PAYLOAD"

        /**
         * @param ao plugin-scoped Active Objects
         * @param key Bandana / [Signature2.key] value
         * @return deserialized AO payload, or `null` if no row
         * @throws CorruptSignaturePayloadException if a row exists but payload is unreadable
         */
        private fun findEntity(ao: ActiveObjects, key: String): Signature2? =
            ao.executeInTransaction {
                val rows = ao.find(
                    SignatureEntity::class.java,
                    Query.select().where("$COLUMN_SIGNATURE_KEY = ?", key)
                )
                val entity = rows.firstOrNull() ?: return@executeInTransaction null
                parseAoPayload(key, entity.payload)
            }

        /**
         * @param key signature key (for error context)
         * @param payload AO PAYLOAD column
         * @return deserialized signature
         * @throws CorruptSignaturePayloadException if payload is null-result or throws
         */
        internal fun parseAoPayload(key: String, payload: String?): Signature2 {
            try {
                return Signature2.deserialize(payload)
                    ?: throw CorruptSignaturePayloadException(key)
            } catch (e: CorruptSignaturePayloadException) {
                log.error("Could not deserialize AO payload for key '{}'", key)
                throw e
            } catch (e: RuntimeException) {
                log.error("Could not deserialize AO payload for key '{}'", key)
                throw CorruptSignaturePayloadException(key, e)
            }
        }

        /**
         * @param ao plugin-scoped Active Objects
         * @param key Bandana / [Signature2.key] value
         * @param sig value to persist
         */
        private fun upsertEntity(ao: ActiveObjects, key: String, sig: Signature2) {
            ao.executeInTransaction<Void?> {
                val existing = ao.find(
                    SignatureEntity::class.java,
                    Query.select().where("$COLUMN_SIGNATURE_KEY = ?", key)
                ).firstOrNull()
                if (existing != null) {
                    writeEntity(existing, key, sig)
                    return@executeInTransaction null
                }
                try {
                    val created = ao.create(
                        SignatureEntity::class.java,
                        DBParam(COLUMN_SIGNATURE_KEY, key),
                        DBParam(COLUMN_PAGE_ID, sig.pageId),
                        DBParam(COLUMN_PAYLOAD, sig.serialize())
                    )
                    writeEntity(created, key, sig)
                } catch (e: RuntimeException) {
                    // Concurrent create hit @Unique — reload and update instead of failing the request.
                    val raced = ao.find(
                        SignatureEntity::class.java,
                        Query.select().where("$COLUMN_SIGNATURE_KEY = ?", key)
                    ).firstOrNull()
                    if (raced == null) {
                        throw e
                    }
                    log.debug(
                        "AO unique race on signature key '{}'; updating existing row",
                        key
                    )
                    writeEntity(raced, key, sig)
                }
                null
            }
        }

        private fun writeEntity(entity: SignatureEntity, key: String, sig: Signature2) {
            entity.signatureKey = key
            entity.pageId = sig.pageId
            entity.payload = sig.serialize()
            entity.save()
        }
    }
}
