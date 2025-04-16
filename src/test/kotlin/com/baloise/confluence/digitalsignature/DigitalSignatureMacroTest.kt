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
    private val bootstrapManager: BootstrapManager = object : BootstrapManager {
        override fun isBootstrapped(): Boolean {
            TODO("Not yet implemented")
        }

        override fun getProperty(p0: String?): Any {
            TODO("Not yet implemented")
        }

        override fun setProperty(p0: String?, p1: Any?) {
            TODO("Not yet implemented")
        }

        override fun isPropertyTrue(p0: String?): Boolean {
            TODO("Not yet implemented")
        }

        override fun removeProperty(p0: String?) {
            TODO("Not yet implemented")
        }

        override fun getString(p0: String?): String {
            TODO("Not yet implemented")
        }

        override fun getFilePathProperty(key: String?): String {
            TODO("Not yet implemented")
        }

        override fun getPropertyKeys(): MutableCollection<Any?> {
            TODO("Not yet implemented")
        }

        override fun getPropertiesWithPrefix(p0: String?): MutableMap<Any?, Any?> {
            TODO("Not yet implemented")
        }

        override fun getBuildNumber(): String {
            TODO("Not yet implemented")
        }

        override fun setBuildNumber(p0: String?) {
            TODO("Not yet implemented")
        }

        override fun isApplicationHomeValid(): Boolean {
            TODO("Not yet implemented")
        }

        override fun getHibernateProperties(): Properties {
            TODO("Not yet implemented")
        }

        override fun save() {
            TODO("Not yet implemented")
        }

        override fun isSetupComplete(): Boolean {
            TODO("Not yet implemented")
        }

        override fun setSetupComplete(p0: Boolean) {
            TODO("Not yet implemented")
        }

        override fun getOperation(): String {
            TODO("Not yet implemented")
        }

        override fun setOperation(p0: String?) {
            TODO("Not yet implemented")
        }

        override fun bootstrapDatasource(p0: String?, p1: String?) {
            TODO("Not yet implemented")
        }

        override fun getSetupPersister(): SetupPersister {
            TODO("Not yet implemented")
        }

        override fun getApplicationConfig(): ApplicationConfiguration {
            TODO("Not yet implemented")
        }

        override fun getApplicationHome(): String {
            TODO("Not yet implemented")
        }

        override fun getConfiguredApplicationHome(): String {
            TODO("Not yet implemented")
        }

        override fun getBootstrapFailureReason(): String {
            TODO("Not yet implemented")
        }

        override fun init() {
            TODO("Not yet implemented")
        }

        override fun publishConfiguration() {
            TODO("Not yet implemented")
        }

        override fun bootstrapDatabase(p0: DatabaseDetails?, p1: Boolean) {
            TODO("Not yet implemented")
        }

        override fun getHibernateConfigurator(): HibernateConfigurator {
            TODO("Not yet implemented")
        }

        override fun setHibernateConfigurator(p0: HibernateConfigurator?) {
            TODO("Not yet implemented")
        }

        override fun getHibernateConfig(): HibernateConfig {
            TODO("Not yet implemented")
        }

        override fun getTestDatasourceConnection(p0: String?): Connection {
            TODO("Not yet implemented")
        }

        override fun databaseContainsExistingData(p0: Connection?): Boolean {
            TODO("Not yet implemented")
        }

        override fun getTestDatabaseConnection(p0: DatabaseDetails?): Connection {
            TODO("Not yet implemented")
        }

        override fun getConfluenceHome(): String {
            TODO("Not yet implemented")
        }

        override fun getSharedHome(): File {
            TODO("Not yet implemented")
        }

        override fun getLocalHome(): File {
            TODO("Not yet implemented")
        }

        override fun setConfluenceHome(confluenceHome: String?) {
            TODO("Not yet implemented")
        }

        override fun getConfiguredLocalHome(): File {
            TODO("Not yet implemented")
        }

        override fun getWebAppContextPath(): String = "nirvana"

        override fun setWebAppContextPath(webAppContextPath: String?) {
            TODO("Not yet implemented")
        }

        override fun isWebAppContextPathSet(): Boolean {
            TODO("Not yet implemented")
        }

        override fun checkConfigurationOnStartup() {
            TODO("Not yet implemented")
        }

        override fun cleanupOnShutdown() {
            TODO("Not yet implemented")
        }

        override fun getDataSourceName(): Optional<String> {
            TODO("Not yet implemented")
        }

        override fun getHibernateDialect(): String {
            TODO("Not yet implemented")
        }

        override fun bootstrapSharedConfiguration(sharedConfig: SharedConfigurationMap?) {
            TODO("Not yet implemented")
        }

    }
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
            val macro = DigitalSignatureMacro(bandana, null, null, null, null, null, null, null)
            val profiles: MutableList<UserProfile> = ArrayList()
            val profile = object : UserProfile {
                override fun getUserKey(): UserKey {
                    TODO("Not yet implemented")
                }

                override fun getUsername(): String {
                    TODO("Not yet implemented")
                }

                override fun getFullName(): String =
                    "Heinz Meier"

                override fun getEmail(): String = "heinz.meier@meier.com"

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
            DigitalSignatureMacro(bandana, null, bootstrapManager, null, null, null, null, null)
        val profiles: MutableList<UserProfile> = ArrayList()
        val profile = object : UserProfile {
            override fun getFullName(): String = "Heinz Meier"
            override fun getEmail(): String = "heinz.meier@meier.com"

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
        for (i in 0..199) {
            profiles.add(profile)
        }

        val mailto = macro.getMailto(profiles, "Subject", true, signature)

        Assertions.assertEquals(
            "nirvana/rest/signature/1.0/emails?key=signature.3224a4d6bba68cd0ece9b64252f8bf5677e24cf6b7c5f543e3176d419d34d517&signed=true",
            mailto
        )
    }

    @Test
    fun mailtoShort(): Unit {
        val macro = DigitalSignatureMacro(bandana, null, null, null, null, null, null, null)
        val profiles: MutableList<UserProfile> = ArrayList()
        val profile = object : UserProfile {
            override fun getFullName(): String = "Heinz Meier"
            override fun getEmail(): String = "heinz.meier@meier.com"

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
        profiles.add(profile)

        val mailto = macro.getMailto(profiles, "Subject", true, null)

        Assertions.assertEquals(
            "mailto:Heinz Meier<heinz.meier@meier.com>?Subject=Subject",
            mailto
        )
    }
}
