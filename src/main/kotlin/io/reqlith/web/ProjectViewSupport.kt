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
import io.reqlith.user.CurrentUser
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
        model.addAttribute("stateLabel", stateLabel(project))
        model.addAttribute("stateClass", if (project.status == ProjectStatus.IN_REVIEW) "review" else "draft")
    }

    fun absolute(path: String): String = formats.absolute(path)

    /** Renders markdown with element and role keys linked into the project's read view. */
    fun render(markdownText: String?, project: Project, document: ProjectDocument): String =
        markdown.render(markdownText, resolver(project, document))

    fun resolver(project: Project, document: ProjectDocument) = ReferenceResolver { key ->
        document.element(key)?.let { Reference("/p/${project.key}/e/$key", "$key · ${it.title}") }
            ?: document.role(key)?.let { Reference("/roles/$key", "$key · ${it.name}") }
    }

    fun stateLabel(project: Project): String = when (project.status) {
        ProjectStatus.IN_REVIEW -> "In review"
        ProjectStatus.DRAFT -> project.latestApprovedVersion?.let { "Draft after v$it" } ?: "Draft"
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
