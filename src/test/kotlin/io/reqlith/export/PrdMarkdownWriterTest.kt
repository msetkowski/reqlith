package io.reqlith.export

import io.reqlith.document.DocElement
import io.reqlith.document.DocLink
import io.reqlith.document.DocProject
import io.reqlith.document.DocRole
import io.reqlith.document.ProjectDocument
import io.reqlith.element.ElementType
import io.reqlith.element.LinkKind
import io.reqlith.element.Priority
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PrdMarkdownWriterTest {

    private val document = ProjectDocument(
        project = DocProject("PRJ-PAY-001", "Self-service refunds", "Refunds without support", "Payments", listOf("refunds"), listOf("Payments")),
        basicInfo = "## Problem statement\nSlow.\n\n## Scope\nRefunds",
        elements = listOf(
            DocElement("BR-01", ElementType.BR, "Fewer contacts", "## Goal\n-30%", Priority.MUST, emptyList(), emptyList(), 1),
            DocElement(
                "FR-01", ElementType.FR, "Request refund", "## Flow\n```mermaid\n# not a heading\nflowchart LR\n```", null, listOf("ux"),
                listOf(DocLink(LinkKind.DERIVED_FROM, "BR-01")), 1,
            ),
            DocElement("RK-01", ElementType.RK, "Abuse", "Limits", null, emptyList(), listOf(DocLink(LinkKind.MITIGATES, "FR-01")), 1),
        ),
        roles = listOf(DocRole(UUID.randomUUID(), "RO-01", "Customer", "Buys", "Requests refunds")),
    )

    private fun input(label: String = "v1") = PrdInput(
        document, label, "Approved", "7 Nov 2026", "ab".repeat(32),
        approvals = listOf(ApprovalRow("v1", "Approved", "7 Nov by Olive", "Rita: Accepted")),
        notes = listOf(ReviewNote("v1-rc1", "FR-01", "Rita", "Clarify")),
    )

    @Test
    fun `chapters follow the BABOK order with assumptions, risks and dependencies under basic information`() {
        val md = PrdMarkdownWriter.write(input(), ExportOptions())
        val order = listOf("# Self-service refunds", "## Version and approval record", "## 1. Basic information", "### Problem statement",
            "### Risks", "#### RK-01 · Abuse", "## 2. Business requirements", "### BR-01 · Fewer contacts", "## 4. Solution requirements · Functional",
            "## 6. Roles", "### RO-01 · Customer", "## 7. Scenarios", "## Appendix A. Traceability matrix")
        val positions = order.map { md.indexOf(it).also { pos -> assertTrue(pos >= 0, "missing '$it' in\n$md") } }
        assertEquals(positions.sorted(), positions)
        assertFalse(md.contains("Appendix B"), "review comments are opt-in")
    }

    @Test
    fun `headings inside bodies are shifted below the element heading, code fences untouched`() {
        val md = PrdMarkdownWriter.write(input(), ExportOptions())
        assertContains(md, "#### Goal")
        assertContains(md, "#### Flow")
        assertContains(md, "```mermaid\n# not a heading")
        assertContains(md, "**Traceability:** Derived from `BR-01`")
        assertContains(md, "| `BR-01` | Fewer contacts | — | Derived from · Refined by `FR-01` |".replace("Derived from · ", ""))
    }

    @Test
    fun `drafts are marked and optional parts can be switched off`() {
        val md = PrdMarkdownWriter.write(input(PrdMarkdownWriter.DRAFT), ExportOptions(traceabilityMatrix = false, approvalRecord = false, reviewComments = true))
        assertContains(md, "**DRAFT**")
        assertFalse(md.contains("Traceability matrix"))
        assertFalse(md.contains("approval record"))
        assertContains(md, "## Appendix B. Review comments")
    }

    @Test
    fun `shiftHeadings caps at level six`() {
        assertEquals("###### deep\nplain", PrdMarkdownWriter.shiftHeadings("##### deep\nplain", 3))
    }
}
