package io.reqlith.web

import io.reqlith.common.ValidationException
import io.reqlith.document.ProjectDocumentService
import io.reqlith.export.ExportFile
import io.reqlith.export.ExportOptions
import io.reqlith.export.ExportService
import io.reqlith.link.ExternalLinkService
import io.reqlith.link.LinkSystem
import io.reqlith.mcp.McpAccessLog
import io.reqlith.project.Project
import io.reqlith.project.ProjectService
import io.reqlith.user.CurrentUser
import io.reqlith.version.ProjectVersion
import io.reqlith.version.VersionService
import org.springframework.http.ContentDisposition
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.servlet.mvc.support.RedirectAttributes
import java.util.UUID

/** "Export, links & agent access": PRD downloads, copyable links and external references. */
@Controller
class ExportController(
    private val projects: ProjectService,
    private val versions: VersionService,
    private val exports: ExportService,
    private val links: ExternalLinkService,
    private val documents: ProjectDocumentService,
    private val accessLog: McpAccessLog,
    private val support: ProjectViewSupport,
) {
    @GetMapping("/p/{key}/export")
    fun page(@AuthenticationPrincipal user: CurrentUser, @PathVariable key: String, model: Model): String {
        val project = projects.get(key)
        val document = documents.fromWorkingCopy(project)
        support.populate(model, project, user, document, active = "export")
        val history = versions.history(project)
        val latest = versions.latestApproved(project)
        model.addAttribute("exportable", history.filter { it.status.name in setOf("APPROVED", "IN_REVIEW") })
        model.addAttribute("latest", latest)
        model.addAttribute("links", links.all(project))
        model.addAttribute("systems", LinkSystem.entries)
        model.addAttribute("approvedNumbers", history.mapNotNull { it.versionNo })
        model.addAttribute("elementKeys", document.elements.map { it.key })
        model.addAttribute("latestUrl", support.absolute("/p/$key"))
        model.addAttribute("mcpUrl", support.absolute("/mcp"))
        model.addAttribute("recentAccess", accessLog.recent(projectKey = project.key))
        model.addAttribute("pinnedUrl", latest?.let { support.absolute("/p/$key/v/${it.slug}") })
        model.addAttribute("elementUrlExample", document.elements.firstOrNull()?.let { e ->
            latest?.let { support.absolute("/p/$key/v/${it.slug}#${e.key}") } ?: support.absolute("/p/$key/e/${e.key}")
        })
        return "export/export"
    }

    /** version = "draft", "2" or "3-rc1"; format = md or pdf. */
    @GetMapping("/p/{key}/export/download")
    fun download(
        @PathVariable key: String,
        @RequestParam(defaultValue = "latest") version: String,
        @RequestParam(defaultValue = "md") format: String,
        @RequestParam(defaultValue = "false") traceability: Boolean,
        @RequestParam(defaultValue = "false") approvals: Boolean,
        @RequestParam(defaultValue = "false") comments: Boolean,
    ): ResponseEntity<ByteArray> {
        val project = projects.get(key)
        return file(export(project, resolve(project, version), format, ExportOptions(traceability, approvals, comments)))
    }

    /** Short, stable download links with default options: /p/KEY/v/2/prd.pdf, /p/KEY/draft/prd.md. */
    @GetMapping("/p/{key}/v/{slug}/prd.{format}")
    fun versionPrd(@PathVariable key: String, @PathVariable slug: String, @PathVariable format: String): ResponseEntity<ByteArray> {
        val project = projects.get(key)
        return file(export(project, versions.bySlug(project, slug), format, ExportOptions()))
    }

    @GetMapping("/p/{key}/draft/prd.{format}")
    fun draftPrd(@PathVariable key: String, @PathVariable format: String): ResponseEntity<ByteArray> =
        file(export(projects.get(key), null, format, ExportOptions()))

    @PostMapping("/p/{key}/links")
    fun addLink(
        @AuthenticationPrincipal user: CurrentUser,
        @PathVariable key: String,
        @RequestParam system: LinkSystem,
        @RequestParam url: String,
        @RequestParam(required = false) label: String?,
        @RequestParam(required = false) scope: String?,
        @RequestParam(required = false) back: String?,
        redirect: RedirectAttributes,
    ): String {
        val project = projects.get(key)
        val (versionNo, elementKey) = when {
            scope.isNullOrBlank() || scope == "project" -> null to null
            scope.startsWith("v") -> scope.removePrefix("v").toIntOrNull() to null
            else -> null to scope
        }
        try {
            links.add(user, project, system, url, label, versionNo, elementKey)
        } catch (e: ValidationException) {
            redirect.addFlashAttribute("flashError", e.message)
        }
        return "redirect:" + safeBack(key, back, "/p/$key/export#links")
    }

    @PostMapping("/p/{key}/links/{id}/remove")
    fun removeLink(@AuthenticationPrincipal user: CurrentUser, @PathVariable key: String, @PathVariable id: UUID, @RequestParam(required = false) back: String?): String {
        links.remove(user, projects.get(key), id)
        return "redirect:" + safeBack(key, back, "/p/$key/export#links")
    }

    private fun resolve(project: Project, version: String): ProjectVersion? = when (version) {
        "draft" -> null
        "latest" -> versions.latestApproved(project)
        else -> versions.bySlug(project, version)
    }

    private fun export(project: Project, version: ProjectVersion?, format: String, options: ExportOptions): ExportFile = when (format) {
        "md" -> exports.markdown(project, version, options)
        "pdf" -> exports.pdf(project, version, options)
        else -> throw ValidationException("format", "Unknown format $format")
    }

    private fun file(file: ExportFile): ResponseEntity<ByteArray> =
        ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(file.contentType))
            .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(file.name).build().toString())
            .body(file.bytes)

    private fun safeBack(key: String, back: String?, default: String) = back?.takeIf { it.startsWith("/p/$key/") } ?: default
}
