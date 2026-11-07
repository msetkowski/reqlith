package io.reqlith.service

import io.reqlith.model.dto.CurrentUser
import io.reqlith.model.entity.MemberId
import io.reqlith.model.entity.MemberRole
import io.reqlith.model.entity.Project
import io.reqlith.repository.ProjectMemberRepository
import java.util.UUID
import org.springframework.security.access.AccessDeniedException
import org.springframework.stereotype.Component

/**
 * Project-level authorization. Every authenticated user can read every project; changing it
 * depends on the membership role.
 */
@Component
class ProjectAccess(private val members: ProjectMemberRepository) {

    fun roleOf(project: Project, user: CurrentUser): MemberRole? = roleOf(project.id, user.userId)

    fun roleOf(projectId: UUID, userId: UUID): MemberRole? =
        members.findById(MemberId(projectId, userId)).orElse(null)?.role

    fun canEdit(project: Project, user: CurrentUser): Boolean =
        roleOf(project, user) in setOf(MemberRole.OWNER, MemberRole.CONTRIBUTOR)

    fun isOwner(project: Project, user: CurrentUser): Boolean = roleOf(project, user) == MemberRole.OWNER

    fun requireEditor(project: Project, user: CurrentUser) {
        if (!canEdit(project, user)) throw AccessDeniedException("Only owners and contributors can change ${project.key}")
    }

    fun requireOwner(project: Project, user: CurrentUser) {
        if (!isOwner(project, user)) throw AccessDeniedException("Only owners can do this in ${project.key}")
    }
}
