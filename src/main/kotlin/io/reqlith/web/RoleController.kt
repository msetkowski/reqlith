package io.reqlith.web

import io.reqlith.common.ValidationException
import io.reqlith.document.ProjectDocumentService
import io.reqlith.project.Project
import io.reqlith.project.ProjectService
import io.reqlith.role.RoleService
import io.reqlith.user.CurrentUser
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import java.util.UUID

@Controller
class RoleController(
    private val projects: ProjectService,
    private val roles: RoleService,
    private val documents: ProjectDocumentService,
    private val support: ProjectViewSupport,
    private val markdown: io.reqlith.markdown.MarkdownRenderer,
) {
    /** Roles of one project: add new / select existing, and describe what each does here. */
    @GetMapping("/p/{key}/roles")
    fun projectRoles(@AuthenticationPrincipal user: CurrentUser, @PathVariable key: String, model: Model): String {
        populate(model, projects.get(key), user)
        return "project/roles"
    }

    @PostMapping("/p/{key}/roles")
    fun add(
        @AuthenticationPrincipal user: CurrentUser,
        @PathVariable key: String,
        @RequestParam(required = false) roleId: UUID?,
        @RequestParam(required = false) name: String?,
        @RequestParam(required = false) description: String?,
        @RequestParam(required = false) context: String?,
        model: Model,
    ): String {
        val project = projects.get(key)
        return try {
            if (roleId != null) {
                roles.addExisting(user, project, roleId, context)
            } else {
                roles.createAndAdd(user, project, name.orEmpty(), description, context)
            }
            "redirect:/p/$key/roles"
        } catch (e: ValidationException) {
            populate(model, project, user)
            model.addAttribute("error", e)
            model.addAttribute("newName", name)
            model.addAttribute("newDescription", description)
            model.addAttribute("newContext", context)
            "project/roles"
        }
    }

    @PostMapping("/p/{key}/roles/{roleId}")
    fun updateContext(
        @AuthenticationPrincipal user: CurrentUser,
        @PathVariable key: String,
        @PathVariable roleId: UUID,
        @RequestParam(required = false) context: String?,
    ): String {
        roles.updateContext(user, projects.get(key), roleId, context)
        return "redirect:/p/$key/roles"
    }

    @PostMapping("/p/{key}/roles/{roleId}/remove")
    fun remove(@AuthenticationPrincipal user: CurrentUser, @PathVariable key: String, @PathVariable roleId: UUID): String {
        roles.removeFromProject(user, projects.get(key), roleId)
        return "redirect:/p/$key/roles"
    }

    /** All roles across projects. */
    @GetMapping("/roles")
    fun all(model: Model): String {
        model.addAttribute("roles", roles.all())
        model.addAttribute("usage", roles.usage())
        return "role/list"
    }

    @GetMapping("/roles/{roleKey}")
    fun show(@AuthenticationPrincipal user: CurrentUser, @PathVariable roleKey: String, model: Model): String {
        val role = roles.get(roleKey)
        val usedIn = roles.projectIdsUsing(role).toSet()
        model.addAttribute("role", role)
        model.addAttribute("descriptionHtml", markdown.render(role.descriptionMd))
        model.addAttribute("projects", projects.all().filter { it.id in usedIn })
        model.addAttribute("canEdit", roles.canEditGlobal(user, role))
        return "role/show"
    }

    @PostMapping("/roles/{roleKey}")
    fun updateGlobal(
        @AuthenticationPrincipal user: CurrentUser,
        @PathVariable roleKey: String,
        @RequestParam name: String,
        @RequestParam(required = false) description: String?,
    ): String {
        roles.updateGlobal(user, roles.get(roleKey), name, description)
        return "redirect:/roles/$roleKey"
    }

    private fun populate(model: Model, project: Project, user: CurrentUser) {
        val document = documents.fromWorkingCopy(project)
        support.populate(model, project, user, document, active = "roles")
        val projectRoles = roles.forProject(project)
        val used = projectRoles.map { it.role.id }.toSet()
        model.addAttribute("projectRoles", projectRoles)
        model.addAttribute("rendered", projectRoles.associate { it.role.id to (markdown.render(it.role.descriptionMd) to support.render(it.contextMd, project, document)) })
        model.addAttribute("availableRoles", roles.all().filter { it.id !in used })
    }
}
