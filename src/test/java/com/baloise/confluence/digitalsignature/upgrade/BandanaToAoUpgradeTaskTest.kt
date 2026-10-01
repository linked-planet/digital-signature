package com.baloise.confluence.digitalsignature.upgrade

import com.atlassian.confluence.content.render.xhtml.definition.PlainTextMacroBody
import com.atlassian.confluence.pages.Page
import com.atlassian.confluence.pages.PageManager
import com.atlassian.confluence.xhtml.api.MacroDefinition
import com.atlassian.confluence.xhtml.api.MacroDefinitionHandler
import com.atlassian.confluence.xhtml.api.XhtmlContent
import java.lang.reflect.Proxy
import com.baloise.confluence.digitalsignature.Signature
import com.baloise.confluence.digitalsignature.Signature2
import com.baloise.confluence.digitalsignature.ao.BandanaFallback
import com.baloise.confluence.digitalsignature.ao.CorruptSignaturePayloadException
import com.baloise.confluence.digitalsignature.ao.SignatureStore
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

internal class BandanaToAoUpgradeTaskTest {

    @Test
    fun doUpgrade_copiesJsonAndLegacySignatureKeys() {
        val jsonSig = Signature2(1, "json-body", "json-title")
        val legacy = Signature(2, "xstream-body", "xstream-title")
        val store = InMemorySignatureStore()
        val bandana = MapBandana(
            "unrelated.key" to "ignore-me",
            jsonSig.key to jsonSig.serialize(),
            legacy.key!! to legacy,
        )
        val task = task(store, bandana)

        val errors = task.doUpgrade()

        Assertions.assertTrue(errors.isEmpty())
        Assertions.assertEquals(jsonSig, store.getFromAo(jsonSig.key))
        Assertions.assertEquals("xstream-body", store.getFromAo(legacy.key!!)!!.body)
        Assertions.assertNull(store.getFromAo("unrelated.key"))
    }

    @Test
    fun doUpgrade_skipsKeysAlreadyInAo() {
        val sig = Signature2(1, "body", "title")
        val store = InMemorySignatureStore()
        store.put(sig.key, sig)
        val mutated = Signature2(1, "body", "title")
        mutated.title = "changed-in-bandana"
        val bandana = MapBandana(sig.key to mutated.serialize())
        val task = task(store, bandana)

        task.doUpgrade()

        Assertions.assertEquals("title", store.getFromAo(sig.key)!!.title)
    }

    @Test
    fun doUpgrade_failedDeserialize_throwsSoSalCanRetry() {
        val store = InMemorySignatureStore()
        val bandana = MapBandana("signature.bad" to 42)
        val task = task(store, bandana)

        val ex = assertThrows<IllegalStateException> { task.doUpgrade() }

        Assertions.assertTrue(ex.message!!.contains("failed=1"))
        Assertions.assertNull(store.getFromAo("signature.bad"))
    }

    @Test
    fun doUpgrade_partialFailure_stillMigratesGoodKeysThenThrows() {
        val good = Signature2(1, "body", "title")
        val store = InMemorySignatureStore()
        val bandana = MapBandana(
            good.key to good.serialize(),
            "signature.bad" to 42,
        )
        val task = task(store, bandana)

        assertThrows<IllegalStateException> { task.doUpgrade() }

        Assertions.assertEquals(good, store.getFromAo(good.key))
        Assertions.assertNull(store.getFromAo("signature.bad"))
    }

    @Test
    fun doUpgrade_secondRunIsIdempotent() {
        val sig = Signature2(1, "body", "title")
        val store = InMemorySignatureStore()
        val bandana = MapBandana(sig.key to sig.serialize())
        val task = task(store, bandana)

        Assertions.assertTrue(task.doUpgrade().isEmpty())
        Assertions.assertTrue(task.doUpgrade().isEmpty())
        Assertions.assertEquals(1, store.putCount)
        Assertions.assertEquals(sig, store.getFromAo(sig.key))
    }

    @Test
    fun doUpgrade_overwritesCorruptAoFromBandana() {
        val sig = Signature2(1, "body", "title")
        val store = InMemorySignatureStore()
        store.putRaw(sig.key, "not-valid-json{")
        val bandana = MapBandana(sig.key to sig.serialize())
        val task = task(store, bandana)

        Assertions.assertTrue(task.doUpgrade().isEmpty())
        Assertions.assertEquals(sig, store.getFromAo(sig.key))
    }

    @Test
    fun pluginKeyAndBuildNumber() {
        val task = task(InMemorySignatureStore(), MapBandana())
        Assertions.assertEquals("com.baloise.confluence.digital-signature", task.pluginKey)
        Assertions.assertEquals(1, task.buildNumber)
    }

