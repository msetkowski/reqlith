package io.reqlith.support

import io.reqlith.model.dto.CurrentUser
import io.reqlith.model.dto.ProjectDetails
import io.reqlith.model.entity.MemberId
import io.reqlith.model.entity.MemberRole
import io.reqlith.model.entity.Project
import io.reqlith.model.entity.ProjectMember
import io.reqlith.repository.ProjectMemberRepository
import io.reqlith.repository.WorkspaceRepository
import io.reqlith.service.ProjectService
import java.time.Instant
import org.springframework.stereotype.Component

/** Shortcuts for setting up projects in integration tests. */
@Component
class Fixtures(
    private val projects: ProjectService,
    private val workspaces: WorkspaceRepository,
    private val members: ProjectMemberRepository,
) {
    fun project(owner: CurrentUser, title: String = "Self-service refunds"): Project {
        val general = workspaces.findAll().first { it.key == "GEN" }
        return projects.create(owner, general.id, ProjectDetails(title, "Summary", setOf("refunds"), setOf("Payments")))
    }

    fun addMember(project: Project, user: CurrentUser, role: MemberRole) {
        members.save(ProjectMember(MemberId(project.id, user.userId), role, Instant.now()))
    }
}
