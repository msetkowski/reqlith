package io.reqlith.model.dto

import com.fasterxml.jackson.annotation.JsonInclude
import io.reqlith.model.entity.ElementType
import io.reqlith.model.entity.LinkKind
import io.reqlith.model.entity.Priority
import java.util.UUID

/**
 * The whole specification of a project as one value. It is built from the working copy and is also
 * the content of a version snapshot, so read views, diffs and exports only deal with this shape.
 */
data class ProjectDocument(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val project: DocProject,
    val basicInfo: String,
    val elements: List<DocElement>,
    val roles: List<DocRole>,
    /**
     * Added after schema version 1 without bumping it: snapshots without the field read as empty, and an empty
     * section is left out of the JSON, so documents without a solution design keep their hash.
     */
    @get:JsonInclude(JsonInclude.Include.NON_EMPTY)
    val solutionDesign: String = "",
) {
    fun elementsOf(type: ElementType): List<DocElement> = elements.filter { it.type == type }

    fun element(key: String): DocElement? = elements.firstOrNull { it.key == key }

    fun role(key: String): DocRole? = roles.firstOrNull { it.key == key }

    /** Incoming links per target key, e.g. SC-02 verifies FR-12 -> FR-12: [(VERIFIED_BY from SC-02)]. */
    fun incomingLinks(): Map<String, List<Pair<DocLink, String>>> = elements.flatMap { from -> from.links.map { it.toKey to (it to from.key) } }
        .groupBy({ it.first }, { it.second })

    /** Links whose target key does not exist in this document, nor among [baselineKeys] (the global NFR baseline). */
    fun brokenLinks(baselineKeys: Set<String> = emptySet()): List<Pair<String, DocLink>> {
        val known = elements.map { it.key }.toSet() + roles.map { it.key } + baselineKeys
        return elements.flatMap { e -> e.links.filter { it.toKey !in known }.map { e.key to it } }
    }

    /** Functional requirements that no scenario verifies (neither direction of a VERIFIED_BY link). */
    fun functionalWithoutScenario(): List<DocElement> {
        val scenarioKeys = elementsOf(ElementType.SC).map { it.key }.toSet()
        val verifiedByScenario = elements.flatMap { e ->
            e.links.filter { it.kind == LinkKind.VERIFIED_BY }.mapNotNull { link ->
                when {
                    e.type == ElementType.FR && link.toKey in scenarioKeys -> e.key
                    e.type == ElementType.SC -> link.toKey
                    else -> null
                }
            }
        }.toSet()
        return elementsOf(ElementType.FR).filter { it.key !in verifiedByScenario }
    }

    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
    }
}

data class DocProject(val key: String, val title: String, val summary: String?, val workspace: String, val tags: List<String>, val domains: List<String>)

data class DocElement(
    val key: String,
    val type: ElementType,
    val title: String,
    val body: String?,
    val priority: Priority?,
    val tags: List<String>,
    val links: List<DocLink>,
    val order: Int,
)

data class DocLink(val kind: LinkKind, val toKey: String)

/** A role with its global description copied in, so later edits of the global role never rewrite history. */
data class DocRole(val roleId: UUID, val key: String, val name: String, val globalDescription: String?, val contextInProject: String?)
