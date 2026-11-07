package io.reqlith.markdown

import org.commonmark.Extension
import org.commonmark.ext.autolink.AutolinkExtension
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension
import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.ext.heading.anchor.HeadingAnchorExtension
import org.commonmark.ext.task.list.items.TaskListItemsExtension
import org.commonmark.node.AbstractVisitor
import org.commonmark.node.Code
import org.commonmark.node.FencedCodeBlock
import org.commonmark.node.Link
import org.commonmark.node.Node
import org.commonmark.parser.Parser
import org.commonmark.renderer.NodeRenderer
import org.commonmark.renderer.html.HtmlNodeRendererContext
import org.commonmark.renderer.html.HtmlRenderer
import org.owasp.html.HtmlPolicyBuilder
import org.owasp.html.PolicyFactory
import org.springframework.stereotype.Component

/** Resolves an element reference such as `FR-12` to a link target, or null when the key is unknown. */
fun interface ReferenceResolver {
    fun resolve(key: String): Reference?

    companion object {
        val NONE = ReferenceResolver { null }
    }
}

data class Reference(val url: String, val title: String)

/**
 * The single markdown renderer used by the editor preview, read views, exports and MCP.
 *
 * - GitHub flavoured tables, strikethrough, task lists, autolinks and heading anchors.
 * - ```` ```mermaid ```` fences become `<pre class="mermaid">`, rendered in the browser (and by headless Chromium for PDF).
 * - Inline code that is an element key (`` `FR-12` ``) links to that element.
 * - Raw HTML is escaped and the result is sanitized as a second line of defence.
 */
@Component
class MarkdownRenderer {

    private val extensions: List<Extension> = listOf(
        TablesExtension.create(),
        StrikethroughExtension.create(),
        TaskListItemsExtension.create(),
        HeadingAnchorExtension.create(),
        AutolinkExtension.create(),
    )

    private val parser: Parser = Parser.builder().extensions(extensions).build()

    private val renderer: HtmlRenderer = HtmlRenderer.builder()
        .extensions(extensions)
        .escapeHtml(true)
        .sanitizeUrls(true)
        .nodeRendererFactory { context -> FencedCodeRenderer(context) }
        .attributeProviderFactory {
            org.commonmark.renderer.html.AttributeProvider { node, tagName, attributes ->
                when {
                    node is Link && node.firstChild is Code && tagName == "a" -> attributes["class"] = "ref"
                    node is Code && KEY.matches(node.literal) && node.parent !is Link -> attributes["class"] = "ref-missing"
                }
            }
        }
        .build()

    fun render(markdown: String?, references: ReferenceResolver = ReferenceResolver.NONE): String {
        if (markdown.isNullOrBlank()) return ""
        val document = parser.parse(markdown)
        document.accept(ReferenceLinker(references))
        return POLICY.sanitize(renderer.render(document))
    }

    /** Wraps inline code that names a known element in a link. */
    private class ReferenceLinker(private val references: ReferenceResolver) : AbstractVisitor() {
        override fun visit(code: Code) {
            if (code.parent is Link || !KEY.matches(code.literal)) return
            val reference = references.resolve(code.literal) ?: return
            val link = Link(reference.url, reference.title)
            code.insertBefore(link)
            code.unlink()
            link.appendChild(code)
        }
    }

    private class FencedCodeRenderer(private val context: HtmlNodeRendererContext) : NodeRenderer {
        private val html = context.writer

        override fun getNodeTypes(): Set<Class<out Node>> = setOf(FencedCodeBlock::class.java)

        override fun render(node: Node) {
            val block = node as FencedCodeBlock
            val language = block.info?.trim()?.substringBefore(' ').orEmpty()
            html.line()
            if (language.equals("mermaid", ignoreCase = true)) {
                html.tag("pre", mapOf("class" to "mermaid"))
                html.text(block.literal)
                html.tag("/pre")
            } else {
                html.tag("pre", context.extendAttributes(block, "pre", emptyMap()))
                val codeAttributes = if (language.isEmpty()) emptyMap() else mapOf("class" to "language-$language")
                html.tag("code", context.extendAttributes(block, "code", codeAttributes))
                html.text(block.literal)
                html.tag("/code")
                html.tag("/pre")
            }
            html.line()
        }
    }

    companion object {
        /** Element and role keys: BR-01, FR-12, NFR-3, RO-01, ... */
        val KEY = Regex("[A-Z]{2,3}-\\d{1,5}")

        /** Exactly what the markdown renderer can produce; anything else is dropped. */
        private val POLICY: PolicyFactory = HtmlPolicyBuilder()
            .allowElements(
                "p", "div", "h1", "h2", "h3", "h4", "h5", "h6", "blockquote", "pre", "code", "hr", "br",
                "ul", "ol", "li", "strong", "em", "del", "s", "a", "img", "input",
                "table", "thead", "tbody", "tr", "th", "td", "span",
            )
            .allowAttributes("class").globally()
            .allowAttributes("id").onElements("h1", "h2", "h3", "h4", "h5", "h6")
            .allowAttributes("href", "title").onElements("a")
            .allowAttributes("src", "alt", "title").onElements("img")
            .allowAttributes("start").onElements("ol")
            .allowAttributes("align").onElements("th", "td")
            .allowAttributes("type", "checked", "disabled").onElements("input")
            .allowUrlProtocols("http", "https", "mailto")
            .requireRelsOnLinks("noopener", "noreferrer")
            .toFactory()
    }
}
