package io.reqlith.mcp

import com.github.difflib.DiffUtils
import com.github.difflib.UnifiedDiffUtils
import io.reqlith.common.ValidationException
import io.reqlith.diff.ProjectDiff
import io.reqlith.document.ChangeKind
import io.reqlith.document.DocElement
import io.reqlith.element.ElementType
import io.reqlith.export.ApprovalRow
import io.reqlith.export.ExportOptions
import io.reqlith.export.PrdInput
import io.reqlith.export.PrdMarkdownWriter
import io.reqlith.export.ReviewNote
import io.reqlith.review.Suggestion
import io.reqlith.config.ReqlithProperties
import org.springframework.ai.mcp.annotation.McpTool
import org.springframework.ai.mcp.annotation.McpToolParam
import org.springframework.ai.mcp.annotation.context.McpSyncRequestContext
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import java.time.Instant

// ------------------------------------------------------------------ tool results

data class ProjectSummary(
    val key: String, val title: String, val summary: String?, val workspace: String, val state: String,
    val latestApprovedVersion: String?, val owners: List<String>, val tags: List<String>, val domains: List<String>,
    val url: String, val updatedAt: Instant,
)

data class RequirementHit(
    val project: String, val version: String, val key: String, val type: String, val title: String,
    val priority: String?, val tags: List<String>, val excerpt: String,
)

data class TraceLinkOut(val kind: String, val target: String)

data class RequirementDetail(
    val project: String, val version: String, val key: String, val type: String, val title: String, val priority: String?,
    val tags: List<String>, val bodyMarkdown: String?, val linksTo: List<TraceLinkOut>, val linkedFrom: List<TraceLinkOut>,
    val externalLinks: List<LinkRow>, val url: String,
)

data class TraceNode(val key: String, val type: String, val title: String)
data class TraceEdge(val from: String, val kind: String, val to: String)
data class Traceability(
    val project: String, val version: String, val nodes: List<TraceNode>, val edges: List<TraceEdge>,
    val functionalWithoutScenario: List<String>, val brokenLinks: List<TraceEdge>,
)

data class VersionSummary(
    val version: String, val status: String, val basedOn: String?, val submittedBy: String, val submittedAt: Instant,
    val closedAt: Instant?, val contentHash: String, val decisions: List<String>, val url: String,
)

data class ItemChange(val key: String, val type: String, val title: String, val change: String, val properties: List<String>, val unifiedDiff: String)
data class VersionDiff(val project: String, val from: String, val to: String, val summary: String, val basicInformationDiff: String?, val changes: List<ItemChange>)

data class ReviewerDecision(val reviewer: String, val decision: String, val comment: String?)
data class SuggestionOut(val round: String, val target: String, val author: String, val status: String, val comment: String?, val proposedMarkdown: String?)
data class ReviewStatus(
    val project: String, val state: String, val openReview: String?, val reviewers: List<ReviewerDecision>,
    val openSuggestions: List<SuggestionOut>, val lastRounds: List<VersionSummary>,
)

/**
 * Read-only MCP tools for agents (assess requirements, suggest improvements, build a backlog elsewhere).
 * Every tool defaults to the latest approved version, reads only through [McpReadModel] and is logged.
 */
