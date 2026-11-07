package io.reqlith.web

import io.reqlith.project.MemberRole
import io.reqlith.project.MembershipService
import io.reqlith.project.Project
import io.reqlith.project.ProjectMemberRepository
import io.reqlith.project.ProjectService
import io.reqlith.project.ProjectStatus
import io.reqlith.user.CurrentUser
import io.reqlith.user.UserService
import io.reqlith.workspace.WorkspaceService
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import java.util.UUID

/** A row of the "All projects" table. */
data class ProjectRow(
    val project: Project,
    val owners: String,
    val myRole: MemberRole?,
    val state: String,
    val stateClass: String,
)

/** A card in "Needs your attention". */
data class AttentionItem(val badge: String, val badgeClass: String, val title: String, val detail: String, val href: String?, val invitationId: UUID?)

@Controller
class HomeController(
    private val projects: ProjectService,
    private val workspaces: WorkspaceService,
    private val memberships: MembershipService,
    private val members: ProjectMemberRepository,
    private val users: UserService,
) {
    /** Every signed-in user can read every project; filters narrow the table (HTMX swaps just the table). */
    @GetMapping("/")
    fun home(
        @AuthenticationPrincipal user: CurrentUser,
        @RequestParam(required = false) ws: UUID?,
        @RequestParam(required = false) state: String?,
        @RequestParam(required = false) role: MemberRole?,
        @RequestParam(required = false) q: String?,
        model: Model,
    ): String {
        val all = projects.all()
        val memberships = members.findAll().groupBy { it.projectId }
        val names = users.byIds(memberships.values.flatten().map { it.userId })
        val rows = all.map { project ->
            val projectMembers = memberships[project.id].orEmpty()
            ProjectRow(
                project = project,
                owners = projectMembers.filter { it.role == MemberRole.OWNER }.mapNotNull { names[it.userId]?.displayName }.sorted().joinToString(", "),
                myRole = projectMembers.firstOrNull { it.userId == user.userId }?.role,
                state = if (project.status == ProjectStatus.IN_REVIEW) "In review" else "Draft",
                stateClass = if (project.status == ProjectStatus.IN_REVIEW) "review" else "draft",
            )
        }
        val needle = q?.trim()?.lowercase().orEmpty()
        model.addAttribute(
            "rows",
            rows.filter { row ->
                (ws == null || row.project.workspace.id == ws) &&
                    (state.isNullOrEmpty() || row.project.status.name == state) &&
                    (role == null || row.myRole == role) &&
                    (needle.isEmpty() || row.project.title.lowercase().contains(needle) || row.project.key.lowercase().contains(needle) ||
                        row.project.tags.any { it.lowercase().contains(needle) })
            },
        )
        model.addAttribute("total", rows.size)
        model.addAttribute("workspaces", workspaces.all())
        model.addAttribute("memberRoles", MemberRole.entries)
        model.addAttribute("filter", mapOf("ws" to ws, "state" to state, "role" to role, "q" to q))
        model.addAttribute("attention", attention(user, all))
        return "home"
    }

    private fun attention(user: CurrentUser, all: List<Project>): List<AttentionItem> {
        val byId = all.associateBy { it.id }
        val invitations = memberships.pendingInvitationsFor(user)
        val inviters = users.byIds(invitations.map { it.invitedBy })
        return invitations.mapNotNull { invitation ->
            val project = byId[invitation.projectId] ?: return@mapNotNull null
            AttentionItem(
                badge = "Invitation",
                badgeClass = "info",
                title = project.title,
                detail = "${inviters[invitation.invitedBy]?.displayName ?: "Someone"} invited you as ${invitation.role.label.lowercase()}",
                href = "/p/${project.key}/draft",
                invitationId = invitation.id,
            )
        }
    }
}
