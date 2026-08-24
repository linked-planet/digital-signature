package com.baloise.confluence.digitalsignature.ao

import com.baloise.confluence.digitalsignature.Signature
import com.baloise.confluence.digitalsignature.Signature2
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

internal class SignatureStoreTest {

    @Test
    fun putGetFromAo_jsonRoundtrip() {
        val store = mapStore()
        val signature = Signature2(42L, "body text", "title text")
        signature.missingSignatures = setOf("max.muster")
        signature.notify = setOf("max.meier")

        store.put(signature.key, signature)

        val loaded = store.getFromAo(signature.key)
        Assertions.assertEquals(signature, loaded)
        Assertions.assertEquals("body text", loaded!!.body)
        Assertions.assertEquals(setOf("max.muster"), loaded.missingSignatures)
        Assertions.assertEquals(setOf("max.meier"), loaded.notify)
    }

    @Test
    fun get_copiesBandanaJsonIntoAo() {
        val signature = Signature2(1, "test", "title")
        val fallback = CountingFallback(mapOf(signature.key to signature.serialize()))
        val store = mapStore(fallback)

        val loaded = store.get(signature.key)

        Assertions.assertEquals(signature, loaded)
        Assertions.assertEquals(signature, store.getFromAo(signature.key))
        Assertions.assertEquals(1, fallback.getValueCalls)
    }

    @Test
    fun get_copiesLegacySignatureIntoAoAsJson() {
        val legacy = Signature(1, "test", "title")
        val fallback = CountingFallback(mapOf(legacy.key!! to legacy))
        val store = mapStore(fallback)

        val loaded = store.get(legacy.key!!)

        Assertions.assertEquals(Signature2(1, "test", "title"), loaded)
        val aoPayload = store.getFromAo(legacy.key!!)
        Assertions.assertNotNull(aoPayload)
        Assertions.assertEquals(legacy.key, aoPayload!!.key)
        Assertions.assertTrue(aoPayload.serialize().contains("\"pageId\":1"))
        Assertions.assertEquals(1, fallback.getValueCalls)
    }

    @Test
    fun get_afterCopyDoesNotConsultFallback() {
        val signature = Signature2(1, "test", "title")
        val fallback = CountingFallback(mapOf(signature.key to signature.serialize()))
        val store = mapStore(fallback)

        store.get(signature.key)
        store.get(signature.key)

        Assertions.assertEquals(1, fallback.getValueCalls)
    }

    @Test
    fun get_missingEverywhere_returnsNull() {
        val fallback = CountingFallback(emptyMap())
        val store = mapStore(fallback)
        Assertions.assertNull(store.get("signature.missing"))
        Assertions.assertEquals(1, fallback.getValueCalls)
    }

    @Test
    fun get_corruptAo_failsClosed_evenIfBandanaHasCopy() {
        val signature = Signature2(1, "test", "title")
        val ao = mutableMapOf(signature.key to "not-valid-json{")
        val fallback = CountingFallback(mapOf(signature.key to signature.serialize()))
        val store = AoSignatureStore(
            findInAo = { key ->
                ao[key]?.let { AoSignatureStore.parseAoPayload(key, it) }
            },
            upsertAo = { key, sig -> ao[key] = sig.serialize() },
            bandanaFallback = fallback,
        )

        assertThrows<CorruptSignaturePayloadException> { store.get(signature.key) }
        Assertions.assertEquals(0, fallback.getValueCalls)
        Assertions.assertEquals("not-valid-json{", ao[signature.key])
    }

    @Test
    fun get_corruptAo_noBandana_failsClosed() {
        val key = "signature.corrupt"
        val ao = mutableMapOf(key to "not-valid-json{")
        val store = AoSignatureStore(
            findInAo = { k ->
                ao[k]?.let { AoSignatureStore.parseAoPayload(k, it) }
            },
            upsertAo = { k, sig -> ao[k] = sig.serialize() },
            bandanaFallback = null,
        )

        assertThrows<CorruptSignaturePayloadException> { store.get(key) }
        assertThrows<CorruptSignaturePayloadException> { store.getFromAo(key) }
    }

    @Test
    fun put_updatesCompletedSignatureMetadata() {
        val store = mapStore()
        val complete = Signature2(2, "done", "done")
        complete.notify = setOf("old")
        store.put(complete.key, complete)

        complete.notify = setOf("new")
        store.put(complete.key, complete)

        Assertions.assertEquals(setOf("new"), store.getFromAo(complete.key)!!.notify)
    }

    @Test
    fun save_writesOnlyWhenMissingSignatures() {
        val store = mapStore()
        val pending = Signature2(1, "test", "title")
        pending.missingSignatures = setOf("alice")
        pending.save(store)
        Assertions.assertNotNull(store.getFromAo(pending.key))

        val complete = Signature2(2, "done", "done")
        complete.save(store)
        Assertions.assertNull(store.getFromAo(complete.key))
    }

    private fun mapStore(fallback: BandanaFallback? = null): AoSignatureStore {
        val ao = mutableMapOf<String, String>()
        return AoSignatureStore(
            findInAo = { key ->
                ao[key]?.let { AoSignatureStore.parseAoPayload(key, it) }
            },
            upsertAo = { key, sig -> ao[key] = sig.serialize() },
            bandanaFallback = fallback,
        )
    }

    private class CountingFallback(private val values: Map<String, Any?>) : BandanaFallback {
        var getValueCalls = 0
        override fun getValue(key: String): Any? {
            getValueCalls++
            return values[key]
        }

        override fun keys(): Iterable<String> = values.keys
    }
}
