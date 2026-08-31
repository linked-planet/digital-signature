package com.baloise.confluence.digitalsignature

import com.atlassian.sal.api.user.UserProfile
import java.io.UnsupportedEncodingException
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/** Builds the notification link rendered by the signature macro. */
internal object MailtoBuilder {
    private const val MAX_MAILTO_CHARACTER_COUNT = 500
    private const val REST_PATH = "/rest/signature/1.0"

    private val contextHelper = ContextHelper()

    fun build(
        profiles: Collection<UserProfile>?,
        subject: String,
        signed: Boolean,
        signature: Signature2?,
        contextPath: String,
    ): String? {
        if (profiles.isNullOrEmpty()) return null

        val profilesWithMail = profiles.filter { profile -> contextHelper.hasEmail(profile) }
        val ret = StringBuilder("mailto:")
        for (profile in profilesWithMail) {
            if (ret.length > 7) ret.append(',')
            ret.append(contextHelper.mailTo(profile))
        }
        ret.append("?Subject=").append(urlEncode(subject))
        if (ret.length > MAX_MAILTO_CHARACTER_COUNT) {
            ret.setLength(0)
            ret.append("mailto:")
            for (profile in profilesWithMail) {
                if (ret.length > 7) ret.append(',')
                ret.append(profile.email.trim { it <= ' ' })
            }
            ret.append("?Subject=").append(urlEncode(subject))
        }
        if (ret.length > MAX_MAILTO_CHARACTER_COUNT) {
            return contextPath + REST_PATH + "/emails?key=" + signature?.key + "&signed=" + signed
        }
        return ret.toString()
    }

    private fun urlEncode(string: String): String {
        try {
            return URLEncoder.encode(string, StandardCharsets.UTF_8.name())
        } catch (e: UnsupportedEncodingException) {
            throw IllegalStateException(e)
        }
    }
}
