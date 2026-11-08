package io.reqlith.controller

import io.reqlith.model.dto.CurrentUser
import io.reqlith.model.dto.RenderedDocument
import io.reqlith.model.entity.ElementType
import io.reqlith.model.entity.Project
import io.reqlith.model.entity.ProjectVersion
import io.reqlith.service.ProjectDocumentService
import io.reqlith.service.ProjectService
import io.reqlith.service.UserService
import io.reqlith.service.VersionService
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable

/** Frozen versions. `/p/{key}/v/{n}` never changes and is the link to paste into JIRA or ADO. */
@Controller
class VersionController(
    private val projects: ProjectService,
    private val versions: VersionService,
    private val documents: ProjectDocumentService,
    private val users: UserService,
    private val support: ProjectViewSupport,
) {
    /** The latest approved version; until there is one, the working copy. */
    @GetMapping("/p/{key}")
    fun latest(@AuthenticationPrincipal user: CurrentUser, @PathVariable key: String, model: Model): String {
        val project = projects.get(key)
        val approved = versions.latestApproved(project) ?: return "redirect:/p/$key/draft"
        return show(user, project, approved, model)
    }

    @GetMapping("/p/{key}/v/{slug}")
    fun version(@AuthenticationPrincipal user: CurrentUser, @PathVariable key: String, @PathVariable slug: String, model: Model): String {
        val project = projects.get(key)
        return show(user, project, versions.bySlug(project, slug), model)
    }

    private fun show(user: CurrentUser, project: Project, version: ProjectVersion, model: Model): String {
        val workingCopy = documents.fromWorkingCopy(project)
        support.populate(model, project, user, workingCopy, active = "document")
        val document = versions.document(version)
        model.addAttribute("version", version)
        model.addAttribute("versionUrl", support.absolute("/p/${project.key}/v/${version.slug}"))
        model.addAttribute("submittedBy", users.byIds(listOf(version.submittedBy))[version.submittedBy])
        model.addAttribute(
            "newerDraft",
            version.versionNo != null && version.versionNo == project.latestApprovedVersion &&
                versions.hasChangesSinceApproval(project),
        )
        model.addAttribute("isLatest", version.versionNo != null && version.versionNo == project.latestApprovedVersion)
        model.addAttribute("document", document)
        model.addAttribute("html", RenderedDocument.of(document) { support.renderInPage(it, document) })
        model.addAttribute("types", ElementType.entries)
        model.addAttribute("incoming", document.incomingLinks())
        return "project/document"
    }
}
