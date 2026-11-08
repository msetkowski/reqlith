package io.reqlith.service

import io.reqlith.exception.ConcurrentEditException
import io.reqlith.exception.ProjectLockedException
import io.reqlith.exception.ValidationException
import io.reqlith.model.dto.ElementContent
import io.reqlith.model.entity.Decision
import io.reqlith.model.entity.ElementType
import io.reqlith.model.entity.LinkKind
import io.reqlith.model.entity.LinkSystem
import io.reqlith.model.entity.MemberRole
import io.reqlith.model.entity.Priority
import io.reqlith.model.entity.ProjectStatus
import io.reqlith.model.entity.TraceLink
import io.reqlith.repository.ProjectRepository
import io.reqlith.support.Fixtures
import io.reqlith.support.IntegrationTest
import io.reqlith.support.TestUsers
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.access.AccessDeniedException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@IntegrationTest
class ElementServiceIT(
    @Autowired private val elements: ElementService,
    @Autowired private val reviews: ReviewService,
    @Autowired private val versions: VersionService,
    @Autowired private val externalLinks: ExternalLinkService,
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

    @Test
    fun `removing an element removes links pointing at it and its external links`() {
        val owner = users.create()
        val project = fixtures.project(owner)
        val sr = elements.create(owner, project, ElementType.SR, content("Stakeholder"))
        val br = elements.create(owner, project, ElementType.BR, content("Business"))
        val fr = elements.create(
            owner,
            project,
            ElementType.FR,
            content("Functional", TraceLink(LinkKind.DERIVED_FROM, sr.key), TraceLink(LinkKind.DERIVED_FROM, br.key)),
        )
        val sc = elements.create(owner, project, ElementType.SC, content("Scenario", TraceLink(LinkKind.DEPENDS_ON, sr.key)))
        externalLinks.add(owner, project, LinkSystem.JIRA, "https://jira.example.com/browse/PAY-1", null, null, sr.key)
        externalLinks.add(owner, project, LinkSystem.JIRA, "https://jira.example.com/browse/PAY-2", null, null, fr.key)

        elements.delete(owner, projects.get(project.key), sr.key)

        assertEquals(setOf(TraceLink(LinkKind.DERIVED_FROM, br.key)), elements.get(project, fr.key).links)
        assertTrue(elements.get(project, sc.key).links.isEmpty())
        assertEquals(listOf(fr.key), externalLinks.all(project).map { it.elementKey })
    }

    @Test
    fun `removing an approved element is a draft change that needs approval`() {
        val owner = users.create()
        val reviewer = users.create("Rita Reyes")
        val project = fixtures.project(owner)
        fixtures.addMember(project, reviewer, MemberRole.REVIEWER)
        val br = elements.create(owner, project, ElementType.BR, content("Business"))
        val as1 = elements.create(owner, project, ElementType.AS, content("Assumption", TraceLink(LinkKind.DEPENDS_ON, br.key)))
        reviews.submit(owner, project, listOf(reviewer.userId))
        reviews.decide(reviewer, projects.get(project.key), Decision.ACCEPT, null)

        elements.delete(owner, projects.get(project.key), br.key)

        val draft = projects.get(project.key)
        assertTrue(versions.hasChangesSinceApproval(draft))
        assertNotNull(versions.approvedDocument(draft, 1)!!.element(br.key), "v1 keeps the removed element")
        assertTrue(elements.get(draft, as1.key).links.isEmpty())
        val v2 = reviews.submit(owner, draft, listOf(reviewer.userId))
        assertNull(versions.document(v2).element(br.key))
    }
}
