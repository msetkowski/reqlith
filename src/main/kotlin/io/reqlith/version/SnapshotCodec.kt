package io.reqlith.version

import io.reqlith.document.ProjectDocument
import org.springframework.stereotype.Component
import tools.jackson.databind.JsonNode
import tools.jackson.databind.MapperFeature
import tools.jackson.databind.SerializationFeature
import tools.jackson.databind.json.JsonMapper
import tools.jackson.databind.node.ObjectNode
import tools.jackson.module.kotlin.jacksonMapperBuilder
import tools.jackson.module.kotlin.treeToValue
import java.security.MessageDigest

/** A document serialized for storage, with the SHA-256 of its canonical form. */
class Snapshot(val json: String, val hash: String, val schemaVersion: Int)

/**
 * Turns a [ProjectDocument] into the immutable JSON stored in a version, and back.
 *
 * The JSON is canonical (sorted keys, `\n` line endings, no insignificant whitespace), so equal content
 * always gives the same SHA-256. The hash makes tampering detectable and tells whether the working copy
 * differs from an approved version.
 *
 * Old snapshots keep their schema forever. When [ProjectDocument] changes shape, bump
 * [ProjectDocument.CURRENT_SCHEMA_VERSION] and add an upgrader from the previous version to [upgraders].
 */
@Component
class SnapshotCodec {

    private val mapper: JsonMapper = jacksonMapperBuilder()
        .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
        .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
        .disable(SerializationFeature.INDENT_OUTPUT)
        .build()

    /** schemaVersion N -> function upgrading an N document to N + 1. */
    private val upgraders: Map<Int, (ObjectNode) -> ObjectNode> = emptyMap()

    fun write(document: ProjectDocument): Snapshot {
        val normalized = normalizeLineEndings(mapper.valueToTree<JsonNode>(document))
        // Re-serializing a tree keeps object keys in insertion order, so sort them explicitly.
        val json = mapper.writeValueAsString(sortKeys(normalized))
        return Snapshot(json, sha256(json), document.schemaVersion)
    }

    fun read(json: String): ProjectDocument {
        var tree = mapper.readTree(json) as ObjectNode
        var version = tree.get("schemaVersion")?.asInt() ?: 1
        while (version < ProjectDocument.CURRENT_SCHEMA_VERSION) {
            val upgrade = upgraders[version] ?: error("No snapshot upgrader from schema version $version")
            tree = upgrade(tree)
            version++
            tree.put("schemaVersion", version)
        }
        return mapper.treeToValue<ProjectDocument>(tree)
    }

    /** True when the stored JSON still matches its recorded hash. */
    fun verify(json: String, hash: String): Boolean = sha256(json) == hash

    private fun normalizeLineEndings(node: JsonNode): JsonNode = when {
        node.isString -> mapper.nodeFactory.stringNode(node.asString().replace("\r\n", "\n").replace('\r', '\n'))
        node.isObject -> (node as ObjectNode).also { obj -> obj.propertyNames().toList().forEach { obj.set(it, normalizeLineEndings(obj.get(it))) } }
        node.isArray -> mapper.nodeFactory.arrayNode().also { array -> node.forEach { array.add(normalizeLineEndings(it)) } }
        else -> node
    }

    private fun sortKeys(node: JsonNode): JsonNode = when {
        node.isObject -> mapper.nodeFactory.objectNode().also { sorted ->
            node.propertyNames().sorted().forEach { sorted.set(it, sortKeys(node.get(it))) }
        }
        node.isArray -> mapper.nodeFactory.arrayNode().also { array -> node.forEach { array.add(sortKeys(it)) } }
        else -> node
    }

    private fun sha256(text: String): String =
        MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
}
