package io.reqlith.controller

import io.reqlith.exception.ValidationException
import io.reqlith.model.dto.ChangeKind
import io.reqlith.model.dto.CurrentUser
import io.reqlith.model.dto.DetailsForm
import io.reqlith.model.dto.NewProjectForm
import io.reqlith.model.dto.ProjectDocument
import io.reqlith.model.dto.RenderedDocument
import io.reqlith.model.entity.Element
import io.reqlith.model.entity.ElementType
import io.reqlith.model.entity.MemberRole
import io.reqlith.model.entity.Project
import io.reqlith.service.DocumentChanges
import io.reqlith.service.ElementService
import io.reqlith.service.ExternalLinkService
import io.reqlith.service.ProjectDocumentService
import io.reqlith.service.ProjectService
import io.reqlith.service.ReviewService
import io.reqlith.service.UserService
import io.reqlith.service.VersionService
import io.reqlith.service.WorkspaceService
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam

@Controller
class ProjectController(
    private val projects: ProjectService,
    private val workspaces: WorkspaceService,
    private val documents: ProjectDocumentService,
    private val elements: ElementService,
    private val versions: VersionService,
    private val reviews: ReviewService,
    private val users: UserService,
    private val links: ExternalLinkService,
    private val support: ProjectViewSupport,
) {
    @GetMapping("/projects/new")
    fun newProject(model: Model): String {
        model.addAttribute("form", NewProjectForm())
        return newProjectView(model)
    }

    @PostMapping("/projects")
    fun create(@AuthenticationPrincipal user: CurrentUser, form: NewProjectForm, model: Model): String {
        return try {
            val invitations = form.invitations().map { it.first to it.second }
            val project = projects.create(
                user,
                form.workspaceId ?: throw ValidationException("workspaceId", "Choose a workspace"),
                form.details(),
                invitations,
            )
            "redirect:/p/${project.key}/draft"
        } catch (e: ValidationException) {
            model.addAttribute("form", form)
            model.addAttribute("error", e)
            newProjectView(model)
        }
    }

    private fun newProjectView(model: Model): String {
        model.addAttribute("workspaces", workspaces.all())
        model.addAttribute("memberRoles", MemberRole.entries)
        model.addAttribute("elementTypes", ElementType.entries)
        return "project/new"
    }

    @GetMapping("/p/{key}/draft")
    fun overview(
        @AuthenticationPrincipal user: CurrentUser,
        @PathVariable key: String,
        @RequestParam(required = false) type: ElementType?,
        @RequestParam(defaultValue = "false") changed: Boolean,
        model: Model,
    ): String {
        val project = projects.get(key)
        val document = documents.fromWorkingCopy(project)
        support.populate(model, project, user, document, active = type?.name ?: "overview")
        val changes = changesSinceApproval(project, document)
        val history = versions.history(project)
        model.addAttribute("document", document)
        model.addAttribute("types", ElementType.entries)
        model.addAttribute("selectedType", type)
        model.addAttribute("changedOnly", changed && changes != null)
        model.addAttribute("changes", changes)
        model.addAttribute(
            "rows",
            (if (type == null) document.elements else document.elementsOf(type))
                .filter { !changed || changes == null || changes.of(it.key) != ChangeKind.UNCHANGED },
        )
        model.addAttribute("history", history)
        model.addAttribute("submitters", users.byIds(history.map { it.submittedBy }))
        model.addAttribute("openSuggestions", reviews.openSuggestions(project))
        model.addAttribute("externalLinks", links.all(project))
        model.addAttribute("modified", elements.all(project).associate { it.key to it.updatedAt })
        model.addAttribute("brokenLinks", document.brokenLinks())
        model.addAttribute("functionalWithoutScenario", document.functionalWithoutScenario())
        return "project/overview"
    }

    /** The whole working copy rendered as one document (read mode). */
    @GetMapping("/p/{key}/draft/document")
    fun document(@AuthenticationPrincipal user: CurrentUser, @PathVariable key: String, model: Model): String {
        val project = projects.get(key)
        val document = documents.fromWorkingCopy(project)
        support.populate(model, project, user, document, active = "document")
        model.addAttribute("document", document)
        model.addAttribute("changes", changesSinceApproval(project, document))
        model.addAttribute("html", RenderedDocument.of(document) { support.render(it, project, document) })
        model.addAttribute("types", ElementType.entries)
        model.addAttribute("incoming", document.incomingLinks())
        return "project/document"
    }

    /** Element states of the working copy relative to the latest approved version (null before the first approval). */
    private fun changesSinceApproval(project: Project, document: ProjectDocument): DocumentChanges? =
        project.latestApprovedVersion?.let { versions.approvedDocument(project, it) }?.let { DocumentChanges(it, document) }

    @GetMapping("/p/{key}/details")
    fun editDetails(@AuthenticationPrincipal user: CurrentUser, @PathVariable key: String, model: Model): String {
        val project = projects.get(key)
        support.populate(model, project, user, documents.fromWorkingCopy(project), active = "overview")
        model.addAttribute("form", DetailsForm.of(project))
        return "project/details"
    }

    @PostMapping("/p/{key}/details")
    fun saveDetails(@AuthenticationPrincipal user: CurrentUser, @PathVariable key: String, form: DetailsForm, model: Model): String {
        val project = projects.get(key)
        return try {
            projects.updateDetails(user, project, form.details(), form.rowVersion)
            "redirect:/p/$key/draft"
        } catch (e: ValidationException) {
            support.populate(model, project, user, documents.fromWorkingCopy(project), active = "overview")
            model.addAttribute("form", form)
            model.addAttribute("error", e)
            "project/details"
        }
    }

    @GetMapping("/p/{key}/basic-info/edit")
    fun editBasicInfo(@AuthenticationPrincipal user: CurrentUser, @PathVariable key: String, model: Model): String {
        val project = projects.get(key)
        val document = documents.fromWorkingCopy(project)
        support.populate(model, project, user, document, active = "basic")
        model.addAttribute("markdown", project.basicInfoMd.orEmpty())
        model.addAttribute("rowVersion", project.rowVersion)
        model.addAttribute("previewHtml", support.render(project.basicInfoMd, project, document))
        return "project/basic-info"
    }

    @PostMapping("/p/{key}/basic-info")
    fun saveBasicInfo(
        @AuthenticationPrincipal user: CurrentUser,
        @PathVariable key: String,
        @RequestParam markdown: String,
        @RequestParam rowVersion: Int,
    ): String {
        projects.updateBasicInfo(user, projects.get(key), markdown, rowVersion)
        return "redirect:/p/$key/draft/document#problem-statement"
    }
}
