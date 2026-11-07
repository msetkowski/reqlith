package io.reqlith.web

import io.reqlith.document.ProjectDocument
import io.reqlith.element.ElementType
import io.reqlith.markdown.MarkdownRenderer
import io.reqlith.markdown.Reference
import io.reqlith.markdown.ReferenceResolver
import io.reqlith.project.MemberRole
import io.reqlith.project.MembershipService
import io.reqlith.project.Project
import io.reqlith.project.ProjectAccess
import io.reqlith.project.ProjectStatus
import io.reqlith.review.ReviewService
import io.reqlith.user.CurrentUser
import io.reqlith.version.SnapshotCodec
import io.reqlith.version.VersionService
import io.reqlith.user.UserService
import org.springframework.stereotype.Component
import org.springframework.ui.Model

/** One entry of the project side navigation. */
data class NavSection(val label: String, val count: Int?, val href: String, val id: String)

/** Model attributes shared by every project page: header, permissions and side navigation. */
@Component
class ProjectViewSupport(
    private val access: ProjectAccess,
    private val memberships: MembershipService,
    private val users: UserService,
    private val markdown: MarkdownRenderer,
    private val formats: Formats,
    private val versions: VersionService,
    private val reviews: ReviewService,
    private val codec: SnapshotCodec,
) {
    fun populate(model: Model, project: Project, user: CurrentUser, document: ProjectDocument, active: String) {
        val role = access.roleOf(project, user)
        val members = memberships.members(project)
        val names = users.byIds(members.map { it.userId })
        val owners = members.filter { it.role == MemberRole.OWNER }.mapNotNull { names[it.userId] }
        model.addAttribute("project", project)
        model.addAttribute("projectUrl", formats.absolute("/p/${project.key}"))
        model.addAttribute("myRole", role)
        model.addAttribute("canEdit", role in setOf(MemberRole.OWNER, MemberRole.CONTRIBUTOR) && project.status == ProjectStatus.DRAFT)
        model.addAttribute("isOwner", role == MemberRole.OWNER)
        model.addAttribute("locked", project.status == ProjectStatus.IN_REVIEW)
        model.addAttribute("owners", owners)
        model.addAttribute("active", active)
        model.addAttribute("sections", sections(project, document))
        val state = state(project, document)
        model.addAttribute("stateLabel", state.first)
        model.addAttribute("stateClass", state.second)
        model.addAttribute("openReview", versions.openCandidate(project))
        model.addAttribute("openSuggestionCount", reviews.openSuggestions(project).size)
    }

    /**
     * Derived, never stored: "In review · v2-rc1", "Approved v1", "Draft after v1" or "Draft",
     * with the badge style to use.
     */
    fun state(project: Project, document: ProjectDocument): Pair<String, String> {
        if (project.status == ProjectStatus.IN_REVIEW) {
            return "In review · ${versions.openCandidate(project)?.label.orEmpty()}" to "review"
        }
        val approved = versions.latestApproved(project) ?: return "Draft" to "draft"
        return if (codec.write(document).hash == approved.contentHash) {
            "Approved ${approved.label}" to "approved"
        } else {
            "Draft after ${approved.label}" to "draft"
        }
    }

    fun absolute(path: String): String = formats.absolute(path)

    /** Renders markdown with element and role keys linked into the project's read view. */
    fun render(markdownText: String?, project: Project, document: ProjectDocument): String =
        markdown.render(markdownText, resolver(project, document))

    /** Renders markdown of a frozen version: references point to anchors on the same page. */
    fun renderInPage(markdownText: String?, document: ProjectDocument): String =
        markdown.render(markdownText) { key ->
            document.element(key)?.let { Reference("#$key", "$key · ${it.title}") }
                ?: document.role(key)?.let { Reference("#$key", "$key · ${it.name}") }
        }

    fun resolver(project: Project, document: ProjectDocument) = ReferenceResolver { key ->
        document.element(key)?.let { Reference("/p/${project.key}/e/$key", "$key · ${it.title}") }
            ?: document.role(key)?.let { Reference("/roles/$key", "$key · ${it.name}") }
    }

    private fun sections(project: Project, document: ProjectDocument): List<NavSection> {
        val base = "/p/${project.key}"
        val basic = listOf(
            NavSection("Problem statement", null, "$base/draft/document#problem-statement", "problem"),
            NavSection("Scope & out of scope", null, "$base/draft/document#scope", "scope"),
            NavSection("Success metrics", null, "$base/draft/document#success-metrics", "metrics"),
        )
        fun typed(type: ElementType) = NavSection(type.section, document.elementsOf(type).size, "$base/draft?type=${type.name}", type.name)
        return basic + listOf(
            typed(ElementType.BR), typed(ElementType.SR), typed(ElementType.FR), typed(ElementType.NFR),
            typed(ElementType.AS), typed(ElementType.RK),
            NavSection("Roles", document.roles.size, "$base/roles", "roles"),
            typed(ElementType.SC), typed(ElementType.DP),
        )
    }
}
