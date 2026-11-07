package io.reqlith.service

import io.reqlith.model.dto.DocElement
import io.reqlith.model.dto.ExportOptions
import io.reqlith.model.dto.PrdInput
import io.reqlith.model.dto.ProjectDocument
import io.reqlith.model.entity.ElementType

/**
 * Writes a whole project as one PRD in Markdown. The PDF is rendered from this same text, so both
 * formats always agree. Headings inside element bodies are shifted down so the document outline stays intact.
 */
object PrdMarkdownWriter {

    fun write(input: PrdInput, options: ExportOptions): String = buildString {
        val doc = input.document
        val p = doc.project
        appendLine("# ${p.title}")
        appendLine()
        p.summary?.let { appendLine("> $it"); appendLine() }
        val facts = listOfNotNull(
            "**Project:** `${p.key}`",
            "**Version:** **${input.versionLabel}** · ${input.status} · ${input.date}",
            "**Workspace:** ${p.workspace}",
            p.domains.takeIf { it.isNotEmpty() }?.let { "**Domains:** ${it.joinToString(", ")}" },
            p.tags.takeIf { it.isNotEmpty() }?.let { tags -> "**Tags:** ${tags.joinToString(", ") { "#$it" }}" },
            input.contentHash?.let { "**Content hash (SHA-256):** `$it`" },
        )
        appendLine(facts.joinToString("\\\n"))
        appendLine()
        if (input.versionLabel == DRAFT) {
            appendLine("> **DRAFT** — this is the working copy. It has not been reviewed and may still change.")
            appendLine()
        }

        if (options.approvalRecord && input.approvals.isNotEmpty()) {
            appendLine("## Version and approval record")
            appendLine()
            appendLine("| Version | Status | Submitted | Reviewer decisions |")
            appendLine("|---|---|---|---|")
            input.approvals.forEach { appendLine("| ${cell(it.version)} | ${cell(it.status)} | ${cell(it.submitted)} | ${cell(it.decisions)} |") }
            appendLine()
        }

        var chapter = 0
        appendLine("## ${++chapter}. Basic information")
        appendLine()
        appendLine(shiftHeadings(doc.basicInfo, 1).trim())
        appendLine()
        listOf(ElementType.AS, ElementType.RK, ElementType.DP).forEach { type ->
            appendLine("### ${type.section}")
            appendLine()
            elements(doc, type, level = 4)
        }

        listOf(ElementType.BR, ElementType.SR, ElementType.FR, ElementType.NFR).forEach { type ->
            appendLine("## ${++chapter}. ${chapterTitle(type)}")
            appendLine()
            elements(doc, type, level = 3)
        }

        appendLine("## ${++chapter}. Roles")
        appendLine()
        if (doc.roles.isEmpty()) appendLine("_None._").also { appendLine() }
        doc.roles.forEach { role ->
            appendLine("### ${role.key} · ${role.name}")
            appendLine()
            role.globalDescription?.let { appendLine(shiftHeadings(it, 3).trim()); appendLine() }
            role.contextInProject?.let {
                appendLine("**In this project:**")
                appendLine()
                appendLine(shiftHeadings(it, 3).trim())
                appendLine()
            }
        }

        appendLine("## ${++chapter}. Scenarios")
        appendLine()
        elements(doc, ElementType.SC, level = 3)

        if (options.traceabilityMatrix) {
            appendLine("## Appendix A. Traceability matrix")
            appendLine()
            val incoming = doc.incomingLinks()
            appendLine("| Element | Title | Links to | Linked from |")
            appendLine("|---|---|---|---|")
            doc.elements.forEach { e ->
                val out = e.links.joinToString("<br>") { "${it.kind.label} `${it.toKey}`" }.ifEmpty { "—" }
                val into = incoming[e.key].orEmpty().joinToString("<br>") { "${it.first.kind.inverseLabel} `${it.second}`" }.ifEmpty { "—" }
                appendLine("| `${e.key}` | ${cell(e.title)} | $out | $into |")
            }
            appendLine()
        }

        if (options.reviewComments && input.notes.isNotEmpty()) {
            appendLine("## Appendix B. Review comments")
            appendLine()
            input.notes.forEach { appendLine("- **${it.version} · ${it.target}** (${it.author}): ${it.text.replace("\n", " ")}") }
            appendLine()
        }
    }.replace(Regex("\n{3,}"), "\n\n").replace("<br>", " · ")

    /** Increases every ATX heading level by [by] (max 6); fenced code blocks are left alone. */
    fun shiftHeadings(markdown: String, by: Int): String {
        var fence: String? = null
        return markdown.replace("\r\n", "\n").lines().joinToString("\n") { line ->
            val trimmed = line.trimStart()
            val marker = FENCE.find(trimmed)?.value
            when {
                fence != null -> { if (marker != null && trimmed.startsWith(fence!!)) fence = null; line }
                marker != null -> { fence = marker; line }
                else -> HEADING.find(line)?.let { m ->
                    "#".repeat((m.groupValues[1].length + by).coerceAtMost(6)) + line.substring(m.groupValues[1].length)
                } ?: line
            }
        }
    }

    private fun StringBuilder.elements(doc: ProjectDocument, type: ElementType, level: Int) {
        val items = doc.elementsOf(type)
        if (items.isEmpty()) {
            appendLine("_None._")
            appendLine()
        }
        items.forEach { element(it, level) }
    }

    private fun StringBuilder.element(e: DocElement, level: Int) {
        appendLine("${"#".repeat(level)} ${e.key} · ${e.title}")
        appendLine()
        val meta = listOfNotNull(
            e.priority?.let { "**Priority:** ${it.label}" },
            e.tags.takeIf { it.isNotEmpty() }?.let { tags -> "**Tags:** ${tags.joinToString(", ") { "#$it" }}" },
        )
        if (meta.isNotEmpty()) appendLine(meta.joinToString(" · ")).also { appendLine() }
        e.body?.let { appendLine(shiftHeadings(it, level - 1).trim()); appendLine() }
        if (e.links.isNotEmpty()) {
            appendLine("**Traceability:** " + e.links.joinToString("; ") { "${it.kind.label} `${it.toKey}`" })
            appendLine()
        }
    }

    private fun chapterTitle(type: ElementType) = when (type) {
        ElementType.FR -> "Solution requirements · Functional"
        ElementType.NFR -> "Solution requirements · Non-functional"
        else -> type.section
    }

    private fun cell(text: String) = text.replace("|", "\\|").replace("\n", " ")

    const val DRAFT = "DRAFT"
    private val FENCE = Regex("^(```+|~~~+)")
    private val HEADING = Regex("^(#{1,6})(?=\\s)")
}
