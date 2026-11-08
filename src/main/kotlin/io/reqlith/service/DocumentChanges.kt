package io.reqlith.service

import io.reqlith.model.dto.ChangeKind
import io.reqlith.model.dto.DocElement
import io.reqlith.model.dto.DocRole
import io.reqlith.model.dto.ProjectDocument

/**
 * What changed between two documents, matched by element and role key. A null [before] means there is
 * nothing to compare against (first version), so everything is new.
 */
class DocumentChanges(before: ProjectDocument?, after: ProjectDocument) {

    val elements: Map<String, ChangeKind> = after.elements.associate { element ->
        element.key to kindOf(before?.element(element.key), element, before == null)
    }

    val removedElements: List<DocElement> = before?.elements?.filter { after.element(it.key) == null }.orEmpty()

    val roles: Map<String, ChangeKind> = after.roles.associate { role ->
        role.key to kindOf(before?.role(role.key), role, before == null)
    }

    val removedRoles: List<DocRole> = before?.roles?.filter { after.role(it.key) == null }.orEmpty()

    val basicInfoChanged: Boolean = before == null || before.basicInfo != after.basicInfo

    val solutionDesignChanged: Boolean = before?.solutionDesign != after.solutionDesign && !(before == null && after.solutionDesign.isEmpty())

    val projectChanged: Boolean = before == null || before.project != after.project

    fun of(key: String): ChangeKind = elements[key] ?: roles[key] ?: ChangeKind.UNCHANGED

    fun count(kind: ChangeKind): Int = when (kind) {
        ChangeKind.REMOVED -> removedElements.size + removedRoles.size
        else -> elements.values.count { it == kind } + roles.values.count { it == kind }
    }

    val newCount: Int get() = count(ChangeKind.NEW)
    val changedCount: Int get() = count(ChangeKind.CHANGED)
    val removedCount: Int get() = count(ChangeKind.REMOVED)

    val hasChanges: Boolean
        get() = basicInfoChanged || solutionDesignChanged || projectChanged || count(ChangeKind.NEW) + count(ChangeKind.CHANGED) + count(ChangeKind.REMOVED) > 0

    /** "3 new, 2 changed, 1 removed". */
    val summary: String
        get() = listOf(ChangeKind.NEW, ChangeKind.CHANGED, ChangeKind.REMOVED)
            .mapNotNull { kind -> count(kind).takeIf { it > 0 }?.let { "$it ${kind.label.lowercase()}" } }
            .plus(if (basicInfoChanged) listOf("basic information changed") else emptyList())
            .plus(if (solutionDesignChanged) listOf("solution design changed") else emptyList())
            .ifEmpty { listOf("no changes") }
            .joinToString(", ")

    private fun <T> kindOf(old: T?, new: T, firstVersion: Boolean): ChangeKind = when {
        firstVersion || old == null -> ChangeKind.NEW
        old != new -> ChangeKind.CHANGED
        else -> ChangeKind.UNCHANGED
    }
}
