package com.baloise.confluence.digitalsignature

import org.apache.commons.codec.digest.DigestUtils
import java.io.Serializable
import java.util.*

/**
 * This class is deprecated and should no longer be used except for downwards compatibility, i.e. reading values from
 * Bandana that were written with an older version.
 * <br></br>
 * Use @[com.baloise.confluence.digitalsignature.Signature2] instead.
 */
@Deprecated("")
class Signature : Serializable, Cloneable {
    var key: String? = ""
    var hash: String? = ""
    var pageId: Long = 0L
    var title: String? = ""
    var body: String = ""
    var maxSignatures: Long? = -1L
    var visibilityLimit: Long? = -1L
    @JvmField
    var signatures: MutableMap<String, Date>? = HashMap()
    @JvmField
    var missingSignatures: MutableSet<String>? = TreeSet()
    var notify: Set<String>? = TreeSet()

    constructor()

    constructor(pageId: Long, body: String, title: String?) {
        this.pageId = pageId
        this.body = body
        this.title = title ?: ""
        hash = DigestUtils.sha256Hex("$pageId:$title:$body")
        key = "signature.$hash"
    }

    val protectedKey: String
        get() = "protected.$hash"

    override fun hashCode(): Int {
        val prime = 31
        var result = 1
        result = prime * result + (if (key == null) 0 else key.hashCode())
        return result
    }

    override fun equals(obj: Any?): Boolean {
        if (this === obj) return true
        if (obj == null) return false
        if (javaClass != obj.javaClass) return false
        val other = obj as Signature
        return if (key == null) {
            other.key == null
        } else key == other.key
    }

    fun withNotified(notified: Set<String>): Signature {
        this.notify = notified
        return this
    }

    fun withMaxSignatures(maxSignatures: Long): Signature {
        this.maxSignatures = maxSignatures
        return this
    }

    fun withVisibilityLimit(visibilityLimit: Long): Signature {
        this.visibilityLimit = visibilityLimit
        return this
    }

    fun hasSigned(userName: String): Boolean {
        return signatures?.containsKey(userName) ?: false
    }

    val isPetitionMode: Boolean
        get() = isPetitionMode(missingSignatures)

    fun sign(userName: String): Boolean {
        if (!isMaxSignaturesReached && !isPetitionMode && missingSignatures?.remove(userName) != true) {
            return false
        } else {
            signatures?.set(userName, Date())
            return true
        }
    }

    val isMaxSignaturesReached: Boolean
        get() = maxSignatures!! > -1 && maxSignatures!! <= (signatures?.size ?: 0)

    fun isSignatureMissing(userName: String): Boolean {
        return !isMaxSignaturesReached && !hasSigned(userName) && isSignatory(userName)
    }

    fun isSignatory(userName: String): Boolean {
        return isPetitionMode || missingSignatures?.contains(userName) ?: false
    }

    fun hasMissingSignatures(): Boolean {
        return !isMaxSignaturesReached && (isPetitionMode || missingSignatures?.isNotEmpty() ?: false)
    }

    @Throws(CloneNotSupportedException::class)
    public override fun clone(): Signature {
        return super.clone() as Signature
    }

    companion object {
        private const val serialVersionUID = 1L

        fun isPetitionMode(userGroups: Set<String>?): Boolean {
            return userGroups != null && userGroups.size == 1 && userGroups.iterator().next().trim { it <= ' ' } == "*"
        }
    }
}
