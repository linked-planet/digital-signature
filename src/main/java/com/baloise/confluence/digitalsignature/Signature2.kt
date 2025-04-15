package com.baloise.confluence.digitalsignature

import com.atlassian.bandana.BandanaManager
import com.atlassian.confluence.setup.bandana.ConfluenceBandanaContext
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import org.apache.commons.codec.digest.DigestUtils
import org.apache.logging.log4j.LogManager
import java.io.Serializable
import java.util.*
import kotlin.collections.HashMap

class Signature2(var pageId: Long, private val body: String, var title: String) : Serializable {
    var hash = DigestUtils.sha256Hex("$pageId:$title:$body")
    var key = "signature.$hash"
    val protectedKey: String = "protected.$hash"
    var maxSignatures: Long = -1
    var visibilityLimit: Long = -1
    var signatures: Map<String, Date> = HashMap()
    var missingSignatures: Set<String> = TreeSet()
    var notify: Set<String> = TreeSet()

    fun serialize(): String {
        return GSON.toJson(this, Signature2::class.java)
    }

    override fun hashCode(): Int {
        return (31 * 1 + key.hashCode())
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null) return false
        if (javaClass != other.javaClass) return false

        return key == (other as Signature2).key
    }

    fun withNotified(notified: Set<String>): Signature2 {
        this.notify = notified
        return this
    }

    fun withMaxSignatures(maxSignatures: Long): Signature2 {
        this.maxSignatures = maxSignatures
        return this
    }

    fun withVisibilityLimit(visibilityLimit: Long): Signature2 {
        this.visibilityLimit = visibilityLimit
        return this
    }

    fun hasSigned(userName: String): Boolean {
        return signatures.containsKey(userName)
    }

    val isPetitionMode: Boolean
        get() = isPetitionMode(missingSignatures)

    fun sign(userName: String): Boolean {
        if (!isMaxSignaturesReached && !isPetitionMode && !missingSignatures.contains(userName)) {
            return false
        }

        missingSignatures = missingSignatures - setOf(userName)
        signatures = signatures.plus(Pair(userName, Date()))
        return true
    }

    val isMaxSignaturesReached: Boolean
        get() = maxSignatures > -1 && maxSignatures <= signatures.size

    fun isSignatureMissing(userName: String): Boolean {
        return !isMaxSignaturesReached && !hasSigned(userName) && isSignatory(userName)
    }

    fun isSignatory(userName: String?): Boolean {
        return isPetitionMode || missingSignatures.contains(userName)
    }

    fun hasMissingSignatures(): Boolean {
        return !isMaxSignaturesReached && (isPetitionMode || missingSignatures.isNotEmpty())
    }

    fun save(bandanaManager: BandanaManager) {
        if (hasMissingSignatures()) {
            toBandana(bandanaManager, this)
        }
    }

    companion object {
        val log = LogManager.getLogger(Signature2::class.java)
        val GSON: Gson = GsonBuilder().setDateFormat("yyyy-MM-dd'T'HH:mm:ssz").create()
        private const val serialVersionUID = 1L
        fun isPetitionMode(userGroups: Set<String>?): Boolean {
            return userGroups != null && userGroups.size == 1 && userGroups.iterator().next().trim { it <= ' ' } == "*"
        }

        @JvmStatic
        fun deserialize(serialization: String?): Signature2? {
            return serialization?.let { GSON.fromJson(it, Signature2::class.java) }
        }

        @JvmStatic
        fun fromBandana(value: Any?): Pair<Signature2?,Boolean> {
            when(value) {
                is Signature -> {
                    // required for backwards compatibility - update for next time.
                    val signature = value
                    val sig = Signature2(signature.pageId ?:-1, signature.body ?: "", signature.title ?: "")
                    sig.signatures = signature.signatures.orEmpty()
                    sig.maxSignatures = signature.maxSignatures ?: 0
                    sig.hash = signature.hash ?: ""
                    sig.key = signature.key ?: ""
                    sig.notify = signature.notify.orEmpty()
                    sig.missingSignatures = signature.missingSignatures.orEmpty()
                    sig.visibilityLimit = signature.visibilityLimit ?: 0
                    return Pair(sig, true)
                }
                is String -> {
                    try {
                        return Pair(deserialize(value), false)
                    } catch (e: Exception) {
                        log.error("Could not deserialize String value from Bandana", e)
                        return Pair(null,false)
                    }
                }
                else -> {
                    require(false) { "Received strange value from Bandana" }
                    return Pair(null,false)
                }
            }
        }

        fun toBandana(mgr: BandanaManager, key: String?, sig: Signature2) {
            mgr.setValue(ConfluenceBandanaContext.GLOBAL_CONTEXT, key, sig.serialize())
        }

        @JvmStatic
        fun toBandana(mgr: BandanaManager, sig: Signature2) {
            toBandana(mgr, sig.key, sig)
        }
    }
}
