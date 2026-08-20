package com.baloise.confluence.digitalsignature.ao

import com.baloise.confluence.digitalsignature.Signature2

/**
 * Persistence for [Signature2]. Active Objects is the live store; Bandana is a C10 read fallback
 * only, isolated here so Confluence 11 can drop it.
 */
interface SignatureStore {
    /**
     * Loads a signature by key. Checks AO first; on a miss only, copies from Bandana (JSON or
     * legacy XStream [com.baloise.confluence.digitalsignature.Signature]) into AO when a fallback
     * is configured. Corrupt AO rows fail closed — Bandana is not consulted for repair.
     *
     * @param key Bandana / [Signature2.key] value, e.g. `signature.<sha256>`
     * @return the stored signature, or `null` if neither AO nor Bandana has a readable value
     * @throws CorruptSignaturePayloadException if an AO row exists but cannot be deserialized
     */
    fun get(key: String): Signature2?

    /**
     * Upserts the signature JSON into AO. Never writes Bandana.
     *
     * @param key Bandana / [Signature2.key] value
     * @param sig value to persist (serialized with [Signature2.serialize])
     */
    fun put(key: String, sig: Signature2)

    /**
     * AO-only lookup used by the upgrade task for idempotent counts. Does not consult Bandana.
     *
     * @param key Bandana / [Signature2.key] value
     * @return the AO row as [Signature2], or `null` if missing
     * @throws CorruptSignaturePayloadException if a row exists but cannot be deserialized
     */
    fun getFromAo(key: String): Signature2?
}
