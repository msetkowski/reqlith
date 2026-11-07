package io.reqlith.support

import io.reqlith.project.MemberId
import io.reqlith.project.MemberRole
import io.reqlith.project.Project
import io.reqlith.project.ProjectDetails
import io.reqlith.project.ProjectMember
import io.reqlith.project.ProjectMemberRepository
import io.reqlith.project.ProjectService
import io.reqlith.user.CurrentUser
import io.reqlith.workspace.WorkspaceRepository
import org.springframework.stereotype.Component
import java.time.Instant

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
