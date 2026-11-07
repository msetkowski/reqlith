package io.reqlith.project

import io.reqlith.common.ConcurrentEditException
import io.reqlith.common.ValidationException
import io.reqlith.support.Fixtures
import io.reqlith.support.IntegrationTest
import io.reqlith.support.TestUsers
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.access.AccessDeniedException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@IntegrationTest
class ProjectServiceIT(
    @Autowired private val projects: ProjectService,
    @Autowired private val memberships: MembershipService,
    @Autowired private val access: ProjectAccess,
    @Autowired private val users: TestUsers,
    @Autowired private val fixtures: Fixtures,
) {
    @Test
    fun `project keys are unique and sequential per workspace, creator becomes owner`() {
        val owner = users.create("Olive Owens")
        val first = fixtures.project(owner, "First")
        val second = fixtures.project(owner, "Second")

        assertTrue(first.key.matches(Regex("PRJ-GEN-\\d{3}")), first.key)
        assertEquals(first.key.takeLast(3).toInt() + 1, second.key.takeLast(3).toInt())
        assertEquals(MemberRole.OWNER, access.roleOf(first, owner))
        assertTrue(first.basicInfoMd!!.contains("## Problem statement"))
    }

    @Test
    fun `only editors change details and stale forms are rejected`() {
        val owner = users.create("Olive Owens")
        val reviewer = users.create("Rita Reyes")
        val project = fixtures.project(owner)
        fixtures.addMember(project, reviewer, MemberRole.REVIEWER)
        val details = ProjectDetails("Renamed", null, setOf("a", "b"), emptySet())

        assertFailsWith<AccessDeniedException> { projects.updateDetails(reviewer, projects.get(project.key), details, 0) }
        projects.updateDetails(owner, projects.get(project.key), details, projects.get(project.key).rowVersion)
        assertEquals("Renamed", projects.get(project.key).title)
        assertEquals(sortedSetOf("a", "b"), projects.get(project.key).tags.toSortedSet())
        assertFailsWith<ConcurrentEditException> { projects.updateDetails(owner, projects.get(project.key), details, 0) }
    }

    @Test
    fun `invited users join with the invited role, and the last owner cannot leave`() {
        val owner = users.create("Olive Owens")
        val contributor = users.create("Carla Conti")
        val project = fixtures.project(owner)

        val invitation = memberships.invite(owner, project, contributor.emailAddress!!.uppercase(), MemberRole.CONTRIBUTOR)
        assertEquals(listOf(invitation.id), memberships.pendingInvitationsFor(contributor).map { it.id })
        memberships.accept(contributor, invitation.id)
        assertEquals(MemberRole.CONTRIBUTOR, access.roleOf(project, contributor))
        assertTrue(memberships.pendingInvitationsFor(contributor).isEmpty())

        assertFailsWith<ValidationException> { memberships.remove(owner, project, owner.userId) }
        assertFailsWith<ValidationException> { memberships.changeRole(owner, project, owner.userId, MemberRole.REVIEWER) }
        assertFailsWith<AccessDeniedException> { memberships.remove(contributor, project, owner.userId) }

        memberships.changeRole(owner, project, contributor.userId, MemberRole.OWNER)
        memberships.changeRole(owner, project, owner.userId, MemberRole.REVIEWER)
        assertEquals(MemberRole.REVIEWER, access.roleOf(project, owner))
    }

    @Test
    fun `someone else's invitation cannot be accepted`() {
        val owner = users.create("Olive Owens")
        val other = users.create("Oscar Ortiz")
        val project = fixtures.project(owner)
        val invitation = memberships.invite(owner, project, "someone@reqlith.test", MemberRole.REVIEWER)
        assertFailsWith<AccessDeniedException> { memberships.accept(other, invitation.id) }
    }
}
