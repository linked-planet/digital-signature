package com.baloise.confluence.digitalsignature.upgrade

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
        val task = BandanaToAoUpgradeTask(store, bandana)

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
        val task = BandanaToAoUpgradeTask(store, bandana)

        task.doUpgrade()

        Assertions.assertEquals("title", store.getFromAo(sig.key)!!.title)
    }

    @Test
    fun doUpgrade_failedDeserialize_throwsSoSalCanRetry() {
        val store = InMemorySignatureStore()
        val bandana = MapBandana("signature.bad" to 42)
        val task = BandanaToAoUpgradeTask(store, bandana)

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
        val task = BandanaToAoUpgradeTask(store, bandana)

        assertThrows<IllegalStateException> { task.doUpgrade() }

        Assertions.assertEquals(good, store.getFromAo(good.key))
        Assertions.assertNull(store.getFromAo("signature.bad"))
    }

    @Test
    fun doUpgrade_secondRunIsIdempotent() {
        val sig = Signature2(1, "body", "title")
        val store = InMemorySignatureStore()
        val bandana = MapBandana(sig.key to sig.serialize())
        val task = BandanaToAoUpgradeTask(store, bandana)

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
        val task = BandanaToAoUpgradeTask(store, bandana)

        Assertions.assertTrue(task.doUpgrade().isEmpty())
        Assertions.assertEquals(sig, store.getFromAo(sig.key))
    }

    @Test
    fun pluginKeyAndBuildNumber() {
        val task = BandanaToAoUpgradeTask(InMemorySignatureStore(), MapBandana())
        Assertions.assertEquals("com.baloise.confluence.digital-signature", task.pluginKey)
        Assertions.assertEquals(1, task.buildNumber)
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
