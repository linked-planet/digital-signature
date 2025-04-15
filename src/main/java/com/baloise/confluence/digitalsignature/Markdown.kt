package com.baloise.confluence.digitalsignature

import com.vladsch.flexmark.html.HtmlRenderer
import com.vladsch.flexmark.parser.Parser
import com.vladsch.flexmark.util.data.MutableDataSet

class Markdown {
    private val parser: Parser
    private val renderer: HtmlRenderer

    init {
        val options = MutableDataSet()
        options.set(HtmlRenderer.DO_NOT_RENDER_LINKS, true)
        options.set(HtmlRenderer.ESCAPE_HTML, true)

        parser = Parser.builder(options).build()
        renderer = HtmlRenderer.builder(options).build()
    }

    fun toHTML(markdown: String): String {
        return renderer.render(parser.parse(markdown))
    }
}
