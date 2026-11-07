package io.reqlith.model.dto

import io.reqlith.model.entity.Element
import io.reqlith.model.entity.ElementType
import io.reqlith.model.entity.LinkKind
import io.reqlith.model.entity.Priority
import io.reqlith.model.entity.TraceLink
import io.reqlith.service.MarkdownRenderer
import io.reqlith.service.ProjectService

class ElementForm(
    var type: ElementType = ElementType.FR,
    var title: String = "",
    var bodyMd: String? = null,
    var priority: Priority? = null,
    var tags: String? = null,
    var linkKinds: MutableList<LinkKind> = mutableListOf(),
    var linkTargets: MutableList<String> = mutableListOf(),
    var rowVersion: Int = 0,
) {
    fun content() = ElementContent(
        title = title,
        bodyMd = bodyMd,
        priority = priority,
        tags = ProjectService.parseList(tags),
        links = linkKinds.zip(linkTargets)
            .map { (kind, target) -> kind to target.trim().uppercase() }
            .filter { it.second.isNotEmpty() }
            .map { TraceLink(it.first, it.second) }
            .toSet(),
    )

    /** Existing links plus one empty row for adding a new link. */
    fun linkRows(): List<Pair<LinkKind, String>> = linkKinds.zip(linkTargets) + (LinkKind.DERIVED_FROM to "")

    companion object {
        fun of(element: Element): ElementForm {
            val links = element.links.sortedWith(compareBy({ it.kind.ordinal }, { it.toKey }))
            return ElementForm(
                type = element.type,
                title = element.title,
                bodyMd = element.bodyMd,
                priority = element.priority,
                tags = element.tags.joinToString(", "),
                linkKinds = links.map { it.kind }.toMutableList(),
                linkTargets = links.map { it.toKey }.toMutableList(),
                rowVersion = element.rowVersion,
            )
        }

        val KEY_PATTERN = MarkdownRenderer.KEY.pattern
    }
}
