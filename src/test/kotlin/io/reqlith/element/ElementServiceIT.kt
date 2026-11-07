package io.reqlith.element

import io.reqlith.common.ConcurrentEditException
import io.reqlith.common.ProjectLockedException
import io.reqlith.common.ValidationException
import io.reqlith.project.MemberRole
import io.reqlith.project.ProjectRepository
import io.reqlith.project.ProjectService
import io.reqlith.project.ProjectStatus
import io.reqlith.support.Fixtures
import io.reqlith.support.IntegrationTest
import io.reqlith.support.TestUsers
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.access.AccessDeniedException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@IntegrationTest
class ElementServiceIT(
    @Autowired private val elements: ElementService,
    @Autowired private val projects: ProjectService,
    @Autowired private val projectRepository: ProjectRepository,
    @Autowired private val users: TestUsers,
    @Autowired private val fixtures: Fixtures,
) {
    private fun content(title: String, vararg links: TraceLink) = ElementContent(title, "## Description\n\nText", Priority.MUST, setOf("ux"), links.toSet())

    @Test
    fun `keys are numbered per type and never reused after deletion`() {
        val owner = users.create()
        val project = fixtures.project(owner)
        val fr1 = elements.create(owner, project, ElementType.FR, content("First"))
        val br1 = elements.create(owner, project, ElementType.BR, content("Business"))
        elements.delete(owner, projects.get(project.key), fr1.key)
        val fr2 = elements.create(owner, projects.get(project.key), ElementType.FR, content("Second"))

        assertEquals("FR-01", fr1.key)
        assertEquals("BR-01", br1.key)
        assertEquals("FR-02", fr2.key)
        assertEquals(listOf("BR-01", "FR-02"), elements.all(project).map { it.key })
    }

    @Test
    fun `contributors edit, reviewers do not`() {
        val owner = users.create()
        val contributor = users.create("Carla Conti")
        val reviewer = users.create("Rita Reyes")
        val project = fixtures.project(owner)
        fixtures.addMember(project, contributor, MemberRole.CONTRIBUTOR)
        fixtures.addMember(project, reviewer, MemberRole.REVIEWER)

        val element = elements.create(contributor, project, ElementType.SR, content("Stakeholder"))
        assertFailsWith<AccessDeniedException> { elements.create(reviewer, project, ElementType.SR, content("Nope")) }
        assertFailsWith<AccessDeniedException> { elements.update(reviewer, project, element.key, content("Nope"), element.rowVersion) }
    }

    @Test
    fun `update keeps links and rejects stale edits and self links`() {
        val owner = users.create()
        val project = fixtures.project(owner)
        val sr = elements.create(owner, project, ElementType.SR, content("Stakeholder"))
        val fr = elements.create(owner, project, ElementType.FR, content("Functional"))

        val updated = elements.update(owner, project, fr.key, content("Functional v2", TraceLink(LinkKind.DERIVED_FROM, sr.key)), fr.rowVersion)
        assertEquals(setOf(TraceLink(LinkKind.DERIVED_FROM, "SR-01")), elements.get(project, fr.key).links)
        assertEquals("Functional v2", elements.get(project, fr.key).title)

        assertFailsWith<ConcurrentEditException> { elements.update(owner, project, fr.key, content("Stale"), fr.rowVersion) }
        assertFailsWith<ValidationException> {
            elements.update(owner, project, fr.key, content("Self", TraceLink(LinkKind.DEPENDS_ON, fr.key)), updated.rowVersion)
        }
    }

    @Test
    fun `working copy is read-only while in review`() {
        val owner = users.create()
        val project = fixtures.project(owner)
        val element = elements.create(owner, project, ElementType.FR, content("Functional"))
        project.status = ProjectStatus.IN_REVIEW
        projectRepository.save(project)

        val locked = projects.get(project.key)
        assertFailsWith<ProjectLockedException> { elements.create(owner, locked, ElementType.FR, content("New")) }
        assertFailsWith<ProjectLockedException> { elements.update(owner, locked, element.key, content("Changed"), element.rowVersion) }
        assertFailsWith<ProjectLockedException> { elements.delete(owner, locked, element.key) }
        assertFailsWith<ProjectLockedException> { projects.updateBasicInfo(owner, locked, "x", locked.rowVersion) }
    }
}
