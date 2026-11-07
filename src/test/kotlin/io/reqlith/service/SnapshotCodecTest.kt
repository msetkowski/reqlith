package io.reqlith.service

import io.reqlith.model.dto.ChangeKind
import io.reqlith.model.dto.DocElement
import io.reqlith.model.dto.DocLink
import io.reqlith.model.dto.DocProject
import io.reqlith.model.dto.DocRole
import io.reqlith.model.dto.ProjectDocument
import io.reqlith.model.entity.ElementType
import io.reqlith.model.entity.LinkKind
import io.reqlith.model.entity.Priority
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class SnapshotCodecTest {
    private val codec = SnapshotCodec()
    private val roleId = UUID.fromString("00000000-0000-0000-0000-000000000001")

    private fun document(body: String = "## Description\nText", basicInfo: String = "## Problem statement\nSlow refunds") = ProjectDocument(
        project = DocProject("PRJ-PAY-001", "Refunds", "Summary", "Payments", listOf("refunds"), listOf("Payments")),
        basicInfo = basicInfo,
        elements = listOf(
            DocElement("BR-01", ElementType.BR, "Fewer contacts", "Goal", Priority.MUST, emptyList(), emptyList(), 1),
            DocElement("FR-01", ElementType.FR, "Request refund", body, Priority.SHOULD, listOf("ux"), listOf(DocLink(LinkKind.DERIVED_FROM, "BR-01")), 1),
        ),
        roles = listOf(DocRole(roleId, "RO-01", "Customer", "Buys things", "Requests refunds")),
    )

    @Test
    fun `snapshot round-trips to an equal document`() {
        val original = document()
        assertEquals(original, codec.read(codec.write(original).json))
    }

    @Test
    fun `equal content gives equal hashes, any change a different one`() {
        val first = codec.write(document())
        assertEquals(first.hash, codec.write(document()).hash)
        assertNotEquals(first.hash, codec.write(document(body = "## Description\nOther")).hash)
        assertEquals(64, first.hash.length)
        assertTrue(codec.verify(first.json, first.hash))
        assertFalse(codec.verify(first.json.replace("Customer", "Client"), first.hash))
    }

    @Test
    fun `line endings are normalized before hashing`() {
        assertEquals(codec.write(document(basicInfo = "a\nb")).hash, codec.write(document(basicInfo = "a\r\nb")).hash)
    }

    @Test
    fun `canonical json has sorted keys and no whitespace`() {
        val json = codec.write(document()).json
        assertTrue(json.startsWith("""{"basicInfo":"""), json)
        assertTrue(json.indexOf("\"elements\"") < json.indexOf("\"project\""))
        assertFalse(json.contains("\n "))
    }

    @Test
    fun `changes are detected by key`() {
        val before = document()
        val after = document(body = "changed").let { doc ->
            doc.copy(
                elements = doc.elements.filter { it.key != "BR-01" } +
                    DocElement("SC-01", ElementType.SC, "Scenario", null, null, emptyList(), emptyList(), 1),
            )
        }
        val changes = DocumentChanges(before, after)
        assertEquals(ChangeKind.CHANGED, changes.of("FR-01"))
        assertEquals(ChangeKind.NEW, changes.of("SC-01"))
        assertEquals(listOf("BR-01"), changes.removedElements.map { it.key })
        assertEquals(ChangeKind.UNCHANGED, changes.of("RO-01"))
        assertEquals("1 new, 1 changed, 1 removed", changes.summary)
        assertFalse(DocumentChanges(before, before).hasChanges)
    }
}
