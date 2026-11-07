package io.reqlith.service

import io.reqlith.model.dto.Reference
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import org.junit.jupiter.api.Test

class MarkdownRendererTest {
    private val renderer = MarkdownRenderer()

    @Test
    fun `mermaid fences become mermaid blocks for the browser`() {
        val html = renderer.render("```mermaid\nflowchart LR\n  A --> B\n```")
        assertContains(html, """<pre class="mermaid">flowchart LR""")
        assertContains(html, "A --&gt; B")
    }

    @Test
    fun `other fences keep their language class`() {
        assertContains(renderer.render("```kotlin\nval x = 1\n```"), """<code class="language-kotlin">val x &#61; 1""")
    }

    @Test
    fun `raw html and javascript links are neutralised`() {
        val html = renderer.render("<script>alert(1)</script>\n\n[x](javascript:alert(1)) <img src=x onerror=alert(1)>")
        assertFalse(html.contains("<script"), html)
        assertFalse(html.contains("javascript:"), html)
        assertFalse(html.contains("<img"), html)
    }

    @Test
    fun `known element keys in inline code link to the element, unknown ones are flagged`() {
        val html = renderer.render("Derived from `SR-03`, see `XX-99`.") { key ->
            if (key == "SR-03") Reference("/p/PRJ-GEN-001/e/SR-03", "SR-03 · Status") else null
        }
        assertContains(html, """href="/p/PRJ-GEN-001/e/SR-03" title="SR-03 · Status" class="ref"""")
        assertContains(html, """<code>SR-03</code></a>""")
        assertContains(html, """<code class="ref-missing">XX-99</code>""")
    }

    @Test
    fun `tables, task lists and heading anchors are supported`() {
        val html = renderer.render("## Success metrics\n\n| A | B |\n|---|---|\n| 1 | 2 |\n\n- [x] done")
        assertContains(html, """<h2 id="success-metrics">""")
        assertContains(html, "<table>")
        assertContains(html, "checkbox")
    }

    @Test
    fun `blank input renders nothing`() {
        assertEquals("", renderer.render("  \n"))
    }
}
