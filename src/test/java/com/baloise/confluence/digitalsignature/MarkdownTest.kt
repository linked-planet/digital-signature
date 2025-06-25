package com.baloise.confluence.digitalsignature

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.function.Executable
import java.io.IOException
import java.net.URISyntaxException
import java.nio.file.Files
import java.nio.file.Paths

internal class MarkdownTest {
    private var markdown: Markdown? = null

    @BeforeEach
    fun setUp() {
        markdown = Markdown()
    }

    @Test
    fun testToHTML() {
        Assertions.assertAll(
            Executable {
                Assertions.assertEquals(
                    "<p>This is <em>Sparta</em></p>\n",
                    markdown!!.toHTML("This is *Sparta*")
                )
            },
            Executable { Assertions.assertEquals("<p>Link</p>\n", markdown!!.toHTML("[Link](http://a.com)")) },
            Executable { Assertions.assertEquals("<p></p>\n", markdown!!.toHTML("![Image](http://url/a.png)")) },
            Executable { Assertions.assertEquals("<p>&lt;b&gt;&lt;/b&gt;</p>\n", markdown!!.toHTML("<b></b>")) },
            Executable {
                Assertions.assertEquals(
                    readResource("commonmark.html").trim { it <= ' ' },
                    markdown!!.toHTML(readResource("commonmark.md")).trim { it <= ' ' })
            }
        )
    }

    @Throws(IOException::class, URISyntaxException::class)
    private fun readResource(name: String): String {
        return java.lang.String.join("\n", Files.readAllLines(Paths.get(javaClass.getResource("/$name").toURI())))
    }
}
