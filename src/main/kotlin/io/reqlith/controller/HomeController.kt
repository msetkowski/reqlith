package io.reqlith.controller

import io.reqlith.model.dto.AttentionItem
import io.reqlith.model.dto.CurrentUser
import io.reqlith.model.dto.ProjectFilter
import io.reqlith.model.dto.ProjectListItem
import io.reqlith.model.entity.MemberRole
import io.reqlith.model.entity.Project
import io.reqlith.model.entity.ProjectStatus
import io.reqlith.model.entity.VersionStatus
import io.reqlith.repository.ProjectMemberRepository
import io.reqlith.repository.ProjectRepository
import io.reqlith.repository.ProjectVersionRepository
import io.reqlith.service.MembershipService
import io.reqlith.service.ReviewService
import io.reqlith.service.UserService
import io.reqlith.service.WorkspaceService
import org.springframework.data.domain.PageRequest
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import java.time.Instant

@Controller
class HomeController(
    private val projects: ProjectRepository,
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
    fun home(@AuthenticationPrincipal user: CurrentUser, filter: ProjectFilter, model: Model): String {
        rows(user, filter, page = 0, model)
        model.addAttribute("total", projects.count())
        model.addAttribute("workspaces", workspaces.all())
        model.addAttribute("memberRoles", MemberRole.entries)
        model.addAttribute("filter", filter)
        model.addAttribute("attention", attention(user))
        return "home"
    }

    /** The next page of the project table, loaded by infinite scroll with the same filters. */
    @GetMapping("/projects/rows")
    fun moreRows(@AuthenticationPrincipal user: CurrentUser, filter: ProjectFilter, @RequestParam page: Int, model: Model): String {
        rows(user, filter, page.coerceAtLeast(0), model)
        return "home :: rows"
    }

    private fun rows(user: CurrentUser, filter: ProjectFilter, page: Int, model: Model) {
        val slice = projects.search(
            workspaceId = filter.ws,
            status = ProjectStatus.entries.firstOrNull { it.name == filter.state },
            userId = user.userId,
            role = filter.role,
            pattern = filter.q?.trim()?.lowercase()?.ifEmpty { null }?.let { "%" + it.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%" },
            pageable = PageRequest.of(page, PAGE_SIZE),
        )
        val ids = slice.content.map { it.id }
        val membersByProject = members.findByIdProjectIdIn(ids).groupBy { it.projectId }
        val names = users.byIds(membersByProject.values.flatten().filter { it.role == MemberRole.OWNER }.map { it.userId })
        val approvedAt = versions.findByProjectIdInAndStatus(ids, VersionStatus.APPROVED).associate { (it.projectId to it.versionNo) to it.closedAt }
        model.addAttribute(
            "rows",
            slice.content.map { project ->
                val projectMembers = membersByProject[project.id].orEmpty()
                val (state, stateClass) = homeState(project, approvedAt[project.id to project.latestApprovedVersion])
                ProjectListItem(
                    project = project,
                    owners = projectMembers.filter { it.role == MemberRole.OWNER }.mapNotNull { names[it.userId]?.displayName }.sorted().joinToString(", "),
                    myRole = projectMembers.firstOrNull { it.userId == user.userId }?.role,
                    state = state,
                    stateClass = stateClass,
                )
            },
        )
        model.addAttribute("nextPage", if (slice.hasNext()) page + 1 else null)
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

    private fun attention(user: CurrentUser): List<AttentionItem> {
        val pending = reviews.pendingFor(user)
        val editableIds = members.findByIdUserId(user.userId).filter { it.role != MemberRole.REVIEWER }.map { it.projectId }
        val invitations = memberships.pendingInvitationsFor(user)
        val byId = projects.findAllById((pending.map { it.projectId } + editableIds + invitations.map { it.projectId }).toSet()).associateBy { it.id }
        val reviewItems = pending.mapNotNull { version ->
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
        val editable = editableIds.mapNotNull { byId[it] }.sortedByDescending { it.updatedAt }
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

    companion object {
        const val PAGE_SIZE = 20
    }
}
