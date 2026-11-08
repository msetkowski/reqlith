package io.reqlith.controller

import io.reqlith.model.dto.CurrentUser
import io.reqlith.model.dto.ProjectDocument
import io.reqlith.model.entity.Project
import io.reqlith.service.ProjectDiff
import io.reqlith.service.ProjectDocumentService
import io.reqlith.service.ProjectService
import io.reqlith.service.VersionService
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestParam

/** Compares any two versions, or a version with the working copy ("what will change in the next version"). */
@Controller
class DiffController(
    private val projects: ProjectService,
    private val versions: VersionService,
    private val documents: ProjectDocumentService,
    private val support: ProjectViewSupport,
) {
    /** from/to: "draft", "2" or "3-rc1". Defaults: latest approved → working copy. */
    @GetMapping("/p/{key}/diff")
    fun diff(
        @AuthenticationPrincipal user: CurrentUser,
        @PathVariable key: String,
        @RequestParam(required = false) from: String?,
        @RequestParam(required = false) to: String?,
        @RequestParam(defaultValue = "source") view: String,
        model: Model,
    ): String {
        val project = projects.get(key)
        val workingCopy = documents.fromWorkingCopy(project)
        support.populate(model, project, user, workingCopy, active = "diff")
        val fromSlug = from ?: project.latestApprovedVersion?.toString()
        val toSlug = to ?: "draft"
        val before = fromSlug?.let { document(project, it, workingCopy) }
        val after = document(project, toSlug, workingCopy)
        val diff = ProjectDiff(before, after)
        model.addAttribute("diff", diff)
        model.addAttribute("from", fromSlug)
        model.addAttribute("to", toSlug)
        model.addAttribute("view", view)
        model.addAttribute("choices", listOf("draft" to "Working copy") + versions.history(project).map { it.slug to "${it.label} · ${it.status.label}" })
        if (view == "rendered") {
            model.addAttribute("renderedOld", diff.items.associate { it.key to support.renderInPage(it.oldBody, before ?: after) })
            model.addAttribute("renderedNew", diff.items.associate { it.key to support.renderInPage(it.newBody, after) })
            model.addAttribute("basicOld", support.renderInPage(before?.basicInfo, before ?: after))
            model.addAttribute("basicNew", support.renderInPage(after.basicInfo, after))
            model.addAttribute("designOld", support.renderInPage(before?.solutionDesign, before ?: after))
            model.addAttribute("designNew", support.renderInPage(after.solutionDesign, after))
        }
        return "diff/diff"
    }

    private fun document(project: Project, slug: String, workingCopy: ProjectDocument): ProjectDocument =
        if (slug == "draft") workingCopy else versions.document(versions.bySlug(project, slug))
}
