package com.baloise.confluence.digitalsignature

import com.baloise.confluence.digitalsignature.Signature2.Companion.deserialize
import com.baloise.confluence.digitalsignature.Signature2.Companion.fromBandana
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

internal class SignatureTest {
    @Nested
    internal inner class SerializationTest {
        @Test
        fun serialize_empty() {
            val signature = Signature2(0, "", "")

            val json = signature.serialize()

            Assertions.assertEquals(
                "{\"pageId\":0,\"body\":\"\",\"title\":\"\",\"hash\":\"d0609c4cf79f05d84dc5065a0f8001d14efd6834343440646d05c888c0b147db\",\"key\":\"signature.d0609c4cf79f05d84dc5065a0f8001d14efd6834343440646d05c888c0b147db\",\"protectedKey\":\"protected.d0609c4cf79f05d84dc5065a0f8001d14efd6834343440646d05c888c0b147db\",\"maxSignatures\":-1,\"visibilityLimit\":-1,\"signatures\":{},\"missingSignatures\":[],\"notify\":[]}",
                json
            )
        }

        @Test
        fun serialize_initializedObject() {
            val signature = Signature2(42L, "body text", "title text")
            signature.sign("max.mustermann")
            signature.missingSignatures = setOf("max.muster")
            signature.notify = setOf("max.meier")

            val json = signature.serialize()

            Assertions.assertTrue(
                json.contains("\"key\":\"signature.752b4cc6b4933fc7f0a6efa819c1bcc440c32155457e836d99d1bfe927cc22f5\"") &&
                        json.contains("\"hash\":\"752b4cc6b4933fc7f0a6efa819c1bcc440c32155457e836d99d1bfe927cc22f5\"") &&
                        json.contains("\"pageId\":42") &&
                        json.contains("\"title\":\"title text\"") &&
                        json.contains("\"body\":\"body text\"") &&
                        json.contains("\"maxSignatures\":-1") &&
                        json.contains("\"visibilityLimit\":-1") &&
                        json.contains("\"signatures\":{}") &&
                        json.contains("\"notify\":[\"max.meier\"]") &&
                        json.contains("\"missingSignatures\":[\"max.muster\"]")
            )
        }

        @Test
        fun deserialize_empty() {
            Assertions.assertNull(deserialize(""))
        }

        @Test
        fun serializeAndDeserialize() {
            val signature = Signature2(42L, "body text", "title text")
            signature.sign("max.mustermann")
            signature.missingSignatures = setOf("max.muster")
            signature.notify = setOf("max.meier")

            val json = signature.serialize()

            val restoredSignature = deserialize(json)

            Assertions.assertEquals(signature, restoredSignature)
        }
    }

    @Nested
    internal inner class BandanaWrapperTest {
        private val signature = Signature2(1, "test", "title")
        private val signatureOld = Signature(1, "test", "title")

        @Test
        fun toBandanaFromBandana_readAsWritten() {
            Assertions.assertEquals(signature, fromBandana(signature.serialize()).first)
        }

        @Test
        fun fromBandana_signature_signature() {
            Assertions.assertEquals(signature, fromBandana(signatureOld).first)
        }

        @Test
        fun fromBandana_string_signature() {
            Assertions.assertEquals(signature, fromBandana(signature.serialize()).first)
        }
    }
}
