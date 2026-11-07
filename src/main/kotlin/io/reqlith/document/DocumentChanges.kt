package io.reqlith.document

enum class ChangeKind(val label: String) {
    NEW("New"),
    CHANGED("Changed"),
    UNCHANGED("Unchanged"),
    REMOVED("Removed"),
}

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

    val projectChanged: Boolean = before == null || before.project != after.project

    fun of(key: String): ChangeKind = elements[key] ?: roles[key] ?: ChangeKind.UNCHANGED

    fun count(kind: ChangeKind): Int = when (kind) {
        ChangeKind.REMOVED -> removedElements.size + removedRoles.size
        else -> elements.values.count { it == kind } + roles.values.count { it == kind }
    }

    val hasChanges: Boolean
        get() = basicInfoChanged || projectChanged || count(ChangeKind.NEW) + count(ChangeKind.CHANGED) + count(ChangeKind.REMOVED) > 0

    /** "3 new, 2 changed, 1 removed". */
    val summary: String
        get() = listOf(ChangeKind.NEW, ChangeKind.CHANGED, ChangeKind.REMOVED)
            .mapNotNull { kind -> count(kind).takeIf { it > 0 }?.let { "$it ${kind.label.lowercase()}" } }
            .plus(if (basicInfoChanged) listOf("basic information changed") else emptyList())
            .ifEmpty { listOf("no changes") }
            .joinToString(", ")

    private fun <T> kindOf(old: T?, new: T, firstVersion: Boolean): ChangeKind = when {
        firstVersion || old == null -> ChangeKind.NEW
        old != new -> ChangeKind.CHANGED
        else -> ChangeKind.UNCHANGED
    }
}
