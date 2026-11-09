package io.reqlith.service

import com.sun.net.httpserver.HttpServer
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.reqlith.config.ReqlithProperties
import io.reqlith.controller.Formats
import io.reqlith.exception.PdfRenderingException
import io.reqlith.model.dto.ExportOptions
import io.reqlith.model.entity.Element
import io.reqlith.model.entity.ElementType
import io.reqlith.model.entity.LinkKind
import io.reqlith.model.entity.ProjectVersion
import io.reqlith.model.entity.TraceLink
import io.reqlith.model.entity.VersionStatus
import io.reqlith.repository.ExportCache
import io.reqlith.support.UnitFixtures
import io.reqlith.support.UnitFixtures.NOW
import org.junit.jupiter.api.Test
import org.thymeleaf.TemplateEngine
import java.net.InetSocketAddress
import java.time.Duration
import kotlin.test.assertContains
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class ExportServiceTest {
    private val versions = mockk<VersionService>()
    private val reviews = mockk<ReviewService>()
    private val documents = mockk<ProjectDocumentService>()
    private val users = mockk<UserService> { every { byIds(any()) } returns emptyMap() }
    private val templates = mockk<TemplateEngine> { every { process("export/print", any()) } returns "<html>print</html>" }
    private val pdf = mockk<PdfRenderer> { every { render(any(), any()) } returns byteArrayOf(1, 2, 3) }
    private val cache = mockk<ExportCache>(relaxUnitFun = true)
    private val properties = ReqlithProperties(idp = ReqlithProperties.Idp("http://idp"))
    private val baseline = mockk<BaselineService> {
        every { all() } returns emptyList()
        every { fingerprint() } returns "bl"
    }
    private val service = ExportService(
        versions, reviews, documents, users, MarkdownRenderer(), templates, pdf, cache,
        Formats(UnitFixtures.clock, properties), baseline,
    )

    private val project = UnitFixtures.project()
    private val document = UnitFixtures.document(project, UnitFixtures.element("BR-01", "Goal"))
    private fun version(status: VersionStatus) = SnapshotCodec().write(document)
        .let { ProjectVersion(project.id, 1, 1, it.json, 1, it.hash, null, project.createdBy, NOW) }
        .also { if (status != VersionStatus.IN_REVIEW) it.close(status, NOW) }

    init {
        every { versions.history(project) } returns emptyList()
        every { documents.fromWorkingCopy(project) } returns document
    }

    @Test
    fun `markdown exports are named after the project and version, drafts are marked`() {
        val draft = service.markdown(project, null, ExportOptions())
        assertEquals("PRD-PAY-001-draft.md", draft.name)
        assertContains(String(draft.bytes), "**DRAFT**")

        val v1 = version(VersionStatus.APPROVED)
        every { versions.document(v1) } returns document
        val approved = service.markdown(project, v1, ExportOptions())
        assertEquals("PRD-PAY-001-v1.md", approved.name)
        assertFalse(String(approved.bytes).contains("DRAFT"))
        assertContains(String(approved.bytes), v1.contentHash)
    }

    @Test
    fun `pdfs of closed versions come from the cache when present`() {
        val v1 = version(VersionStatus.APPROVED)
        every { cache.get(v1.contentHash, "pdf", "t1a1c0|v1|APPROVED|bl") } returns byteArrayOf(9)
        assertContentEquals(byteArrayOf(9), service.pdf(project, v1, ExportOptions()).bytes)
        verify(exactly = 0) { pdf.render(any(), any()) }
    }

    @Test
    fun `pdfs are rendered and cached on a miss, open candidates and drafts are never cached`() {
        val v1 = version(VersionStatus.APPROVED)
        every { versions.document(any()) } returns document
        every { cache.get(any(), any(), any()) } returns null
        service.pdf(project, v1, ExportOptions())
        verify { cache.put(v1.contentHash, "pdf", "t1a1c0|v1|APPROVED|bl", byteArrayOf(1, 2, 3)) }

        service.pdf(project, version(VersionStatus.IN_REVIEW), ExportOptions())
        service.pdf(project, null, ExportOptions())
        verify(exactly = 1) { cache.put(any(), any(), any(), any()) }
    }
}

class ProjectDocumentServiceTest {
    @Test
    fun `the working copy becomes a document with sorted, stable content`() {
        val project = UnitFixtures.project()
        val element = Element(project.id, ElementType.FR, "FR-01", "Refund", "Body", 1, NOW, NOW, project.createdBy).apply {
            tags = sortedSetOf("ux", "api")
            links = mutableSetOf(TraceLink(LinkKind.VERIFIED_BY, "SC-01"), TraceLink(LinkKind.DERIVED_FROM, "SR-02"), TraceLink(LinkKind.DERIVED_FROM, "SR-01"))
        }
        val elements = mockk<ElementService> { every { all(project) } returns listOf(element) }
        val roles = mockk<RoleService> { every { forProject(project) } returns emptyList() }
        project.tags = sortedSetOf("b", "a")

        val document = ProjectDocumentService(elements, roles).fromWorkingCopy(project)

        assertEquals(listOf("a", "b"), document.project.tags)
        assertEquals(listOf("api", "ux"), document.elements.single().tags)
        assertEquals(listOf("SR-01", "SR-02", "SC-01"), document.elements.single().links.map { it.toKey })
        assertEquals("## Problem statement", document.basicInfo)
    }
}

/** The client for the rendering service (Gotenberg), against a local HTTP server. */
class PdfRendererTest {
    private fun withServer(status: Int, body: ByteArray, test: (url: String, requests: MutableList<String>) -> Unit) {
        val requests = mutableListOf<String>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/forms/chromium/convert/html") { exchange ->
            requests += exchange.requestHeaders.getFirst("Content-Type") + "\n" + exchange.requestBody.readAllBytes().toString(Charsets.UTF_8)
            exchange.sendResponseHeaders(status, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.start()
        try {
            test("http://127.0.0.1:${server.address.port}", requests)
        } finally {
            server.stop(0)
        }
    }

    private fun renderer(url: String) = PdfRenderer(
        ReqlithProperties(idp = ReqlithProperties.Idp("http://idp"), pdf = ReqlithProperties.Pdf(url, Duration.ofSeconds(5))),
    )

    @Test
    fun `sends the page with header, footer and A4 settings, and mermaid only when needed`() = withServer(200, "%PDF-1.7".toByteArray()) { url, requests ->
        assertContentEquals("%PDF-1.7".toByteArray(), renderer(url).render("<p>plain</p>", "PRJ-PAY-001 · v1 <Refunds>"))
        renderer(url).render("""<pre class="mermaid">flowchart LR</pre>""", "t")

        val (plain, diagram) = requests
        assertContains(plain, "multipart/form-data")
        listOf("index.html", "footer.html", "header.html", "waitForExpression", "window.mermaidDone === true", "8.27", "11.69").forEach {
            assertContains(plain, it)
        }
        assertContains(plain, "PRJ-PAY-001 · v1 &lt;Refunds&gt;")
        assertFalse(plain.contains("mermaid.min.js"))
        assertContains(diagram, "filename=\"mermaid.min.js\"")
    }

    @Test
    fun `renderer failures become a PdfRenderingException`() = withServer(503, "down".toByteArray()) { url, _ ->
        assertFailsWith<PdfRenderingException> { renderer(url).render("<p>x</p>", "t") }
    }

    @Test
    fun `an unreachable renderer becomes a PdfRenderingException`() {
        assertFailsWith<PdfRenderingException> { renderer("http://127.0.0.1:1").render("<p>x</p>", "t") }
    }
}
