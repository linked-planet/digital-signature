package com.baloise.confluence.digitalsignature.ao

import com.atlassian.bandana.BandanaManager
import com.atlassian.confluence.setup.bandana.ConfluenceBandanaContext
import com.atlassian.plugin.spring.scanner.annotation.component.ConfluenceComponent
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport

/**
 * Read-only Bandana access for C10 migration. Delete this type (and [C10BandanaFallback]) when
 * targeting Confluence 11.
 */
interface BandanaFallback {
    /**
     * @param key Bandana key under global context
     * @return stored value (JSON [String] or legacy XStream [com.baloise.confluence.digitalsignature.Signature]), or `null`
     */
    fun getValue(key: String): Any?

    /**
     * @return all keys in [ConfluenceBandanaContext.GLOBAL_CONTEXT]
     */
    fun keys(): Iterable<String>
}

/**
 * [BandanaManager] adapter limited to Confluence global Bandana. Reads only (`getValue` / `getKeys`).
 */
@ConfluenceComponent
class C10BandanaFallback(
    @param:ComponentImport private val bandanaManager: BandanaManager,
) : BandanaFallback {
    /**
     * @param key Bandana key under global context
     * @return stored value, or `null`
     */
    override fun getValue(key: String): Any? =
        bandanaManager.getValue(ConfluenceBandanaContext.GLOBAL_CONTEXT, key)

    /**
     * @return keys in global Bandana context; empty if the product returns `null`
     */
    override fun keys(): Iterable<String> =
        bandanaManager.getKeys(ConfluenceBandanaContext.GLOBAL_CONTEXT) ?: emptyList()
}
