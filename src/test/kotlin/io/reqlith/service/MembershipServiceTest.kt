package io.reqlith.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.reqlith.exception.ValidationException
import io.reqlith.model.entity.Invitation
import io.reqlith.model.entity.MemberId
import io.reqlith.model.entity.MemberRole
import io.reqlith.model.entity.ProjectMember
import io.reqlith.repository.InvitationRepository
import io.reqlith.repository.ProjectMemberRepository
import io.reqlith.support.UnitFixtures
import io.reqlith.support.UnitFixtures.NOW
import org.junit.jupiter.api.Test
import org.springframework.security.access.AccessDeniedException
import java.util.Optional
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class MembershipServiceTest {
    private val members = mockk<ProjectMemberRepository>(relaxUnitFun = true) { every { save(any()) } answers { firstArg() } }
    private val invitations = mockk<InvitationRepository>(relaxUnitFun = true) { every { save(any()) } answers { firstArg() } }
    private val access = mockk<ProjectAccess>(relaxUnitFun = true)
    private val service = MembershipService(members, invitations, access, UnitFixtures.clock)

    private val owner = UnitFixtures.user("Olive Owens")
    private val project = UnitFixtures.project(owner)
    private fun member(user: UnitFixtures.TestUser, role: MemberRole) = ProjectMember(MemberId(project.id, user.userId), role, NOW)

    @Test
    fun `invitations are normalized and renewed instead of duplicated`() {
        every { invitations.findByProjectIdAndEmail(project.id, "carla@reqlith.test") } returns null
        val first = service.invite(owner, project, "  Carla@Reqlith.TEST ", MemberRole.CONTRIBUTOR)
        assertEquals("carla@reqlith.test", first.email)

        every { invitations.findByProjectIdAndEmail(project.id, "carla@reqlith.test") } returns first
        val renewed = service.invite(owner, project, "carla@reqlith.test", MemberRole.REVIEWER)
        assertEquals(first.id, renewed.id)
        assertEquals(MemberRole.REVIEWER, renewed.role)
        assertFailsWith<ValidationException> { service.invite(owner, project, "not-an-address", MemberRole.REVIEWER) }
        verify { access.requireOwner(project, owner) }
    }

    @Test
    fun `accepting joins with the invited role but never demotes an owner`() {
        val carla = UnitFixtures.user("Carla Conti")
        val invitation = Invitation(project.id, carla.emailAddress!!, MemberRole.REVIEWER, owner.userId, NOW)
        every { invitations.findById(invitation.id) } returns Optional.of(invitation)

        every { members.findById(MemberId(project.id, carla.userId)) } returns Optional.empty()
        assertEquals(project.id, service.accept(carla, invitation.id))
        verify { members.save(match { it.role == MemberRole.REVIEWER && it.userId == carla.userId }) }

        val existingOwner = member(carla, MemberRole.OWNER)
        every { members.findById(MemberId(project.id, carla.userId)) } returns Optional.of(existingOwner)
        service.accept(carla, invitation.id)
        assertEquals(MemberRole.OWNER, existingOwner.role)
    }

    @Test
    fun `nobody accepts or declines someone else's invitation`() {
        val invitation = Invitation(project.id, "someone@reqlith.test", MemberRole.REVIEWER, owner.userId, NOW)
        every { invitations.findById(invitation.id) } returns Optional.of(invitation)
        val other = UnitFixtures.user("Oscar Ortiz")
        assertFailsWith<AccessDeniedException> { service.accept(other, invitation.id) }
        assertFailsWith<AccessDeniedException> { service.decline(other, invitation.id) }
    }

    @Test
    fun `the last owner can be neither removed nor demoted`() {
        val sole = member(owner, MemberRole.OWNER)
        every { members.findById(sole.id) } returns Optional.of(sole)
        every { members.findByIdProjectId(project.id) } returns listOf(sole)

        assertFailsWith<ValidationException> { service.remove(owner, project, owner.userId) }
        assertFailsWith<ValidationException> { service.changeRole(owner, project, owner.userId, MemberRole.REVIEWER) }

        val second = member(UnitFixtures.user("Oscar Ortiz"), MemberRole.OWNER)
        every { members.findByIdProjectId(project.id) } returns listOf(sole, second)
        service.changeRole(owner, project, owner.userId, MemberRole.CONTRIBUTOR)
        assertEquals(MemberRole.CONTRIBUTOR, sole.role)
    }

    @Test
    fun `pending invitations are found by the signed-in user's address`() {
        val carla = UnitFixtures.user("Carla Conti")
        every { invitations.findByEmail(carla.emailAddress!!) } returns emptyList()
        assertEquals(emptyList(), service.pendingInvitationsFor(carla))
        assertEquals(emptyList(), service.pendingInvitationsFor(carla.copy(emailAddress = null)))
    }
}
