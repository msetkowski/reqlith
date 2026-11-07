package io.reqlith.model.dto

import java.time.Instant
import java.util.UUID

data class ProjectRow(
    val id: UUID,
    val key: String,
    val title: String,
    val summary: String?,
    val workspace: String,
    val status: String,
    val latestApprovedVersion: Int?,
    val updatedAt: Instant,
    val tags: List<String>,
    val domains: List<String>,
    val owners: List<String>,
)


data class VersionRow(
    val id: UUID,
    val versionNo: Int?,
    val targetVersion: Int,
    val candidateNo: Int,
    val status: String,
    val snapshot: String,
    val contentHash: String,
    val basedOnVersion: Int?,
    val submittedBy: String,
    val submittedAt: Instant,
    val closedAt: Instant?,
) {
    val label: String get() = versionNo?.let { "v$it" } ?: "v$targetVersion-rc$candidateNo"
}


data class DecisionRow(val versionId: UUID, val reviewer: String, val decision: String, val comment: String?, val decidedAt: Instant?)


data class SuggestionRow(
    val versionId: UUID, val elementKey: String, val proposedMd: String?, val comment: String?,
    val author: String, val status: String, val createdAt: Instant,
)


data class CommentRow(val versionId: UUID, val elementKey: String?, val author: String, val body: String, val createdAt: Instant)


data class LinkRow(val system: String, val url: String, val label: String, val versionNo: Int?, val elementKey: String?)


data class RoleRow(val key: String, val name: String, val description: String?, val projects: List<String>)


/** A document and where it came from. */
data class ResolvedDocument(val document: ProjectDocument, val label: String, val status: String, val version: VersionRow?)
