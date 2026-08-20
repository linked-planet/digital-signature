package com.baloise.confluence.digitalsignature

import com.atlassian.activeobjects.external.ActiveObjects
import com.atlassian.bandana.BandanaManager
import com.atlassian.plugins.osgi.javaconfig.ExportOptions
import com.atlassian.plugins.osgi.javaconfig.OsgiServices.exportOsgiService
import com.atlassian.plugins.osgi.javaconfig.OsgiServices.importOsgiService
import com.atlassian.sal.api.upgrade.PluginUpgradeTask
import com.baloise.confluence.digitalsignature.ao.AoSignatureStore
import com.baloise.confluence.digitalsignature.ao.BandanaFallback
import com.baloise.confluence.digitalsignature.ao.C10BandanaFallback
import com.baloise.confluence.digitalsignature.ao.SignatureStore
import com.baloise.confluence.digitalsignature.upgrade.BandanaToAoUpgradeTask
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Spring bean wiring for this plugin. Creates [SignatureStore] and exports it to OSGi so
 * consumers ([DigitalSignatureMacro], REST) can [importOsgiService] it.
 */
@Configuration
open class PluginConfig {

    @Bean
    open fun activeObjects(): ActiveObjects =
        importOsgiService(ActiveObjects::class.java)

    @Bean
    open fun bandanaManager(): BandanaManager =
        importOsgiService(BandanaManager::class.java)

    @Bean
    open fun bandanaFallback(bandanaManager: BandanaManager): BandanaFallback =
        C10BandanaFallback(bandanaManager)

    @Bean
    open fun signatureStore(
        activeObjects: ActiveObjects,
        bandanaFallback: BandanaFallback,
    ): SignatureStore =
        AoSignatureStore(activeObjects, bandanaFallback)

    @Bean
    open fun exportSignatureStore(signatureStore: SignatureStore) =
        exportOsgiService(signatureStore, ExportOptions.`as`(SignatureStore::class.java))

    @Bean
    open fun bandanaToAoUpgradeTask(
        signatureStore: SignatureStore,
        bandanaFallback: BandanaFallback,
    ): PluginUpgradeTask =
        BandanaToAoUpgradeTask(signatureStore, bandanaFallback)

    @Bean
    open fun exportBandanaToAoUpgradeTask(bandanaToAoUpgradeTask: PluginUpgradeTask) =
        exportOsgiService(bandanaToAoUpgradeTask, ExportOptions.`as`(PluginUpgradeTask::class.java))
}
