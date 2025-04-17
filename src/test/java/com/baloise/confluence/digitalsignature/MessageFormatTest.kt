package com.baloise.confluence.digitalsignature

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.text.MessageFormat

internal class MessageFormatTest {
    @Test
    fun testFormat_inOrder() {
        val rawTemplate = "Email addresses of users who {0}signed{1} {2}"
        val actual = MessageFormat.format(rawTemplate, "<b>", "</b>", "#123")
        Assertions.assertEquals("Email addresses of users who <b>signed</b> #123", actual)
    }

    @Test
    fun testFormat_outOfOrder() {
        val rawTemplate = "{2} was {0}signed{1}"
        val actual = MessageFormat.format(rawTemplate, "<b>", "</b>", "#123")
        Assertions.assertEquals("#123 was <b>signed</b>", actual)
    }
}
