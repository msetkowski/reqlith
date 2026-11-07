package io.reqlith.service

import io.reqlith.exception.ProjectLockedException
import io.reqlith.exception.ValidationException
import io.reqlith.model.dto.ElementContent
import io.reqlith.model.dto.ReqlithOidcUser
import io.reqlith.model.entity.Decision
import io.reqlith.model.entity.ElementType
import io.reqlith.model.entity.MemberRole
import io.reqlith.model.entity.Project
import io.reqlith.model.entity.ProjectStatus
import io.reqlith.model.entity.Suggestion
import io.reqlith.model.entity.VersionStatus
import io.reqlith.repository.ProjectVersionRepository
import io.reqlith.support.Fixtures
import io.reqlith.support.IntegrationTest
import io.reqlith.support.TestUsers
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.dao.DataAccessException
import org.springframework.security.access.AccessDeniedException
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@IntegrationTest
class ReviewServiceIT(
    @Autowired private val reviews: ReviewService,
    @Autowired private val versions: VersionService,
    @Autowired private val versionRepository: ProjectVersionRepository,
    @Autowired private val projects: ProjectService,
    @Autowired private val elements: ElementService,
    @Autowired private val roles: RoleService,
    @Autowired private val users: TestUsers,
    @Autowired private val fixtures: Fixtures,
) {
    private class Team(val owner: ReqlithOidcUser, val r1: ReqlithOidcUser, val r2: ReqlithOidcUser, val project: Project)

    private fun team(): Team {
        val owner = users.create("Olive Owens")
        val r1 = users.create("Rita Reyes")
        val r2 = users.create("Raj Rao")
        val project = fixtures.project(owner)
        fixtures.addMember(project, r1, MemberRole.REVIEWER)
        fixtures.addMember(project, r2, MemberRole.REVIEWER)
        elements.create(owner, project, ElementType.BR, ElementContent("Fewer contacts", "Goal", null, emptySet(), emptySet()))
        return Team(owner, r1, r2, project)
    }

    private fun fresh(project: Project) = projects.get(project.key)

    @Test
    fun `full lifecycle - submit, approve v1, edit, reject v2-rc1, resubmit v2-rc2, approve v2`() {
        val t = team()
        val rc1 = reviews.submit(t.owner, t.project, listOf(t.r1.userId, t.r2.userId))
        assertEquals("v1-rc1", rc1.label)
        assertEquals(ProjectStatus.IN_REVIEW, fresh(t.project).status)
        assertFailsWith<ProjectLockedException> {
            elements.create(t.owner, fresh(t.project), ElementType.FR, ElementContent("Blocked", null, null, emptySet(), emptySet()))
        }

        reviews.decide(t.r1, fresh(t.project), Decision.ACCEPT, null)
        assertEquals(ProjectStatus.IN_REVIEW, fresh(t.project).status, "one of two reviewers is not enough")
        reviews.decide(t.r2, fresh(t.project), Decision.ACCEPT, "Looks good")

        val approved = fresh(t.project)
        assertEquals(ProjectStatus.DRAFT, approved.status)
        assertEquals(1, approved.latestApprovedVersion)
        assertEquals("v1", versions.latestApproved(approved)!!.label)
        assertFalse(versions.hasChangesSinceApproval(approved))

        // Submitting unchanged content is refused; an edit starts the next version.
        assertFailsWith<ValidationException> { reviews.submit(t.owner, approved, listOf(t.r1.userId)) }
        elements.create(t.owner, approved, ElementType.FR, ElementContent("Request refund", "Body", null, emptySet(), emptySet()))
        assertTrue(versions.hasChangesSinceApproval(fresh(t.project)))

        val v2rc1 = reviews.submit(t.owner, fresh(t.project), listOf(t.r1.userId))
        assertEquals("v2-rc1", v2rc1.label)
        assertEquals(1, v2rc1.basedOnVersion)
        assertFailsWith<ValidationException> { reviews.decide(t.r1, fresh(t.project), Decision.REJECT, " ") }
        reviews.decide(t.r1, fresh(t.project), Decision.REJECT, "Out of scope")
        assertEquals(VersionStatus.REJECTED, versionRepository.findById(v2rc1.id).get().status)
        assertEquals(ProjectStatus.DRAFT, fresh(t.project).status)

        val v2rc2 = reviews.submit(t.owner, fresh(t.project), listOf(t.r2.userId))
        assertEquals("v2-rc2", v2rc2.label)
        reviews.decide(t.r2, fresh(t.project), Decision.ACCEPT, null)
        assertEquals(2, fresh(t.project).latestApprovedVersion)
        assertEquals(listOf("v2", "v2-rc1", "v1"), versions.history(fresh(t.project)).map { it.label })
        assertEquals(listOf("BR-01"), versions.document(versions.bySlug(fresh(t.project), "1")).elements.map { it.key })
        assertEquals(listOf("BR-01", "FR-01"), versions.document(versions.bySlug(fresh(t.project), "2")).elements.map { it.key })
    }

    @Test
    fun `approved versions cannot be modified through JPA either`() {
        val t = team()
        val rc = reviews.submit(t.owner, t.project, listOf(t.r1.userId))
        reviews.decide(t.r1, fresh(t.project), Decision.ACCEPT, null)
        val v1 = versionRepository.findById(rc.id).get()
        v1.closedAt = v1.closedAt!!.plusSeconds(1)
        assertFailsWith<DataAccessException> { versionRepository.saveAndFlush(v1) }
    }

    @Test
    fun `only assigned reviewers decide, once, and owners cannot review themselves`() {
        val t = team()
        assertFailsWith<ValidationException> { reviews.submit(t.owner, t.project, emptyList()) }
        assertFailsWith<ValidationException> { reviews.submit(t.owner, t.project, listOf(t.owner.userId)) }
        assertFailsWith<ValidationException> { reviews.submit(t.owner, t.project, listOf(users.create("Outsider").userId)) }
        assertFailsWith<AccessDeniedException> { reviews.submit(t.r1, t.project, listOf(t.r2.userId)) }

        reviews.submit(t.owner, t.project, listOf(t.r1.userId, t.r2.userId))
        assertFailsWith<AccessDeniedException> { reviews.decide(t.owner, fresh(t.project), Decision.ACCEPT, null) }
        reviews.decide(t.r1, fresh(t.project), Decision.ACCEPT, null)
        assertFailsWith<ValidationException> { reviews.decide(t.r1, fresh(t.project), Decision.ACCEPT, null) }
        assertEquals(listOf(fresh(t.project).currentCandidateId), reviews.pendingFor(t.r2).map { it.id })
        assertTrue(reviews.pendingFor(t.r1).isEmpty())
    }

    @Test
    fun `withdrawing reopens the working copy and the candidate stays in history`() {
        val t = team()
        val rc = reviews.submit(t.owner, t.project, listOf(t.r1.userId))
        assertFailsWith<AccessDeniedException> { reviews.withdraw(t.r1, fresh(t.project)) }
        reviews.withdraw(t.owner, fresh(t.project))

        val project = fresh(t.project)
        assertEquals(ProjectStatus.DRAFT, project.status)
        assertNull(project.currentCandidateId)
        assertEquals(VersionStatus.WITHDRAWN, versionRepository.findById(rc.id).get().status)
        assertEquals("v1-rc2", reviews.submit(t.owner, project, listOf(t.r1.userId)).label)
    }

    @Test
    fun `suggestions request changes and are applied to the working copy afterwards`() {
        val t = team()
        reviews.submit(t.owner, t.project, listOf(t.r1.userId))
        assertFailsWith<AccessDeniedException> { reviews.suggest(t.r2, fresh(t.project), "BR-01", "x", null) }
        val suggestion = reviews.suggest(t.r1, fresh(t.project), "BR-01", "Reduce contacts by 30%", "Be specific")
        reviews.suggest(t.r1, fresh(t.project), Suggestion.BASIC_INFO, null, "Add a baseline")
        reviews.comment(t.owner, fresh(t.project), null, "Thanks")
        assertFailsWith<ProjectLockedException> { reviews.resolve(t.owner, fresh(t.project), suggestion.id, apply = true) }

        reviews.decide(t.r1, fresh(t.project), Decision.CHANGES, null) // suggestions count as the explanation
        assertEquals(2, reviews.openSuggestions(fresh(t.project)).size)

        reviews.resolve(t.owner, fresh(t.project), suggestion.id, apply = true)
        assertEquals("Reduce contacts by 30%", elements.get(fresh(t.project), "BR-01").bodyMd)
        val other = reviews.openSuggestions(fresh(t.project)).single()
        reviews.resolve(t.owner, fresh(t.project), other.id, apply = false)
        assertTrue(reviews.openSuggestions(fresh(t.project)).isEmpty())
    }

    @Test
    fun `snapshots keep the role description they were approved with`() {
        val t = team()
        val role = roles.createAndAdd(t.owner, t.project, "Customer " + UUID.randomUUID().toString().take(6), "Original", "Context")
        reviews.submit(t.owner, fresh(t.project), listOf(t.r1.userId))
        reviews.decide(t.r1, fresh(t.project), Decision.ACCEPT, null)

        roles.updateGlobal(t.owner, role, role.name, "Rewritten")
        val v1 = versions.document(versions.bySlug(fresh(t.project), "1"))
        assertEquals("Original", v1.roles.single().globalDescription)
        assertTrue(versions.hasChangesSinceApproval(fresh(t.project)), "a global role edit is a change for the next version")
    }
}
