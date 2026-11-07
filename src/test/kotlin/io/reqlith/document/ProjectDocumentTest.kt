package io.reqlith.document

import io.reqlith.element.ElementType
import io.reqlith.element.LinkKind
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals

class ProjectDocumentTest {

    private fun element(key: String, vararg links: Pair<LinkKind, String>) = DocElement(
        key = key, type = ElementType.ofKey(key)!!, title = key, body = null, priority = null, tags = emptyList(),
        links = links.map { DocLink(it.first, it.second) }, order = 0,
    )

    private fun document(vararg elements: DocElement) = ProjectDocument(
        project = DocProject("PRJ-GEN-001", "T", null, "General", emptyList(), emptyList()),
        basicInfo = "",
        elements = elements.toList(),
        roles = listOf(DocRole(UUID.randomUUID(), "RO-01", "Customer", null, null)),
    )

    @Test
    fun `functional requirement is covered by a scenario link in either direction`() {
        val doc = document(
            element("FR-01", LinkKind.VERIFIED_BY to "SC-01"),
            element("FR-02"),
            element("FR-03"),
            element("SC-01", LinkKind.VERIFIED_BY to "FR-03"),
        )
        assertEquals(listOf("FR-02"), doc.functionalWithoutScenario().map { it.key })
    }

    @Test
    fun `links to missing elements are broken, links to roles are not`() {
        val doc = document(element("FR-01", LinkKind.DERIVED_FROM to "SR-09", LinkKind.DEPENDS_ON to "RO-01"))
        assertEquals(listOf("FR-01" to "SR-09"), doc.brokenLinks().map { it.first to it.second.toKey })
    }

    @Test
    fun `incoming links are grouped by target`() {
        val doc = document(element("FR-01", LinkKind.DERIVED_FROM to "SR-01"), element("SR-01"))
        assertEquals(listOf("FR-01"), doc.incomingLinks()["SR-01"]!!.map { it.second })
    }
}
