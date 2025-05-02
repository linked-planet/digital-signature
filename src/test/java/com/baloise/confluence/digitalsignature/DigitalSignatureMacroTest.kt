package com.baloise.confluence.digitalsignature

import com.atlassian.bandana.BandanaContext
import com.atlassian.bandana.BandanaManager
import com.atlassian.config.ApplicationConfiguration
import com.atlassian.config.db.DatabaseDetails
import com.atlassian.config.db.HibernateConfig
import com.atlassian.config.db.HibernateConfigurator
import com.atlassian.config.setup.SetupPersister
import com.atlassian.confluence.setup.BootstrapManager
import com.atlassian.confluence.setup.SharedConfigurationMap
import com.atlassian.sal.api.user.UserKey
import com.atlassian.sal.api.user.UserProfile
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.io.File
import java.net.URI
import java.sql.Connection
import java.util.*
import kotlin.collections.ArrayList

internal class DigitalSignatureMacroTest {
    private val signature = Signature2(1, "test", "title")
    private val bandana: BandanaManager = object : BandanaManager {
        private val content = mutableMapOf<String,Any>()
        override fun init() {}

        override fun setValue(p0: BandanaContext?, p1: String?, p2: Any?) {
            content[p1!!] = p2!!
        }

        override fun getValue(p0: BandanaContext?, p1: String?): Any? {
            return content[p1!!]
        }

        override fun getValue(p0: BandanaContext?, p1: String?, p2: Boolean): Any? {
            return content[p1!!]
        }

        override fun getKeys(p0: BandanaContext?): MutableIterable<String> {
            return content.keys
        }

        override fun removeValue(p0: BandanaContext?, p1: String?) {
            content.remove(p1)
        }

    }

    @Test
    fun mailtoLong(): Unit {
        val macro = DigitalSignatureMacro()
        val profiles: MutableList<UserProfile> = ArrayList()
        val profile = buildUserProfile("Heinz Meier", "heinz.meier@meier.com")
        for (i in 0..19) {
            profiles.add(profile)
        }

        val mailto = macro.getMailto(profiles, "Subject", true, null)

        Assertions.assertEquals(
            "mailto:heinz.meier@meier.com,heinz.meier@meier.com,heinz.meier@meier.com,heinz.meier@meier.com,heinz.meier@meier.com,heinz.meier@meier.com,heinz.meier@meier.com,heinz.meier@meier.com,heinz.meier@meier.com,heinz.meier@meier.com,heinz.meier@meier.com,heinz.meier@meier.com,heinz.meier@meier.com,heinz.meier@meier.com,heinz.meier@meier.com,heinz.meier@meier.com,heinz.meier@meier.com,heinz.meier@meier.com,heinz.meier@meier.com,heinz.meier@meier.com?Subject=Subject",
            mailto
        )
    }

    @Test
    fun mailtoVeryLong(): Unit {
        val macro =
            DigitalSignatureMacro()
        val profiles: MutableList<UserProfile> = ArrayList()
        val profile = buildUserProfile("Heinz Meier", "heinz.meier@meier.com")
        for (i in 0..199) {
            profiles.add(profile)
        }

        val mailto = macro.getMailto(profiles, "Subject", true, signature, "nirvana")

        Assertions.assertEquals(
            "nirvana/rest/signature/1.0/emails?key=signature.3224a4d6bba68cd0ece9b64252f8bf5677e24cf6b7c5f543e3176d419d34d517&signed=true",
            mailto
        )
    }

    @Test
    fun mailtoShort(): Unit {
        val macro = DigitalSignatureMacro()
        val profiles: MutableList<UserProfile> = ArrayList()
        val profile = buildUserProfile("Heinz Meier", "heinz.meier@meier.com")
        profiles.add(profile)

        val mailto = macro.getMailto(profiles, "Subject", true, null)

        Assertions.assertEquals(
            "mailto:Heinz Meier<heinz.meier@meier.com>?Subject=Subject",
            mailto
        )
    }

    fun buildUserProfile(userName: String, userEmail: String)= object : UserProfile {
            override fun getFullName(): String = userName
            override fun getEmail(): String = userEmail

            override fun getUserKey(): UserKey {
                TODO("Not yet implemented")
            }
            override fun getUsername(): String {
                TODO("Not yet implemented")
            }
            override fun getProfilePictureUri(p0: Int, p1: Int): URI {
                TODO("Not yet implemented")
            }
            override fun getProfilePictureUri(): URI {
                TODO("Not yet implemented")
            }
            override fun getProfilePageUri(): URI {
                TODO("Not yet implemented")
            }
        }
}