@Component
class ReqlithMcpTools(
    private val read: McpReadModel,
    private val log: McpAccessLog,
    private val properties: ReqlithProperties,
) {

    @McpTool(name = "list_projects", description = "List requirement projects with state, latest approved version, owners, tags and domains.",
        annotations = McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true))
    fun listProjects(
        @McpToolParam(description = "Optional text to match in key, title, tags or domains", required = false) query: String?,
        context: McpSyncRequestContext,
    ): List<ProjectSummary> {
        audit(context, "list_projects", null, mapOf("query" to query))
        val needle = query?.trim()?.lowercase().orEmpty()
        return read.projects()
            .filter { p -> needle.isEmpty() || (listOf(p.key, p.title, p.summary.orEmpty()) + p.tags + p.domains).any { it.lowercase().contains(needle) } }
            .map { p ->
                ProjectSummary(p.key, p.title, p.summary, p.workspace, if (p.status == "IN_REVIEW") "in review" else "draft",
                    p.latestApprovedVersion?.let { "v$it" }, p.owners, p.tags, p.domains, url("/p/${p.key}"), p.updatedAt)
            }
    }

    @McpTool(name = "get_prd", description = "The whole specification of a project as one PRD in Markdown (basic information, business, stakeholder and solution requirements, roles, scenarios, traceability).",
        annotations = McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true))
    fun getPrd(
        @McpToolParam(description = "Project key, e.g. PRJ-PAY-014") project: String,
        @McpToolParam(description = "Version: omit for the latest approved version, \"2\", \"3-rc1\" or \"draft\"", required = false) version: String?,
        @McpToolParam(description = "Include review comments and suggestions (default false)", required = false) includeReviewComments: Boolean?,
        context: McpSyncRequestContext,
    ): String {
        val p = read.project(project)
        audit(context, "get_prd", p.key, mapOf("version" to version, "includeReviewComments" to includeReviewComments))
        val resolved = read.document(p, version)
        val history = read.versions(p).filter { resolved.version == null || !it.submittedAt.isAfter(resolved.version.submittedAt) }
        val ids = history.map { it.id }
        val decisions = read.decisions(ids).groupBy { it.versionId }
        val labels = history.associate { it.id to it.label }
        val notes = read.suggestions(ids).map { s ->
            ReviewNote(labels[s.versionId].orEmpty(), target(s.elementKey), s.author,
                listOfNotNull(s.comment, s.proposedMd?.let { "Suggested wording: $it" }).joinToString(" — ") + " [${s.status.lowercase()}]")
        } + read.comments(ids).map { c -> ReviewNote(labels[c.versionId].orEmpty(), c.elementKey?.let(::target) ?: "General", c.author, c.body) }
        val input = PrdInput(
            document = resolved.document,
            versionLabel = resolved.label,
            status = resolved.status,
            date = (resolved.version?.closedAt ?: resolved.version?.submittedAt ?: p.updatedAt).toString(),
            contentHash = resolved.version?.contentHash,
            approvals = history.sortedBy { it.submittedAt }.map { v ->
                ApprovalRow(v.label, v.status.lowercase().replace('_', ' '), "${v.submittedAt} by ${v.submittedBy}",
                    decisions[v.id].orEmpty().joinToString("; ") { "${it.reviewer}: ${it.decision.lowercase()}" })
            },
            notes = notes,
        )
        return PrdMarkdownWriter.write(input, ExportOptions(traceabilityMatrix = true, approvalRecord = true, reviewComments = includeReviewComments == true))
    }

    @McpTool(name = "search_requirements", description = "Find requirements, scenarios, assumptions, risks and dependencies by type, tag or text. Searches the latest approved version of each project unless a version is given.",
        annotations = McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true))
    fun searchRequirements(
        @McpToolParam(description = "Project key; omit to search all projects with an approved version", required = false) project: String?,
        @McpToolParam(description = "Element type: BR (business), SR (stakeholder), FR (functional), NFR (non-functional), SC (scenario), AS (assumption), RK (risk), DP (dependency)", required = false) type: String?,
        @McpToolParam(description = "Text to find in key, title or body", required = false) text: String?,
        @McpToolParam(description = "Tag to match", required = false) tag: String?,
        @McpToolParam(description = "Version (only with a project): omit for latest approved, \"2\", \"3-rc1\" or \"draft\"", required = false) version: String?,
        context: McpSyncRequestContext,
    ): List<RequirementHit> {
        audit(context, "search_requirements", project, mapOf("type" to type, "text" to text, "tag" to tag, "version" to version))
        val wantedType = type?.trim()?.uppercase()?.takeIf { it.isNotEmpty() }?.let {
            runCatching { ElementType.valueOf(it) }.getOrElse { throw ValidationException("type", "Unknown type $type. Use one of ${ElementType.entries.joinToString()}") }
        }
        val needle = text?.trim()?.lowercase().orEmpty()
        val wantedTag = tag?.trim()?.removePrefix("#")?.lowercase()
        val sources = if (project != null) {
            listOf(read.document(read.project(project), version))
        } else {
            read.projects().filter { it.latestApprovedVersion != null }.map { read.document(it, null) }
        }
        return sources.flatMap { doc ->
            doc.document.elements.filter { e ->
                (wantedType == null || e.type == wantedType) &&
                    (wantedTag.isNullOrEmpty() || e.tags.any { it.lowercase() == wantedTag }) &&
                    (needle.isEmpty() || listOf(e.key, e.title, e.body.orEmpty()).any { it.lowercase().contains(needle) })
            }.map { e -> hit(doc.document.project.key, doc.label, e, needle) }
        }.take(200)
    }

    @McpTool(name = "get_requirement", description = "One element with its Markdown body, priority, tags, traceability in both directions and external links (JIRA, ADO).",
        annotations = McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true))
    fun getRequirement(
        @McpToolParam(description = "Project key") project: String,
        @McpToolParam(description = "Element key, e.g. FR-12") key: String,
        @McpToolParam(description = "Version: omit for latest approved, \"2\", \"3-rc1\" or \"draft\"", required = false) version: String?,
        context: McpSyncRequestContext,
    ): RequirementDetail {
        val p = read.project(project)
        audit(context, "get_requirement", p.key, mapOf("key" to key, "version" to version))
        val resolved = read.document(p, version)
        val element = resolved.document.element(key.trim().uppercase())
            ?: throw ValidationException("key", "$key is not part of ${p.key} ${resolved.label}")
        val incoming = resolved.document.incomingLinks()[element.key].orEmpty()
        return RequirementDetail(
            project = p.key, version = resolved.label, key = element.key, type = element.type.label, title = element.title,
            priority = element.priority?.label, tags = element.tags, bodyMarkdown = element.body,
            linksTo = element.links.map { TraceLinkOut(it.kind.label, it.toKey) },
            linkedFrom = incoming.map { TraceLinkOut(it.first.kind.inverseLabel, it.second) },
            externalLinks = read.links(p).filter { it.elementKey == element.key },
            url = resolved.version?.let { url("/p/${p.key}/v/${it.label.removePrefix("v")}#${element.key}") } ?: url("/p/${p.key}/e/${element.key}"),
        )
    }

    @McpTool(name = "get_traceability", description = "Traceability graph of a project (BR → SR → FR/NFR → scenarios, risks, dependencies, roles) with gaps: functional requirements without a scenario and broken links.",
        annotations = McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true))
    fun getTraceability(
        @McpToolParam(description = "Project key") project: String,
        @McpToolParam(description = "Version: omit for latest approved, \"2\", \"3-rc1\" or \"draft\"", required = false) version: String?,
        context: McpSyncRequestContext,
    ): Traceability {
        val p = read.project(project)
        audit(context, "get_traceability", p.key, mapOf("version" to version))
        val doc = read.document(p, version).let { it.document to it.label }
        val (document, label) = doc
        return Traceability(
            project = p.key,
            version = label,
            nodes = document.elements.map { TraceNode(it.key, it.type.label, it.title) } + document.roles.map { TraceNode(it.key, "Role", it.name) },
            edges = document.elements.flatMap { e -> e.links.map { TraceEdge(e.key, it.kind.label, it.toKey) } },
            functionalWithoutScenario = document.functionalWithoutScenario().map { it.key },
            brokenLinks = document.brokenLinks().map { TraceEdge(it.first, it.second.kind.label, it.second.toKey) },
        )
    }

    @McpTool(name = "list_versions", description = "Version history of a project: approved versions (immutable) and review candidates with their decisions.",
        annotations = McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true))
    fun listVersions(@McpToolParam(description = "Project key") project: String, context: McpSyncRequestContext): List<VersionSummary> {
        val p = read.project(project)
        audit(context, "list_versions", p.key, emptyMap())
        return summaries(p, read.versions(p))
    }

    @McpTool(name = "diff_versions", description = "What changed between two versions of a project (or a version and the working copy), as unified diffs per element.",
        annotations = McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true))
    fun diffVersions(
        @McpToolParam(description = "Project key") project: String,
        @McpToolParam(description = "From version, e.g. \"1\"; omit for the version before `to`", required = false) from: String?,
        @McpToolParam(description = "To version, e.g. \"2\" or \"draft\"; omit for the latest approved version", required = false) to: String?,
        context: McpSyncRequestContext,
    ): VersionDiff {
        val p = read.project(project)
        audit(context, "diff_versions", p.key, mapOf("from" to from, "to" to to))
        val after = read.document(p, to)
        val before = from?.let { read.document(p, it) }
            ?: after.version?.basedOnVersion?.let { read.document(p, it.toString()) }
            ?: p.latestApprovedVersion?.takeIf { after.version == null }?.let { read.document(p, it.toString()) }
        val diff = ProjectDiff(before?.document, after.document)
        return VersionDiff(
            project = p.key,
            from = before?.label ?: "(nothing)",
            to = after.label,
            summary = diff.changes.summary,
            basicInformationDiff = if (diff.changes.basicInfoChanged) unified("basic-information", before?.document?.basicInfo, after.document.basicInfo) else null,
            changes = diff.items.map { item ->
                ItemChange(item.key, item.typeLabel, item.title, item.kind.label.lowercase(), item.properties,
                    if (item.kind == ChangeKind.UNCHANGED) "" else unified(item.key, item.oldBody, item.newBody))
            },
        )
    }

    @McpTool(name = "get_review_status", description = "Review state of a project: open review round and reviewer decisions, open suggestions to resolve, recent rounds.",
        annotations = McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true))
    fun getReviewStatus(@McpToolParam(description = "Project key") project: String, context: McpSyncRequestContext): ReviewStatus {
        val p = read.project(project)
        audit(context, "get_review_status", p.key, emptyMap())
        val versions = read.versions(p)
        val open = versions.firstOrNull { it.status == "IN_REVIEW" }
        val labels = versions.associate { it.id to it.label }
        val closed = versions.filter { it.status != "IN_REVIEW" }.map { it.id }
        return ReviewStatus(
            project = p.key,
            state = when {
                open != null -> "in review (${open.label})"
                p.latestApprovedVersion != null -> "draft after v${p.latestApprovedVersion}"
                else -> "draft"
            },
            openReview = open?.label,
            reviewers = open?.let { v -> read.decisions(listOf(v.id)).map { ReviewerDecision(it.reviewer, it.decision.lowercase(), it.comment) } }.orEmpty(),
            openSuggestions = read.suggestions(closed).filter { it.status == "OPEN" }.map {
                SuggestionOut(labels[it.versionId].orEmpty(), target(it.elementKey), it.author, it.status.lowercase(), it.comment, it.proposedMd)
            },
            lastRounds = summaries(p, versions.take(5)),
        )
    }

    @McpTool(name = "get_external_links", description = "External references of a project (JIRA epics, ADO features, documents) for the project, specific versions or single elements.",
        annotations = McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true))
    fun getExternalLinks(@McpToolParam(description = "Project key") project: String, context: McpSyncRequestContext): List<LinkRow> {
        val p = read.project(project)
        audit(context, "get_external_links", p.key, emptyMap())
        return read.links(p)
    }

    @McpTool(name = "list_roles", description = "Roles (groups of users) shared across projects, with their general description and the projects using them.",
        annotations = McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true))
    fun listRoles(@McpToolParam(description = "Optional text to match in role name or description", required = false) query: String?, context: McpSyncRequestContext): List<RoleRow> {
        audit(context, "list_roles", null, mapOf("query" to query))
        val needle = query?.trim()?.lowercase().orEmpty()
        return read.roles().filter { needle.isEmpty() || it.name.lowercase().contains(needle) || it.description.orEmpty().lowercase().contains(needle) }
    }

    // ------------------------------------------------------------------ helpers

    private fun audit(context: McpSyncRequestContext, tool: String, projectKey: String?, args: Map<String, Any?>) {
        val user = SecurityContextHolder.getContext().authentication?.principal as? McpPrincipal
            ?: throw AccessDeniedException("Not authenticated")
        val client = runCatching { context.clientInfo()?.name() }.getOrNull()
        log.record(user, client, tool, projectKey?.trim()?.uppercase(), args)
    }

    private fun summaries(p: ProjectRow, versions: List<VersionRow>): List<VersionSummary> {
        val decisions = read.decisions(versions.map { it.id }).groupBy { it.versionId }
        return versions.map { v ->
            VersionSummary(v.label, v.status.lowercase().replace('_', ' '), v.basedOnVersion?.let { "v$it" }, v.submittedBy, v.submittedAt, v.closedAt,
                v.contentHash, decisions[v.id].orEmpty().map { "${it.reviewer}: ${it.decision.lowercase()}" }, url("/p/${p.key}/v/${v.label.removePrefix("v")}"))
        }
    }

    private fun hit(project: String, version: String, e: DocElement, needle: String): RequirementHit {
        val body = e.body.orEmpty().replace(Regex("\\s+"), " ").trim()
        val at = if (needle.isEmpty()) 0 else body.lowercase().indexOf(needle).coerceAtLeast(0)
        val excerpt = body.substring((at - 60).coerceAtLeast(0), (at + 180).coerceAtMost(body.length))
        return RequirementHit(project, version, e.key, e.type.label, e.title, e.priority?.label, e.tags, excerpt)
    }

    private fun unified(name: String, before: String?, after: String?): String {
        val old = before.orEmpty().lines()
        val new = after.orEmpty().lines()
        return UnifiedDiffUtils.generateUnifiedDiff("a/$name", "b/$name", old, DiffUtils.diff(old, new), 2).joinToString("\n")
    }

    private fun target(key: String) = if (key == Suggestion.BASIC_INFO) "Basic information" else key

    private fun url(path: String) = properties.baseUrl.trimEnd('/') + path
}
