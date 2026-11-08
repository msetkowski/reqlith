package io.reqlith.service

import io.github.oshai.kotlinlogging.KotlinLogging
import io.reqlith.controller.Formats
import io.reqlith.model.dto.ApprovalRow
import io.reqlith.model.dto.ExportFile
import io.reqlith.model.dto.ExportOptions
import io.reqlith.model.dto.PrdInput
import io.reqlith.model.dto.ReviewNote
import io.reqlith.model.entity.Project
import io.reqlith.model.entity.ProjectVersion
import io.reqlith.model.entity.Suggestion
import io.reqlith.model.entity.VersionStatus
import io.reqlith.repository.ExportCache
import org.springframework.stereotype.Service
import org.thymeleaf.TemplateEngine
import org.thymeleaf.context.Context
import java.util.Base64

private val logger = KotlinLogging.logger {}

/**
 * Exports a project version (or the working copy, marked DRAFT) as a single PRD. The PDF is the
 * Markdown rendered by the shared [MarkdownRenderer] and printed by headless Chromium.
 */
@Service
class ExportService(
    private val versions: VersionService,
    private val reviews: ReviewService,
    private val documents: ProjectDocumentService,
    private val users: UserService,
    private val markdown: MarkdownRenderer,
    private val templates: TemplateEngine,
    private val pdf: PdfRenderer,
    private val cache: ExportCache,
    private val formats: Formats,
) {
    /** [version] null exports the working copy. */
    fun markdown(project: Project, version: ProjectVersion?, options: ExportOptions): ExportFile {
        val text = PrdMarkdownWriter.write(input(project, version), options)
        logger.info { "PRD exported project=${project.key} version=${version?.label ?: "draft"} format=md" }
        return ExportFile("${fileName(project, version)}.md", "text/markdown; charset=UTF-8", text.toByteArray())
    }

    fun pdf(project: Project, version: ProjectVersion?, options: ExportOptions): ExportFile {
        val name = "${fileName(project, version)}.pdf"
        // A candidate and its approved version share a content hash, so label and status are part of the key.
        // Open candidates are not cached: their status and review record are still changing.
        val cacheKey = version?.takeIf { it.status != VersionStatus.IN_REVIEW }?.let { "${options.key}|${it.label}|${it.status}" }
        cacheKey?.let { key ->
            cache.get(version.contentHash, "pdf", key)?.let {
                logger.info { "PRD exported project=${project.key} version=${version.label} format=pdf" }
                logger.debug { "PDF served from cache project=${project.key} version=${version.label} options=${options.key}" }
                return ExportFile(name, "application/pdf", it)
            }
        }
        val input = input(project, version)
        val html = printHtml(input, PrdMarkdownWriter.write(input, options))
        val bytes = pdf.render(html, "${project.key} · ${input.versionLabel} · ${input.document.project.title}")
        cacheKey?.let { cache.put(version.contentHash, "pdf", it, bytes) }
        logger.info { "PRD exported project=${project.key} version=${version?.label ?: "draft"} format=pdf" }
        logger.debug { "PDF rendered project=${project.key} version=${version?.label ?: "draft"} bytes=${bytes.size} cached=${cacheKey != null}" }
        return ExportFile(name, "application/pdf", bytes)
    }

    /** "PRD-PAY-001-v2", "PRD-PAY-001-v3-rc1" or "PRD-PAY-001-draft". */
    fun fileName(project: Project, version: ProjectVersion?): String = "PRD-" + project.key.removePrefix("PRJ-") + "-" + (version?.label ?: "draft")

    private fun input(project: Project, version: ProjectVersion?): PrdInput {
        val history = versions.history(project).filter { version == null || !it.submittedAt.isAfter(version.submittedAt) }
        val assignments = history.associateWith { reviews.assignments(it) }
        val suggestions = history.flatMap { v -> reviews.suggestions(v).map { v to it } }
        val comments = history.flatMap { v -> reviews.comments(v).map { v to it } }
        val names = users.byIds(
            history.map { it.submittedBy } + assignments.values.flatten().map { it.reviewerId } +
                suggestions.map { it.second.authorId } + comments.map { it.second.authorId },
        )
        fun name(id: java.util.UUID) = names[id]?.displayName ?: "unknown"
        return PrdInput(
            document = version?.let(versions::document) ?: documents.fromWorkingCopy(project),
            versionLabel = version?.label ?: PrdMarkdownWriter.DRAFT,
            status = version?.status?.label ?: "Working copy",
            date = formats.dateTime(version?.closedAt ?: version?.submittedAt ?: project.updatedAt),
            contentHash = version?.contentHash,
            approvals = history.sortedBy { it.submittedAt }.map { v ->
                ApprovalRow(
                    version = v.label,
                    status = v.status.label + (v.closedAt?.let { " · ${formats.date(it)}" } ?: ""),
                    submitted = "${formats.date(v.submittedAt)} by ${name(v.submittedBy)}",
                    decisions = assignments[v].orEmpty().joinToString("; ") { "${name(it.reviewerId)}: ${it.decision.label}" },
                )
            },
            notes = suggestions.map { (v, s) ->
                ReviewNote(
                    v.label,
                    target(s.elementKey),
                    name(s.authorId),
                    listOfNotNull(s.commentMd, s.proposedMd?.let { "Suggested wording: $it" }).joinToString(" — ") + " [${s.status.name.lowercase()}]",
                )
            } + comments.map { (v, c) -> ReviewNote(v.label, c.elementKey?.let(::target) ?: "General", name(c.authorId), c.bodyMd) },
        )
    }

    private fun target(key: String) = Suggestion.targetLabel(key)

    private fun printHtml(input: PrdInput, prd: String): String {
        val context = Context().apply {
            setVariable("title", "${input.document.project.title} ${input.versionLabel}")
            val body = markdown.render(prd)
            setVariable("body", body)
            setVariable("mermaid", body.contains(PdfRenderer.MERMAID_MARKER))
            setVariable("draft", input.versionLabel == PrdMarkdownWriter.DRAFT)
            setVariable("fonts", fontFaces)
        }
        return templates.process("export/print", context)
    }

    /** IBM Plex embedded as data URIs: the print page has no network access. */
    private val fontFaces: String by lazy {
        listOf(
            Triple("IBM Plex Sans", 400, "ibm-plex-sans-latin-400-normal"),
            Triple("IBM Plex Sans", 500, "ibm-plex-sans-latin-500-normal"),
            Triple("IBM Plex Sans", 600, "ibm-plex-sans-latin-600-normal"),
            Triple("IBM Plex Mono", 400, "ibm-plex-mono-latin-400-normal"),
        ).joinToString("\n") { (family, weight, file) ->
            val bytes = requireNotNull(javaClass.getResourceAsStream("/static/vendor/fonts/$file.woff2")).use { it.readAllBytes() }
            "@font-face { font-family: \"$family\"; font-weight: $weight; src: url(data:font/woff2;base64,${Base64.getEncoder().encodeToString(
                bytes,
            )}) format(\"woff2\"); }"
        }
    }
}
