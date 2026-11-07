package io.reqlith.service

import io.reqlith.model.dto.ChangeKind
import io.reqlith.model.dto.DiffLine
import io.reqlith.model.dto.DocElement
import io.reqlith.model.dto.DocRole
import io.reqlith.model.dto.ItemDiff
import io.reqlith.model.dto.ProjectDocument
import io.reqlith.model.entity.ElementType

/**
 * Compares two project documents: elements and roles are matched by key, bodies are diffed line by
 * line with word-level highlights.
 */
class ProjectDiff(val before: ProjectDocument?, val after: ProjectDocument) {

    val changes = DocumentChanges(before, after)

    val basicInfo: List<DiffLine?> = TextDiff.withContext(TextDiff.lines(before?.basicInfo, after.basicInfo))

    val projectProperties: List<String> = buildList {
        val old = before?.project ?: return@buildList
        val new = after.project
        if (old.title != new.title) add("Title: ${old.title} → ${new.title}")
        if (old.summary != new.summary) add("Summary changed")
        if (old.tags != new.tags) add("Tags: ${old.tags.joinToString().ifEmpty { "—" }} → ${new.tags.joinToString().ifEmpty { "—" }}")
        if (old.domains != new.domains) add("Domains: ${old.domains.joinToString().ifEmpty { "—" }} → ${new.domains.joinToString().ifEmpty { "—" }}")
    }

    /** Changed, new and removed items in document order; unchanged items are left out. */
    val items: List<ItemDiff> = buildList {
        ElementType.entries.forEach { type ->
            if (type == ElementType.SC) addAll(roleDiffs())
            after.elementsOf(type).forEach { element ->
                val kind = changes.of(element.key)
                if (kind != ChangeKind.UNCHANGED) add(elementDiff(before?.element(element.key), element, kind))
            }
            changes.removedElements.filter { it.type == type }.forEach { add(elementDiff(it, null, ChangeKind.REMOVED)) }
        }
    }

    fun item(key: String): ItemDiff? = items.firstOrNull { it.key == key }

    private fun elementDiff(old: DocElement?, new: DocElement?, kind: ChangeKind): ItemDiff {
        val current = new ?: old!!
        return ItemDiff(
            key = current.key,
            title = current.title,
            typeLabel = current.type.shortLabel,
            kind = kind,
            titleHtml = if (old != null && new != null && old.title != new.title) TextDiff.inline(old.title, new.title) else escape(current.title),
            titleChanged = old != null && new != null && old.title != new.title,
            lines = TextDiff.withContext(TextDiff.lines(old?.body, new?.body)),
            properties = if (old == null || new == null) emptyList() else buildList {
                if (old.priority != new.priority) add("Priority: ${old.priority?.label ?: "—"} → ${new.priority?.label ?: "—"}")
                if (old.tags != new.tags) add("Tags: ${old.tags.joinToString().ifEmpty { "—" }} → ${new.tags.joinToString().ifEmpty { "—" }}")
                (new.links - old.links.toSet()).forEach { add("Link added: ${it.kind.label} ${it.toKey}") }
                (old.links - new.links.toSet()).forEach { add("Link removed: ${it.kind.label} ${it.toKey}") }
            },
            oldBody = old?.body,
            newBody = new?.body,
        )
    }

    private fun roleDiffs(): List<ItemDiff> {
        fun diff(old: DocRole?, new: DocRole?, kind: ChangeKind): ItemDiff {
            val current = new ?: old!!
            val oldText = old?.let { "${it.globalDescription.orEmpty()}\n\nIn this project:\n${it.contextInProject.orEmpty()}" }
            val newText = new?.let { "${it.globalDescription.orEmpty()}\n\nIn this project:\n${it.contextInProject.orEmpty()}" }
            return ItemDiff(
                key = current.key, title = current.name, typeLabel = "Role", kind = kind,
                titleHtml = if (old != null && new != null && old.name != new.name) TextDiff.inline(old.name, new.name) else escape(current.name),
                titleChanged = old != null && new != null && old.name != new.name,
                lines = TextDiff.withContext(TextDiff.lines(oldText, newText)), properties = emptyList(),
                oldBody = oldText, newBody = newText,
            )
        }
        return after.roles.filter { changes.of(it.key) != ChangeKind.UNCHANGED }.map { diff(before?.role(it.key), it, changes.of(it.key)) } +
            changes.removedRoles.map { diff(it, null, ChangeKind.REMOVED) }
    }

    private fun escape(text: String) = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
}
