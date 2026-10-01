package com.baloise.confluence.digitalsignature.upgrade

import com.atlassian.confluence.content.render.xhtml.DefaultConversionContext
import com.atlassian.confluence.pages.PageManager
import com.atlassian.confluence.renderer.PageContext
import com.atlassian.confluence.xhtml.api.XhtmlContent
import com.atlassian.plugin.spring.scanner.annotation.component.ConfluenceComponent
import com.atlassian.plugin.spring.scanner.annotation.export.ExportAsService
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport
import com.atlassian.sal.api.message.Message
import com.atlassian.sal.api.upgrade.PluginUpgradeTask
import com.baloise.confluence.digitalsignature.Signature
import com.baloise.confluence.digitalsignature.Signature2
import com.baloise.confluence.digitalsignature.ao.BandanaFallback
import com.baloise.confluence.digitalsignature.ao.CorruptSignaturePayloadException
import com.baloise.confluence.digitalsignature.ao.SignatureStore
import com.google.gson.JsonParser
import org.apache.logging.log4j.LogManager

/**
 * Copies global Bandana `signature.*` rows still referenced by current page macros into AO.
 * Does not delete Bandana or consider historical page versions.
 * Safe to run twice (rows already in AO are skipped).
 *
 * Throws if any key fails so SAL does not advance the plugin build number and the task can retry.
 */
@ConfluenceComponent
@ExportAsService(PluginUpgradeTask::class)
class BandanaToAoUpgradeTask(
    private val store: SignatureStore,
    private val bandana: BandanaFallback,
    @param:ComponentImport private val pageManager: PageManager,
    @param:ComponentImport private val xhtmlContent: XhtmlContent,
) : PluginUpgradeTask {

    /**
     * @return SAL build number for this task; first AO migration is `1`
     */
    override fun getBuildNumber(): Int = BUILD_NUMBER

    /**
     * @return short description for UPM / logs
     */
    override fun getShortDescription(): String =
        "Copy Bandana signatures to Active Objects"

    /**
     * @return OSGi plugin key (`groupId.artifactId`), not the colon mywork key
     */
    override fun getPluginKey(): String = PLUGIN_KEY

    /**
     * Copies `signature.*` Bandana values referenced by current page macros into AO.
     * JSON strings and legacy XStream
     * [com.baloise.confluence.digitalsignature.Signature] beans are both accepted.
     *
     * @return empty on full success
     * @throws IllegalStateException if any key failed (keeps SAL from recording this build number)
     */
    override fun doUpgrade(): Collection<Message> {
        var migrated = 0
        var skipped = 0
        var failed = 0
        val currentKeysByPage = mutableMapOf<Long, Set<String>>()

        for (key in bandana.keys()) {
            if (!key.startsWith(KEY_PREFIX)) {
                continue
            }
            try {
                val existing = try {
                    store.getFromAo(key)
                } catch (_: CorruptSignaturePayloadException) {
                    // Corrupt AO row: fall through and overwrite from Bandana when possible.
                    null
                }
                if (existing != null) {
                    skipped++
                    continue
                }
                val value = bandana.getValue(key)
                if (value != null) {
                    // Read only pageId, so obsolete JSON with invalid notify can still be skipped.
                    val pageId = when (value) {
                        is Signature -> value.pageId
                        is String -> JsonParser.parseString(value).asJsonObject.get("pageId").asLong
                        else -> error("Unsupported Bandana value for '$key'")
                    }
                    val currentKeys = currentKeysByPage.getOrPut(pageId) { currentSignatureKeys(pageId) }
                    if (key !in currentKeys) {
                        skipped++
                        log.debug("DIGITAL SIGNATURE: Skipping unused Bandana signature '{}'", key)
                        continue
                    }
                }
                val (sig, _) = Signature2.fromPersistedValue(value)
                if (sig == null) {
                    if (value == null) {
                        skipped++
                    } else {
                        failed++
                        log.error("DIGITAL SIGNATURE: Could not deserialize Bandana signature '{}'", key)
                    }
                    continue
                }
                store.put(key, sig)
                migrated++
                log.debug("DIGITAL SIGNATURE: Migrated Bandana signature '{}' to AO", key)
            } catch (e: Exception) {
                failed++
                log.error("DIGITAL SIGNATURE: Failed to migrate Bandana signature '{}'", key, e)
            }
        }

        log.info(
            "DIGITAL SIGNATURE: Bandana to Active Objects signature migration: migrated={}, skipped={}, failed={}",
            migrated,
            skipped,
            failed
        )
        if (failed > 0) {
            throw IllegalStateException(
                "Bandana to AO signature migration incomplete: migrated=$migrated, skipped=$skipped, failed=$failed"
            )
        }
        return emptyList()
    }

    private fun currentSignatureKeys(pageId: Long): Set<String> {
        val page = pageManager.getPage(pageId) ?: return emptySet()
        if (!page.isCurrent || page.isDeleted) return emptySet()
        val keys = mutableSetOf<String>()
        xhtmlContent.handleMacroDefinitions(
            page.bodyAsString,
            DefaultConversionContext(PageContext(page)),
        ) { macro ->
            if (macro.name == "signature") {
                keys.add(Signature2(page.latestVersionId, macro.bodyText ?: "", macro.parameters["title"] ?: "").key)
            }
        }
        return keys
    }

    companion object {
        private val log = LogManager.getLogger(BandanaToAoUpgradeTask::class.java)
        const val PLUGIN_KEY: String = "com.baloise.confluence.digital-signature"
        const val BUILD_NUMBER: Int = 1
        const val KEY_PREFIX: String = "signature."
    }
}
