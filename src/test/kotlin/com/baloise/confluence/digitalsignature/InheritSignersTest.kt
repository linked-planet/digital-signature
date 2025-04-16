package com.baloise.confluence.digitalsignature

import com.baloise.confluence.digitalsignature.InheritSigners.Companion.ofValue
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test


internal class InheritSignersTest {
    @Test
    fun testOfValueReadersOnly() {
        Assertions.assertEquals(InheritSigners.READERS_ONLY, ofValue("readers only"))
    }

    @Test
    fun testOfValueNoneIllegalArgument() {
        Assertions.assertEquals(InheritSigners.NONE, ofValue("asdasd"))
    }
}
