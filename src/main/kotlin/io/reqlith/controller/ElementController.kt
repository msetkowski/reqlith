package io.reqlith.controller

import io.reqlith.exception.ValidationException
import io.reqlith.model.dto.CurrentUser
import io.reqlith.model.dto.ElementForm
import io.reqlith.model.dto.ProjectDocument
import io.reqlith.model.entity.Element
import io.reqlith.model.entity.ElementType
import io.reqlith.model.entity.LinkKind
import io.reqlith.model.entity.LinkSystem
import io.reqlith.model.entity.Priority
import io.reqlith.model.entity.Project
import io.reqlith.service.ElementService
import io.reqlith.service.ExternalLinkService
import io.reqlith.service.MembershipService
import io.reqlith.service.ProjectDocumentService
import io.reqlith.service.ProjectService
import io.reqlith.service.ReviewService
import io.reqlith.service.UserService
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseBody

@Controller
class ElementController(
    private val projects: ProjectService,
    private val elements: ElementService,
    private val documents: ProjectDocumentService,
    private val memberships: MembershipService,
    private val users: UserService,
    private val reviews: ReviewService,
    private val links: ExternalLinkService,
    private val support: ProjectViewSupport,
) {
    /** One element in read mode (working copy). */
    @GetMapping("/p/{key}/e/{elementKey}")
    fun show(@AuthenticationPrincipal user: CurrentUser, @PathVariable key: String, @PathVariable elementKey: String, model: Model): String {
        val project = projects.get(key)
        val document = documents.fromWorkingCopy(project)
        val element = elements.get(project, elementKey)
        support.populate(model, project, user, document, active = element.type.name)
        model.addAttribute("element", element)
        model.addAttribute("elementUrl", support.absolute("/p/${project.key}/e/${element.key}"))
        model.addAttribute("bodyHtml", support.render(element.bodyMd, project, document))
        model.addAttribute("incoming", document.incomingLinks()[element.key].orEmpty())
        model.addAttribute("document", document)
        model.addAttribute("updatedBy", users.byIds(listOf(element.updatedBy))[element.updatedBy])
        model.addAttribute("externalLinks", links.forElement(project, element.key))
        model.addAttribute("systems", LinkSystem.entries)
        return "element/show"
    }

    @GetMapping("/p/{key}/elements/new")
    fun newElement(@AuthenticationPrincipal user: CurrentUser, @PathVariable key: String, @RequestParam type: ElementType, model: Model): String {
        val project = projects.get(key)
        project.requireEditable()
        val form = ElementForm(type = type, bodyMd = TEMPLATES[type])
        return editor(model, project, user, form, element = null)
    }

    @PostMapping("/p/{key}/elements")
    fun create(@AuthenticationPrincipal user: CurrentUser, @PathVariable key: String, form: ElementForm, model: Model): String {
        val project = projects.get(key)
        return try {
            val element = elements.create(user, project, form.type, form.content())
            "redirect:/p/$key/e/${element.key}"
        } catch (e: ValidationException) {
            model.addAttribute("error", e)
            editor(model, project, user, form, element = null)
        }
    }

    @GetMapping("/p/{key}/e/{elementKey}/edit")
    fun edit(@AuthenticationPrincipal user: CurrentUser, @PathVariable key: String, @PathVariable elementKey: String, model: Model): String {
        val project = projects.get(key)
        project.requireEditable()
        val element = elements.get(project, elementKey)
        return editor(model, project, user, ElementForm.of(element), element)
    }

    @PostMapping("/p/{key}/e/{elementKey}")
    fun update(
        @AuthenticationPrincipal user: CurrentUser,
        @PathVariable key: String,
        @PathVariable elementKey: String,
        form: ElementForm,
        model: Model,
    ): String {
        val project = projects.get(key)
        return try {
            elements.update(user, project, elementKey, form.content(), form.rowVersion)
            "redirect:/p/$key/e/$elementKey"
        } catch (e: ValidationException) {
            model.addAttribute("error", e)
            editor(model, project, user, form, elements.get(project, elementKey))
        }
    }

    @PostMapping("/p/{key}/e/{elementKey}/delete")
    fun delete(@AuthenticationPrincipal user: CurrentUser, @PathVariable key: String, @PathVariable elementKey: String): String {
        val project = projects.get(key)
        val type = elements.get(project, elementKey).type
        elements.delete(user, project, elementKey)
        return "redirect:/p/$key/draft?type=${type.name}"
    }

    /** Live preview for the markdown editor (HTMX). Uses the same renderer as every read view. */
    @PostMapping("/p/{key}/preview")
    @ResponseBody
    fun preview(
        @PathVariable key: String,
        @RequestParam(required = false) markdown: String?,
        @RequestParam(required = false) bodyMd: String?,
    ): String {
        val project = projects.get(key)
        val html = support.render(markdown ?: bodyMd, project, documents.fromWorkingCopy(project))
        return html.ifEmpty { """<p class="muted">Nothing to preview yet.</p>""" }
    }

    private fun editor(model: Model, project: Project, user: CurrentUser, form: ElementForm, element: Element?): String {
        val document = documents.fromWorkingCopy(project)
        support.populate(model, project, user, document, active = form.type.name)
        model.addAttribute("form", form)
        model.addAttribute("element", element)
        model.addAttribute("previewHtml", support.render(form.bodyMd, project, document))
        model.addAttribute("priorities", Priority.entries)
        model.addAttribute("linkKinds", LinkKind.entries)
        model.addAttribute("linkTargets", linkTargets(document, element?.key))
        val suggestions = element?.let { e -> reviews.openSuggestions(project).filter { it.elementKey == e.key } }.orEmpty()
        val members = memberships.members(project)
        val names = users.byIds(members.map { it.userId } + suggestions.map { it.authorId })
        model.addAttribute("people", members.mapNotNull { m -> names[m.userId]?.let { it to m.role } })
        model.addAttribute("suggestions", suggestions)
        model.addAttribute("suggestionHtml", suggestions.associate { it.id to support.render(it.proposedMd, project, document) })
        model.addAttribute("names", names)
        return "element/edit"
    }

    /** Everything an element can link to, as (key, label). */
    private fun linkTargets(document: ProjectDocument, ownKey: String?): List<Pair<String, String>> =
        document.elements.filter { it.key != ownKey }.map { it.key to "${it.key} · ${it.title}" } +
            document.roles.map { it.key to "${it.key} · ${it.name}" }

    companion object {
        private val TEMPLATES = mapOf(
            ElementType.FR to "## Description\n\n\n## Acceptance criteria\n\n- \n",
            ElementType.NFR to "## Description\n\n\n## Measurement\n\n- \n",
            ElementType.SC to "## Context\n\n\n## Steps\n\n1. \n\n## Expected outcome\n\n",
            ElementType.RK to "## Description\n\n\n## Impact and likelihood\n\n\n## Mitigation\n\n",
        )
    }
}
