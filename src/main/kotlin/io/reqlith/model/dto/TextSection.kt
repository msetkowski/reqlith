package io.reqlith.model.dto

import io.reqlith.exception.NotFoundException

/** A project-level markdown section edited as one text: its URL segment, document anchor and editor texts. */
enum class TextSection(val path: String, val anchor: String, val label: String, val heading: String, val hint: String) {
    BASIC_INFO(
        "basic-info",
        "problem-statement",
        "Basic information",
        "Problem statement, scope and success metrics",
        "Keep the three headings; assumptions, risks and dependencies are separate elements so they can be traced.",
    ),
    SOLUTION_DESIGN(
        "solution-design",
        "solution-design",
        "Solution design",
        "Solution design",
        "Architecture, components and key decisions. Mermaid diagrams (```mermaid) and element keys such as `FR-01` are supported.",
    ),
    ;

    companion object {
        fun ofPath(path: String): TextSection = entries.firstOrNull { it.path == path } ?: throw NotFoundException("No section $path")
    }
}
