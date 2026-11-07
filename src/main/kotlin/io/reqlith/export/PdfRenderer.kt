package io.reqlith.export

import com.microsoft.playwright.Browser
import com.microsoft.playwright.Page
import com.microsoft.playwright.Playwright
import jakarta.annotation.PreDestroy
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Prints self-contained HTML to PDF with headless Chromium, so Mermaid diagrams are drawn exactly as
 * in the browser. Playwright objects are not thread-safe: one browser lives on a dedicated thread and
 * is started lazily on the first export. The page has no network access.
 */
@Component
class PdfRenderer {
    private val log = LoggerFactory.getLogger(javaClass)
    private val thread: ExecutorService = Executors.newSingleThreadExecutor { Thread(it, "pdf-renderer").apply { isDaemon = true } }
    private var playwright: Playwright? = null
    private var browser: Browser? = null

    private val mermaid: String by lazy {
        requireNotNull(javaClass.getResourceAsStream("/static/vendor/mermaid.min.js")) { "mermaid.min.js missing" }
            .use { it.readAllBytes().toString(Charsets.UTF_8) }
    }

    fun render(html: String, title: String): ByteArray = thread.submit(Callable { print(html, title) }).get(2, TimeUnit.MINUTES)

    private fun print(html: String, title: String): ByteArray {
        val page = browser().newPage()
        try {
            page.route("**/*") { route -> if (route.request().url().startsWith("data:")) route.resume() else route.abort() }
            page.setContent(html)
            if (page.querySelector("pre.mermaid") != null) {
                page.addScriptTag(Page.AddScriptTagOptions().setContent(mermaid))
                page.evaluate(RENDER_MERMAID)
                page.waitForFunction("() => window.mermaidDone === true", null, Page.WaitForFunctionOptions().setTimeout(60_000.0))
            }
            return page.pdf(
                Page.PdfOptions()
                    .setFormat("A4")
                    .setPrintBackground(true)
                    .setDisplayHeaderFooter(true)
                    .setHeaderTemplate("<span></span>")
                    .setFooterTemplate(
                        """<div style="font: 8px sans-serif; width: 100%; padding: 0 12mm; display: flex; justify-content: space-between; color: #5B6170">
                           <span>${escape(title)}</span><span><span class="pageNumber"></span> / <span class="totalPages"></span></span></div>""",
                    )
                    .setMargin(com.microsoft.playwright.options.Margin().setTop("16mm").setBottom("18mm").setLeft("14mm").setRight("14mm")),
            )
        } finally {
            page.close()
        }
    }

    private fun browser(): Browser {
        browser?.takeIf { it.isConnected }?.let { return it }
        log.info("Starting headless Chromium for PDF export")
        // The driver gets this environment as a whole, so start from the process environment
        // (PLAYWRIGHT_BROWSERS_PATH, PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD in the container image).
        val env = System.getenv() + ("PLAYWRIGHT_SKIP_VALIDATE_HOST_REQUIREMENTS" to "true")
        val pw = playwright ?: Playwright.create(Playwright.CreateOptions().setEnv(env)).also { playwright = it }
        return pw.chromium().launch().also { browser = it }
    }

    @PreDestroy
    fun close() {
        thread.submit {
            runCatching { browser?.close() }
            runCatching { playwright?.close() }
        }
        thread.shutdown()
    }

    private fun escape(text: String) = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    companion object {
        private val RENDER_MERMAID = """
            () => {
              mermaid.initialize({ startOnLoad: false, securityLevel: "strict", theme: "neutral",
                                   fontFamily: "IBM Plex Sans, sans-serif", flowchart: { htmlLabels: false } });
              mermaid.run({ querySelector: "pre.mermaid", suppressErrors: true })
                .finally(() => { window.mermaidDone = true; });
            }
        """.trimIndent()
    }
}
