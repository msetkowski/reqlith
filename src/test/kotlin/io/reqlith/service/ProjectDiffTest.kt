package io.reqlith.service

import io.reqlith.model.dto.ChangeKind
import io.reqlith.model.dto.DocLink
import io.reqlith.model.dto.DocRole
import io.reqlith.model.entity.LinkKind
import io.reqlith.model.entity.Priority
import io.reqlith.support.UnitFixtures
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProjectDiffTest {
    private val project = UnitFixtures.project()
    private val roleId = UUID.randomUUID()
    private val before = UnitFixtures.document(project, UnitFixtures.element("BR-01"), UnitFixtures.element("FR-01", "Refund within 14 days"))
        .let { it.copy(roles = listOf(DocRole(roleId, "RO-01", "Customer", "Buys", "Refunds"))) }

    @Test
    fun `changed, new and removed items in document order, unchanged ones left out`() {
        val after = before.copy(
            elements = listOf(
                UnitFixtures.element("FR-01", "Refund within 30 days").copy(title = "Request a refund", priority = Priority.MUST,
                    links = listOf(DocLink(LinkKind.DERIVED_FROM, "BR-01"))),
                UnitFixtures.element("SC-01"),
            ),
            roles = listOf(DocRole(roleId, "RO-01", "Customer", "Buys things", "Refunds")),
        )
        val diff = ProjectDiff(before, after)

        assertEquals(listOf("FR-01" to ChangeKind.CHANGED, "BR-01" to ChangeKind.REMOVED, "RO-01" to ChangeKind.CHANGED, "SC-01" to ChangeKind.NEW),
            diff.items.map { it.key to it.kind }.sortedBy { listOf("FR-01", "BR-01", "RO-01", "SC-01").indexOf(it.first) })
        val fr = diff.item("FR-01")!!
        assertTrue(fr.titleChanged)
        assertContains(fr.properties, "Priority: — → Must")
        assertContains(fr.properties, "Link added: Derived from BR-01")
        assertTrue(fr.lines.filterNotNull().any { "<ins>30</ins>" in it.html })
        assertNull(diff.item("missing"))
        assertTrue(diff.basicInfo.isEmpty())
    }

    @Test
    fun `without a previous version everything is new`() {
        val diff = ProjectDiff(null, before)
        assertTrue(diff.items.all { it.kind == ChangeKind.NEW })
        assertTrue(diff.projectProperties.isEmpty())
    }

    @Test
    fun `project property changes are listed`() {
        val after = before.copy(project = before.project.copy(title = "Refunds 2", tags = listOf("q1")))
        assertEquals(listOf("Title: Self-service refunds → Refunds 2", "Tags: — → q1"), ProjectDiff(before, after).projectProperties)
    }
}
