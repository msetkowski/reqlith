package io.reqlith.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.reqlith.exception.ConcurrentEditException
import io.reqlith.exception.ProjectLockedException
import io.reqlith.exception.ValidationException
import io.reqlith.model.dto.ElementContent
import io.reqlith.model.entity.Element
import io.reqlith.model.entity.ElementType
import io.reqlith.model.entity.LinkKind
import io.reqlith.model.entity.Priority
import io.reqlith.model.entity.ProjectStatus
import io.reqlith.model.entity.TraceLink
import io.reqlith.repository.ElementRepository
import io.reqlith.repository.KeyCounter
import io.reqlith.support.UnitFixtures
import io.reqlith.support.UnitFixtures.NOW
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ElementServiceTest {
    private val elements = mockk<ElementRepository>(relaxUnitFun = true) { every { save(any()) } answers { firstArg() } }
    private val projectService = mockk<ProjectService>(relaxUnitFun = true)
    private val access = mockk<ProjectAccess>(relaxUnitFun = true)
    private val keyCounter = mockk<KeyCounter>()
    private val service = ElementService(elements, projectService, access, keyCounter, UnitFixtures.clock)

    private val owner = UnitFixtures.user()
    private val project = UnitFixtures.project(owner)
    private fun content(title: String = "Request refund", vararg links: TraceLink) =
        ElementContent(title, "Body\r\nline", Priority.MUST, setOf("ux"), links.toSet())

    @Test
    fun `new elements get the next key of their type and normalized text`() {
        every { keyCounter.next("ELEMENT_KEY:${project.id}", "FR") } returns 7
        val element = service.create(owner, project, ElementType.FR, content("  Request refund  "))
        assertEquals("FR-07", element.key)
        assertEquals("Request refund", element.title)
        assertEquals("Body\nline", element.bodyMd)
        assertEquals(setOf("ux"), element.tags)
        verify { access.requireEditor(project, owner); projectService.touch(project, owner) }
    }

    @Test
    fun `titles are required and links must be valid keys other than the element itself`() {
        every { keyCounter.next(any(), any()) } returns 1
        assertFailsWith<ValidationException> { service.create(owner, project, ElementType.BR, content(" ")) }
        assertFailsWith<ValidationException> { service.create(owner, project, ElementType.BR, content("x".repeat(301))) }
        assertFailsWith<ValidationException> { service.create(owner, project, ElementType.BR, content("ok", TraceLink(LinkKind.DEPENDS_ON, "not a key"))) }
        assertFailsWith<ValidationException> { service.create(owner, project, ElementType.BR, content("ok", TraceLink(LinkKind.DEPENDS_ON, "BR-01"))) }
    }

    @Test
    fun `updates replace links and tags, stale forms and locked projects are refused`() {
        val element = Element(project.id, ElementType.FR, "FR-01", "Old", null, 1, NOW, NOW, owner.userId)
        every { elements.findByProjectIdAndKey(project.id, "FR-01") } returns element

        service.update(owner, project, "FR-01", content("New", TraceLink(LinkKind.DERIVED_FROM, "SR-01")), element.rowVersion)
        assertEquals("New", element.title)
        assertEquals(setOf(TraceLink(LinkKind.DERIVED_FROM, "SR-01")), element.links)

        assertFailsWith<ConcurrentEditException> { service.update(owner, project, "FR-01", content(), element.rowVersion + 1) }
        project.status = ProjectStatus.IN_REVIEW
        assertFailsWith<ProjectLockedException> { service.update(owner, project, "FR-01", content(), element.rowVersion) }
        assertFailsWith<ProjectLockedException> { service.delete(owner, project, "FR-01") }
    }

    @Test
    fun `elements are listed by type, then creation order`() {
        val b2 = Element(project.id, ElementType.BR, "BR-02", "b2", null, 2, NOW, NOW, owner.userId)
        val f1 = Element(project.id, ElementType.FR, "FR-01", "f1", null, 1, NOW, NOW, owner.userId)
        val b1 = Element(project.id, ElementType.BR, "BR-01", "b1", null, 1, NOW, NOW, owner.userId)
        every { elements.findByProjectId(project.id) } returns listOf(f1, b2, b1)
        assertEquals(listOf("BR-01", "BR-02", "FR-01"), service.all(project).map { it.key })
    }
}