    @Test
    fun doUpgrade_skipsObsoleteInvalidNotifyAndMigratesCurrentMacro() {
        val obsolete = Signature2(1, "old-body", "title")
        val current = Signature2(1, "new-body", "title")
        val badJson = obsolete.serialize().replace("\"notify\":[]", "\"notify\":\"user\"")
        val store = InMemorySignatureStore()
        val bandana = MapBandana(obsolete.key to badJson, current.key to current.serialize())

        Assertions.assertTrue(task(store, bandana, listOf(current)).doUpgrade().isEmpty())
        Assertions.assertNull(store.getFromAo(obsolete.key))
        Assertions.assertEquals(current, store.getFromAo(current.key))

        val currentStore = InMemorySignatureStore()
        Assertions.assertTrue(task(currentStore, MapBandana(obsolete.key to badJson), listOf(obsolete)).doUpgrade().isEmpty())
        val migrated = currentStore.getFromAo(obsolete.key)!!
        Assertions.assertEquals(obsolete.key, migrated.key)
        Assertions.assertTrue(migrated.notify.isEmpty())
    }

    @Test
    fun doUpgrade_skipsRemovedMacrosMissingPagesAndChangedTitles() {
        val old = Signature2(1, "body", "old-title")
        val renamed = Signature2(1, "body", "new-title")
        for (current in listOf(emptyList(), listOf(renamed))) {
            val store = InMemorySignatureStore()
            Assertions.assertTrue(task(store, MapBandana(old.key to old.serialize()), current).doUpgrade().isEmpty())
            Assertions.assertNull(store.getFromAo(old.key))
        }
        val store = InMemorySignatureStore()
        Assertions.assertTrue(task(store, MapBandana(old.key to old.serialize()), listOf(renamed), missingPage = true).doUpgrade().isEmpty())
        Assertions.assertNull(store.getFromAo(old.key))
    }

    @Test
    fun doUpgrade_pageParsingFailureStillFailsMigration() {
        val sig = Signature2(1, "body", "title")
        val store = InMemorySignatureStore()
        assertThrows<IllegalStateException> {
            task(store, MapBandana(sig.key to sig.serialize()), parsingFailure = true).doUpgrade()
        }
        Assertions.assertNull(store.getFromAo(sig.key))
    }

    private fun task(
        store: SignatureStore,
        bandana: BandanaFallback,
        current: List<Signature2> = bandana.keys().filter { it.startsWith("signature.") }
            .mapNotNull { Signature2.fromPersistedValue(bandana.getValue(it)).first },
        missingPage: Boolean = false,
        parsingFailure: Boolean = false,
    ): BandanaToAoUpgradeTask {
        val pageManager = Proxy.newProxyInstance(
            PageManager::class.java.classLoader, arrayOf(PageManager::class.java),
        ) { _, method, args ->
            check(method.name == "getPage")
            if (missingPage) null else Page().apply {
                id = args!![0] as Long
                bodyAsString = "storage:$id"
            }
        } as PageManager
        val xhtmlContent = Proxy.newProxyInstance(
            XhtmlContent::class.java.classLoader, arrayOf(XhtmlContent::class.java),
        ) { _, method, args ->
            check(method.name == "handleMacroDefinitions")
            if (parsingFailure) error("Cannot parse page storage")
            val pageId = (args!![0] as String).removePrefix("storage:").toLong()
            val handler = args[2] as MacroDefinitionHandler
            for (sig in current.filter { it.pageId == pageId }) {
                handler.handle(MacroDefinition.builder("signature")
                    .withMacroBody(PlainTextMacroBody(sig.body))
                    .withParameter("title", sig.title).build())
            }
            null
        } as XhtmlContent
        return BandanaToAoUpgradeTask(store, bandana, pageManager, xhtmlContent)
    }

    private class MapBandana(vararg entries: Pair<String, Any?>) : BandanaFallback {
        private val values = mapOf(*entries)
        override fun getValue(key: String): Any? = values[key]
        override fun keys(): Iterable<String> = values.keys
    }

    private class InMemorySignatureStore : SignatureStore {
        private val ao = mutableMapOf<String, String>()
        var putCount = 0
        override fun get(key: String): Signature2? = getFromAo(key)
        override fun put(key: String, sig: Signature2) {
            putCount++
            ao[key] = sig.serialize()
        }

        fun putRaw(key: String, payload: String) {
            ao[key] = payload
        }

        override fun getFromAo(key: String): Signature2? {
            val payload = ao[key] ?: return null
            try {
                return Signature2.deserialize(payload)
                    ?: throw CorruptSignaturePayloadException(key)
            } catch (e: CorruptSignaturePayloadException) {
                throw e
            } catch (e: RuntimeException) {
                throw CorruptSignaturePayloadException(key, e)
            }
        }
    }
}
