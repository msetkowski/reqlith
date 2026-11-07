package io.reqlith.export

import io.reqlith.config.ReqlithProperties
import org.springframework.core.io.ByteArrayResource
import org.springframework.http.MediaType
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException

class PdfRenderingException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

/**
 * Prints self-contained HTML to PDF in the separate rendering service (Gotenberg: headless Chromium behind
 * an HTTP API), so Mermaid diagrams are drawn exactly as in the browser while the application image stays
 * small. The page gets no network access: fonts are embedded and mermaid.js is sent along as a file.
 */
@Component
class PdfRenderer(properties: ReqlithProperties) {
    private val settings = properties.pdf

    private val client = RestClient.builder()
        .baseUrl(settings.url)
        .requestFactory(
            SimpleClientHttpRequestFactory().apply {
                setConnectTimeout(5_000)
                setReadTimeout(settings.timeout.toMillis().toInt())
            },
        )
        .build()

    private val mermaid: ByteArray by lazy {
        requireNotNull(javaClass.getResourceAsStream("/static/vendor/mermaid.min.js")) { "mermaid.min.js missing" }.use { it.readAllBytes() }
    }

    fun render(html: String, title: String): ByteArray {
        val form = LinkedMultiValueMap<String, Any>().apply {
            add("files", file("index.html", html.toByteArray()))
            if (html.contains(MERMAID_MARKER)) add("files", file("mermaid.min.js", mermaid))
            add("files", file("header.html", HEADER.toByteArray()))
            add("files", file("footer.html", footer(title).toByteArray()))
            // A4 with margins, in inches.
            add("paperWidth", "8.27")
            add("paperHeight", "11.69")
            add("marginTop", "0.63")
            add("marginBottom", "0.71")
            add("marginLeft", "0.55")
            add("marginRight", "0.55")
            add("printBackground", "true")
            // print.html sets this once Mermaid has drawn every diagram (immediately when there are none).
            add("waitForExpression", "window.mermaidDone === true")
        }
        return try {
            client.post()
                .uri("/forms/chromium/convert/html")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .header("Gotenberg-Output-Filename", "prd")
                .body(form)
                .retrieve()
                .body(ByteArray::class.java)
                ?: throw PdfRenderingException("The PDF rendering service returned no document")
        } catch (e: RestClientException) {
            throw PdfRenderingException("The PDF rendering service at ${settings.url} failed: ${e.message}", e)
        }
    }

    private fun file(name: String, bytes: ByteArray) = object : ByteArrayResource(bytes) {
        override fun getFilename() = name
    }

    private fun footer(title: String) = """
        <html><head><style>
          html, body { margin: 0; width: 100%; }
          .bar { box-sizing: border-box; width: 100%; padding: 0 14mm; font: 8px sans-serif; color: #5B6170;
                 display: flex; justify-content: space-between; }
        </style></head>
        <body><div class="bar"><span>${escape(title)}</span>
          <span><span class="pageNumber"></span> / <span class="totalPages"></span></span></div></body></html>
    """.trimIndent()

    private fun escape(text: String) = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    companion object {
        /** Rendered Mermaid blocks; mermaid.js is only sent when the document has diagrams. */
        const val MERMAID_MARKER = "<pre class=\"mermaid\">"
        private const val HEADER = "<html><head></head><body></body></html>"
    }
}
