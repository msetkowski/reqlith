package io.reqlith.controller

import io.reqlith.model.dto.AttentionItem
import io.reqlith.model.dto.CurrentUser
import io.reqlith.model.dto.ProjectListItem
import io.reqlith.model.entity.MemberRole
import io.reqlith.model.entity.Project
import io.reqlith.model.entity.ProjectMember
import io.reqlith.model.entity.ProjectStatus
import io.reqlith.model.entity.VersionStatus
import io.reqlith.repository.ProjectMemberRepository
import io.reqlith.repository.ProjectVersionRepository
import io.reqlith.service.MembershipService
import io.reqlith.service.ProjectService
import io.reqlith.service.ReviewService
import io.reqlith.service.UserService
import io.reqlith.service.WorkspaceService
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import java.time.Instant
import java.util.UUID

@Controller
class HomeController(
    private val projects: ProjectService,
    private val workspaces: WorkspaceService,
    private val memberships: MembershipService,
    private val formats: Formats,
    private val members: ProjectMemberRepository,
    private val users: UserService,
    private val reviews: ReviewService,
    private val versions: ProjectVersionRepository,
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
        val membersByProject = members.findAll().groupBy { it.projectId }
        val names = users.byIds(membersByProject.values.flatten().map { it.userId })
        val approvedAt = versions.findByStatus(VersionStatus.APPROVED).associate { (it.projectId to it.versionNo) to it.closedAt }
        val rows = all.map { project ->
            val projectMembers = membersByProject[project.id].orEmpty()
            val (state, stateClass) = homeState(project, approvedAt[project.id to project.latestApprovedVersion])
            ProjectListItem(
                project = project,
                owners = projectMembers.filter { it.role == MemberRole.OWNER }.mapNotNull { names[it.userId]?.displayName }.sorted().joinToString(", "),
                myRole = projectMembers.firstOrNull { it.userId == user.userId }?.role,
                state = state,
                stateClass = stateClass,
            )
        }
        val needle = q?.trim()?.lowercase().orEmpty()
        model.addAttribute(
            "rows",
            rows.filter { row ->
                (ws == null || row.project.workspace.id == ws) &&
                    (state.isNullOrEmpty() || row.project.status.name == state) &&
                    (role == null || row.myRole == role) &&
                    (
                        needle.isEmpty() || row.project.title.lowercase().contains(needle) || row.project.key.lowercase().contains(needle) ||
                            row.project.tags.any { it.lowercase().contains(needle) }
                        )
            },
        )
        model.addAttribute("total", rows.size)
        model.addAttribute("workspaces", workspaces.all())
        model.addAttribute("memberRoles", MemberRole.entries)
        model.addAttribute("filter", mapOf("ws" to ws, "state" to state, "role" to role, "q" to q))
        model.addAttribute("attention", attention(user, all, membersByProject))
        return "home"
    }

    /**
     * Cheap approximation of the derived project state for the list: a project edited after its latest
     * approval is a draft of the next version. (Project pages compare content hashes instead.)
     */
    private fun homeState(project: Project, approvedAt: Instant?): Pair<String, String> = when {
        project.status == ProjectStatus.IN_REVIEW -> "In review" to "review"
        project.latestApprovedVersion == null -> "Draft" to "draft"
        approvedAt != null && project.updatedAt.isAfter(approvedAt) -> "Draft after v${project.latestApprovedVersion}" to "draft"
        else -> "Approved" to "approved"
    }

    private fun attention(user: CurrentUser, all: List<Project>, membersByProject: Map<UUID, List<ProjectMember>>): List<AttentionItem> {
        val byId = all.associateBy { it.id }
        val reviewItems = reviews.pendingFor(user).mapNotNull { version ->
            val project = byId[version.projectId] ?: return@mapNotNull null
            val submitter = users.byIds(listOf(version.submittedBy))[version.submittedBy]
            AttentionItem(
                badge = "Review requested",
                badgeClass = "review",
                title = "${project.title} · ${version.label}",
                detail = "Requested by ${submitter?.displayName ?: "an owner"} on ${formats.date(version.submittedAt)}",
                href = "/p/${project.key}/review",
                invitationId = null,
            )
        }
        val editable = all.filter { p -> membersByProject[p.id].orEmpty().any { it.userId == user.userId && it.role != MemberRole.REVIEWER } }
        val feedbackItems = editable.filter { it.status == ProjectStatus.DRAFT }.mapNotNull { project ->
            val open = reviews.openSuggestions(project)
            val lastRound = versions.findByProjectIdOrderBySubmittedAtDesc(project.id).firstOrNull()
            val changesRequested = lastRound?.status in setOf(VersionStatus.CHANGES_REQUESTED, VersionStatus.REJECTED)
            if (open.isEmpty() && !changesRequested) return@mapNotNull null
            val authors = users.byIds(open.map { it.authorId }).values.map { it.displayName }.sorted()
            AttentionItem(
                badge = if (lastRound?.status == VersionStatus.REJECTED) "Rejected" else "Changes requested",
                badgeClass = "danger",
                title = "${project.title}${lastRound?.let { " · ${it.label}" }.orEmpty()}",
                detail = if (open.isEmpty()) "Reviewers asked for changes" else "${open.size} suggestion(s) from ${authors.joinToString(", ")}",
                href = "/p/${project.key}/review",
                invitationId = null,
            )
        }
        val invitations = memberships.pendingInvitationsFor(user)
        val inviters = users.byIds(invitations.map { it.invitedBy })
        return reviewItems + feedbackItems + invitations.mapNotNull { invitation ->
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
