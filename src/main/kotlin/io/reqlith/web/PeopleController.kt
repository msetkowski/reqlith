package io.reqlith.web

import io.reqlith.common.ValidationException
import io.reqlith.document.ProjectDocumentService
import io.reqlith.project.MemberRole
import io.reqlith.project.MembershipService
import io.reqlith.project.Project
import io.reqlith.project.ProjectService
import io.reqlith.user.CurrentUser
import io.reqlith.user.UserService
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.servlet.mvc.support.RedirectAttributes
import java.util.UUID

@Controller
class PeopleController(
    private val projects: ProjectService,
    private val memberships: MembershipService,
    private val documents: ProjectDocumentService,
    private val users: UserService,
    private val support: ProjectViewSupport,
) {
    @GetMapping("/p/{key}/people")
    fun people(@AuthenticationPrincipal user: CurrentUser, @PathVariable key: String, model: Model): String {
        val project = projects.get(key)
        populate(model, project, user)
        return "project/people"
    }

    @PostMapping("/p/{key}/people/invite")
    fun invite(
        @AuthenticationPrincipal user: CurrentUser,
        @PathVariable key: String,
        @RequestParam email: String,
        @RequestParam role: MemberRole,
        model: Model,
    ): String {
        val project = projects.get(key)
        return try {
            memberships.invite(user, project, email, role)
            "redirect:/p/$key/people"
        } catch (e: ValidationException) {
            populate(model, project, user)
            model.addAttribute("error", e)
            model.addAttribute("inviteEmail", email)
            "project/people"
        }
    }

    @PostMapping("/p/{key}/people/{userId}/role")
    fun changeRole(
        @AuthenticationPrincipal user: CurrentUser,
        @PathVariable key: String,
        @PathVariable userId: UUID,
        @RequestParam role: MemberRole,
        redirect: RedirectAttributes,
    ): String {
        guarded(redirect) { memberships.changeRole(user, projects.get(key), userId, role) }
        return "redirect:/p/$key/people"
    }

    @PostMapping("/p/{key}/people/{userId}/remove")
    fun remove(@AuthenticationPrincipal user: CurrentUser, @PathVariable key: String, @PathVariable userId: UUID, redirect: RedirectAttributes): String {
        guarded(redirect) { memberships.remove(user, projects.get(key), userId) }
        return "redirect:/p/$key/people"
    }

    @PostMapping("/p/{key}/invitations/{invitationId}/cancel")
    fun cancelInvitation(@AuthenticationPrincipal user: CurrentUser, @PathVariable key: String, @PathVariable invitationId: UUID): String {
        memberships.cancelInvitation(user, projects.get(key), invitationId)
        return "redirect:/p/$key/people"
    }

    @PostMapping("/invitations/{invitationId}/accept")
    fun accept(@AuthenticationPrincipal user: CurrentUser, @PathVariable invitationId: UUID): String {
        val projectId = memberships.accept(user, invitationId)
        val project = projects.all().first { it.id == projectId }
        return "redirect:/p/${project.key}/draft"
    }

    @PostMapping("/invitations/{invitationId}/decline")
    fun decline(@AuthenticationPrincipal user: CurrentUser, @PathVariable invitationId: UUID): String {
        memberships.decline(user, invitationId)
        return "redirect:/"
    }

    private fun populate(model: Model, project: Project, user: CurrentUser) {
        support.populate(model, project, user, documents.fromWorkingCopy(project), active = "people")
        val members = memberships.members(project)
        val invitations = memberships.invitations(project)
        val names = users.byIds(members.map { it.userId } + invitations.map { it.invitedBy })
        model.addAttribute("members", members.sortedWith(compareBy({ it.role.ordinal }, { names[it.userId]?.displayName })))
        model.addAttribute("invitations", invitations.sortedBy { it.email })
        model.addAttribute("names", names)
        model.addAttribute("memberRoles", MemberRole.entries)
    }

    private fun guarded(redirect: RedirectAttributes, action: () -> Unit) {
        try {
            action()
        } catch (e: ValidationException) {
            redirect.addFlashAttribute("flashError", e.message)
        }
    }
}
