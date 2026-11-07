package io.reqlith.model.dto

import java.time.Instant

// ------------------------------------------------------------------ tool results

data class ProjectSummary(
    val key: String,
    val title: String,
    val summary: String?,
    val workspace: String,
    val state: String,
    val latestApprovedVersion: String?,
    val owners: List<String>,
    val tags: List<String>,
    val domains: List<String>,
    val url: String,
    val updatedAt: Instant,
)

data class RequirementHit(
    val project: String,
    val version: String,
    val key: String,
    val type: String,
    val title: String,
    val priority: String?,
    val tags: List<String>,
    val excerpt: String,
)

data class TraceLinkOut(val kind: String, val target: String)

data class RequirementDetail(
    val project: String,
    val version: String,
    val key: String,
    val type: String,
    val title: String,
    val priority: String?,
    val tags: List<String>,
    val bodyMarkdown: String?,
    val linksTo: List<TraceLinkOut>,
    val linkedFrom: List<TraceLinkOut>,
    val externalLinks: List<LinkRow>,
    val url: String,
)

data class TraceNode(val key: String, val type: String, val title: String)

data class TraceEdge(val from: String, val kind: String, val to: String)

data class Traceability(
    val project: String,
    val version: String,
    val nodes: List<TraceNode>,
    val edges: List<TraceEdge>,
    val functionalWithoutScenario: List<String>,
    val brokenLinks: List<TraceEdge>,
)

data class VersionSummary(
    val version: String,
    val status: String,
    val basedOn: String?,
    val submittedBy: String,
    val submittedAt: Instant,
    val closedAt: Instant?,
    val contentHash: String,
    val decisions: List<String>,
    val url: String,
)

data class ItemChange(val key: String, val type: String, val title: String, val change: String, val properties: List<String>, val unifiedDiff: String)

data class VersionDiff(
    val project: String,
    val from: String,
    val to: String,
    val summary: String,
    val basicInformationDiff: String?,
    val changes: List<ItemChange>,
)

data class ReviewerDecision(val reviewer: String, val decision: String, val comment: String?)

data class SuggestionOut(val round: String, val target: String, val author: String, val status: String, val comment: String?, val proposedMarkdown: String?)

data class ReviewStatus(
    val project: String,
    val state: String,
    val openReview: String?,
    val reviewers: List<ReviewerDecision>,
    val openSuggestions: List<SuggestionOut>,
    val lastRounds: List<VersionSummary>,
)
