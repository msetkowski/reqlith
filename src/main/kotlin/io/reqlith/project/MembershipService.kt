package io.reqlith.project

import io.reqlith.common.NotFoundException
import io.reqlith.common.ValidationException
import io.reqlith.user.CurrentUser
import org.springframework.security.access.AccessDeniedException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant
import java.util.UUID

/** Owners, contributors and reviewers of a project, and invitations by e-mail. */
@Service
class MembershipService(
    private val members: ProjectMemberRepository,
    private val invitations: InvitationRepository,
    private val access: ProjectAccess,
    private val clock: Clock,
) {
    fun members(project: Project): List<ProjectMember> = members.findByIdProjectId(project.id)

    fun invitations(project: Project): List<Invitation> = invitations.findByProjectId(project.id)

    fun pendingInvitationsFor(user: CurrentUser): List<Invitation> =
        user.emailAddress?.let { invitations.findByEmailIgnoreCase(it) }.orEmpty()

    @Transactional
    fun invite(user: CurrentUser, project: Project, email: String, role: MemberRole): Invitation {
        access.requireOwner(project, user)
        val normalized = email.trim().lowercase()
        if (!EMAIL.matches(normalized)) throw ValidationException("email", "Enter a valid e-mail address")
        val now = Instant.now(clock)
        val existing = invitations.findByProjectIdAndEmailIgnoreCase(project.id, normalized)
        if (existing != null) {
            existing.role = role
            existing.invitedBy = user.userId
            existing.invitedAt = now
            return invitations.save(existing)
        }
        return invitations.save(Invitation(project.id, normalized, role, user.userId, now))
    }

    @Transactional
    fun cancelInvitation(user: CurrentUser, project: Project, invitationId: UUID) {
        access.requireOwner(project, user)
        val invitation = invitations.findById(invitationId).orElseThrow { NotFoundException("Invitation not found") }
        require(invitation.projectId == project.id)
        invitations.delete(invitation)
    }

    /** The invitee joins the project with the invited role (an existing membership is updated). */
    @Transactional
    fun accept(user: CurrentUser, invitationId: UUID): UUID {
        val invitation = ownInvitation(user, invitationId)
        val id = MemberId(invitation.projectId, user.userId)
        val member = members.findById(id).orElse(null)
        if (member == null) {
            members.save(ProjectMember(id, invitation.role, Instant.now(clock)))
        } else if (member.role != MemberRole.OWNER) {
            member.role = invitation.role
        }
        invitations.delete(invitation)
        return invitation.projectId
    }

    @Transactional
    fun decline(user: CurrentUser, invitationId: UUID) {
        invitations.delete(ownInvitation(user, invitationId))
    }

    @Transactional
    fun changeRole(user: CurrentUser, project: Project, memberUserId: UUID, role: MemberRole) {
        access.requireOwner(project, user)
        val member = members.findById(MemberId(project.id, memberUserId)).orElseThrow { NotFoundException("Member not found") }
        if (member.role == MemberRole.OWNER && role != MemberRole.OWNER) requireAnotherOwner(project)
        member.role = role
    }

    @Transactional
    fun remove(user: CurrentUser, project: Project, memberUserId: UUID) {
        access.requireOwner(project, user)
        val member = members.findById(MemberId(project.id, memberUserId)).orElseThrow { NotFoundException("Member not found") }
        if (member.role == MemberRole.OWNER) requireAnotherOwner(project)
        members.delete(member)
    }

    private fun requireAnotherOwner(project: Project) {
        if (members.findByIdProjectId(project.id).count { it.role == MemberRole.OWNER } <= 1) {
            throw ValidationException(null, "A project needs at least one owner. Add another owner first.")
        }
    }

    private fun ownInvitation(user: CurrentUser, invitationId: UUID): Invitation {
        val invitation = invitations.findById(invitationId).orElseThrow { NotFoundException("Invitation not found") }
        if (!invitation.email.equals(user.emailAddress, ignoreCase = true)) {
            throw AccessDeniedException("This invitation is for someone else")
        }
        return invitation
    }

    companion object {
        private val EMAIL = Regex("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")
    }
}
