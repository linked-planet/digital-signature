package com.baloise.confluence.digitalsignature

import com.atlassian.sal.api.user.UserKey
import com.atlassian.sal.api.user.UserProfile
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.net.URI

internal class DigitalSignatureMacroTest {
    private val signature = Signature2(1, "test", "title")

    @Test
    fun mailtoLong(): Unit {
        val profiles: MutableList<UserProfile> = ArrayList()
        val profile = buildUserProfile("Heinz Meier", "heinz.meier@meier.com")
        for (i in 0..19) {
            profiles.add(profile)
        }

        val mailto = MailtoBuilder.build(profiles, "Subject", true, null, "test")

        Assertions.assertEquals(
            "mailto:heinz.meier@meier.com,heinz.meier@meier.com,heinz.meier@meier.com,heinz.meier@meier.com,heinz.meier@meier.com,heinz.meier@meier.com,heinz.meier@meier.com,heinz.meier@meier.com,heinz.meier@meier.com,heinz.meier@meier.com,heinz.meier@meier.com,heinz.meier@meier.com,heinz.meier@meier.com,heinz.meier@meier.com,heinz.meier@meier.com,heinz.meier@meier.com,heinz.meier@meier.com,heinz.meier@meier.com,heinz.meier@meier.com,heinz.meier@meier.com?Subject=Subject",
            mailto
        )
    }

    @Test
    fun mailtoVeryLong(): Unit {
        val profiles: MutableList<UserProfile> = ArrayList()
        val profile = buildUserProfile("Heinz Meier", "heinz.meier@meier.com")
        for (i in 0..199) {
            profiles.add(profile)
        }

        val mailto = MailtoBuilder.build(profiles, "Subject", true, signature, "nirvana")

        Assertions.assertEquals(
            "nirvana/rest/signature/1.0/emails?key=signature.3224a4d6bba68cd0ece9b64252f8bf5677e24cf6b7c5f543e3176d419d34d517&signed=true",
            mailto
        )
    }

    @Test
    fun mailtoShort(): Unit {
        val profiles: MutableList<UserProfile> = ArrayList()
        val profile = buildUserProfile("Heinz Meier", "heinz.meier@meier.com")
        profiles.add(profile)

        val mailto = MailtoBuilder.build(profiles, "Subject", true, null, "test")

        Assertions.assertEquals(
            "mailto:Heinz Meier<heinz.meier@meier.com>?Subject=Subject",
            mailto
        )
    }

    fun buildUserProfile(userName: String, userEmail: String)= object : UserProfile {
            override fun getFullName(): String = userName
            override fun getEmail(): String = userEmail

            override fun getUserKey(): UserKey {
                throw UnsupportedOperationException("Not yet implemented")
            }
            override fun getUsername(): String {
                throw UnsupportedOperationException("Not yet implemented")
            }
            override fun getProfilePictureUri(p0: Int, p1: Int): URI {
                throw UnsupportedOperationException("Not yet implemented")
            }
            override fun getProfilePictureUri(): URI {
                throw UnsupportedOperationException("Not yet implemented")
            }
            override fun getProfilePageUri(): URI {
                throw UnsupportedOperationException("Not yet implemented")
            }
        }
}
