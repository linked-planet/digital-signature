package com.baloise.confluence.digitalsignature

import com.baloise.confluence.digitalsignature.Signature2.Companion.deserialize
import com.baloise.confluence.digitalsignature.Signature2.Companion.fromBandana
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.function.Executable
import java.io.IOException
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.nio.file.Files
import java.nio.file.Paths
import java.util.*

internal class SignatureSerialisationTest {
    @Test
    @Throws(IOException::class, ClassNotFoundException::class)
    fun deserialize() {
        val signatureKey = "signature.a077cdcc5bfcf275fe447ae2c609c1c361331b4e90cb85909582e0d824cbc5b3"

        val signature: Signature2?
        ObjectInputStream(javaClass.getResourceAsStream("/signature.ser")).use { signatureText ->
            val keys = HashSet<String>()
            keys.add(signatureKey)
            signature = fromBandana(signatureText.readObject() as Signature).first
        }
        Assertions.assertNotNull(signature)
        Assertions.assertAll(
            Executable { Assertions.assertEquals(signatureKey, signature!!.key) },
            Executable { Assertions.assertEquals("[missing1, missing2]", signature!!.missingSignatures.toString()) },
            Executable { Assertions.assertEquals(1, signature!!.signatures.size) },
            Executable { Assertions.assertTrue(signature!!.signatures.containsKey("signed1")) },
            Executable {
                Assertions.assertEquals(
                    9999, signature!!.signatures["signed1"]!!
                        .time
                )
            },  // assert we can still read the old gson serialization

            Executable {
                Assertions.assertEquals(
                    signature, deserialize(
                        SIG_JSON
                    )
                )
            },  // assert that deserialization of the serialization results in the original Signature

            Executable {
                Assertions.assertEquals(
                    signature, deserialize(
                        signature!!.serialize()
                    )
                )
            }
        )
    }

    @Test
    @Throws(IOException::class, ClassNotFoundException::class)
    fun serialize() {
        val signature = Signature2(123L, "body", "title")
        signature.notify += setOf("notify1")
        signature.missingSignatures += setOf("missing1")
        signature.missingSignatures += setOf("missing2")
        signature.signatures += Pair("signed1", Date(9999))

        val path = Paths.get("src/test/resources/signature-test.ser")
        ObjectOutputStream(Files.newOutputStream(path)).use { out ->
            out.writeObject(signature)
        }
        // assert the serialization we just wrote can be deserialized
        val `in` = ObjectInputStream(Files.newInputStream(path))
        Assertions.assertEquals(signature, `in`.readObject())
    }

    @Test
    @Throws(IOException::class, ClassNotFoundException::class)
    fun deserializeHistoricalRecord() {
        val signature = Signature(123L, "body", "title")
        signature.notify = signature.notify.orEmpty() + setOf("notify1")
        (signature.missingSignatures.orEmpty() + setOf("missing1")).also { signature.missingSignatures = it as MutableSet<String> }
        (signature.missingSignatures.orEmpty() + setOf("missing2")).also { signature.missingSignatures = it as MutableSet<String> }
        signature.signatures?.set("signed1", Date(9999))

        // assert the historically serialized class can still be deserialized
        val stream = ObjectInputStream(javaClass.getResourceAsStream("/signature.ser"))
        Assertions.assertEquals(signature, stream.readObject())
    }

    companion object {
        const val SIG_JSON: String =
            "{\"key\":\"signature.a077cdcc5bfcf275fe447ae2c609c1c361331b4e90cb85909582e0d824cbc5b3\",\"hash\":\"a077cdcc5bfcf275fe447ae2c609c1c361331b4e90cb85909582e0d824cbc5b3\",\"pageId\":123,\"title\":\"title\",\"body\":\"body\",\"maxSignatures\":-1,\"visibilityLimit\":-1,\"signatures\":{\"signed1\":\"1970-01-01T01:00:09CET\"},\"missingSignatures\":[\"missing1\",\"missing2\"],\"notify\":[\"notify1\"]}"
    }
}
