package io.reqlith.mcp

import io.reqlith.common.NotFoundException
import io.reqlith.common.ValidationException
import io.reqlith.document.DocElement
import io.reqlith.document.DocLink
import io.reqlith.document.DocProject
import io.reqlith.document.DocRole
import io.reqlith.document.ProjectDocument
import io.reqlith.element.ElementType
import io.reqlith.element.LinkKind
import io.reqlith.element.Priority
import io.reqlith.version.SnapshotCodec
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Component
import java.sql.ResultSet
import java.time.Instant
import java.time.OffsetDateTime
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

/**
 * Everything the MCP tools read, through the read-only connection only. Version content comes from the
 * immutable snapshots; the working copy (version="draft") is assembled from its tables.
 */
@Component
class McpReadModel(database: McpReadOnlyDatabase, private val codec: SnapshotCodec) {
    private val jdbc: JdbcClient = database.jdbc

    fun projects(): List<ProjectRow> {
        val tags = pairs("select project_id, tag from project_tag")
        val domains = pairs("select project_id, domain from project_domain")
        val owners = pairs(
            """select m.project_id, u.display_name from project_member m join app_user u on u.id = m.user_id
               where m.member_role = 'OWNER'""",
        )
        return jdbc.sql(
            """select p.id, p.project_key, p.title, p.summary, w.name as workspace, p.status, p.latest_approved_version, p.updated_at
               from project p join workspace w on w.id = p.workspace_id order by p.project_key""",
        ).query { rs, _ ->
            val id = rs.uuid("id")
            ProjectRow(
                id, rs.getString("project_key"), rs.getString("title"), rs.getString("summary"), rs.getString("workspace"),
                rs.getString("status"), rs.intOrNull("latest_approved_version"), rs.instant("updated_at")!!,
                tags[id].orEmpty().sorted(), domains[id].orEmpty().sorted(), owners[id].orEmpty().sorted(),
            )
        }.list()
    }

    fun project(key: String): ProjectRow =
        projects().firstOrNull { it.key.equals(key.trim(), ignoreCase = true) } ?: throw NotFoundException("Project $key not found")

    fun versions(project: ProjectRow): List<VersionRow> =
        jdbc.sql(
            """select v.id, v.version_no, v.target_version, v.candidate_no, v.status, v.snapshot, v.content_hash,
                      v.based_on_version, u.display_name as submitted_by, v.submitted_at, v.closed_at
               from project_version v join app_user u on u.id = v.submitted_by
               where v.project_id = ? order by v.submitted_at desc""",
        ).param(project.id).query { rs, _ ->
            VersionRow(
                rs.uuid("id"), rs.intOrNull("version_no"), rs.getInt("target_version"), rs.getInt("candidate_no"), rs.getString("status"),
                rs.getString("snapshot"), rs.getString("content_hash"), rs.intOrNull("based_on_version"), rs.getString("submitted_by"),
                rs.instant("submitted_at")!!, rs.instant("closed_at"),
            )
        }.list()

    /**
     * [version]: null or "latest" = latest approved, "draft" = working copy, "2" = v2, "3-rc1" = candidate.
     * Agents get approved content unless they explicitly ask for something else.
     */
    fun document(project: ProjectRow, version: String?): ResolvedDocument {
        val wanted = version?.trim()?.lowercase()?.removePrefix("v")?.ifEmpty { null }
        if (wanted == "draft") return ResolvedDocument(workingCopy(project), "DRAFT", "Working copy (not reviewed)", null)
        val versions = versions(project)
        val row = when (wanted) {
            null, "latest" -> versions.firstOrNull { it.status == "APPROVED" }
                ?: throw ValidationException(
                    "version",
                    "${project.key} has no approved version yet. Pass version=\"draft\" to read the unreviewed working copy.",
                )
            else -> versions.firstOrNull { it.label == "v$wanted" }
                ?: throw NotFoundException("Version v$wanted of ${project.key} not found. Available: ${versions.joinToString { it.label }}")
        }
        return ResolvedDocument(codec.read(row.snapshot), row.label, row.status.lowercase().replace('_', ' '), row)
    }

    fun decisions(versionIds: Collection<UUID>): List<DecisionRow> = byVersion(
        """select a.version_id, u.display_name, a.decision, a.comment_md, a.decided_at
           from review_assignment a join app_user u on u.id = a.reviewer_id where a.version_id in (:ids)""",
        versionIds,
    ) { rs -> DecisionRow(rs.uuid("version_id"), rs.getString("display_name"), rs.getString("decision"), rs.getString("comment_md"), rs.instant("decided_at")) }

    fun suggestions(versionIds: Collection<UUID>): List<SuggestionRow> = byVersion(
        """select s.version_id, s.element_key, s.proposed_md, s.comment_md, u.display_name, s.status, s.created_at
           from suggestion s join app_user u on u.id = s.author_id where s.version_id in (:ids) order by s.created_at""",
        versionIds,
    ) { rs ->
        SuggestionRow(rs.uuid("version_id"), rs.getString("element_key"), rs.getString("proposed_md"), rs.getString("comment_md"),
            rs.getString("display_name"), rs.getString("status"), rs.instant("created_at")!!)
    }

    fun comments(versionIds: Collection<UUID>): List<CommentRow> = byVersion(
        """select c.version_id, c.element_key, u.display_name, c.body_md, c.created_at
           from review_comment c join app_user u on u.id = c.author_id where c.version_id in (:ids) order by c.created_at""",
        versionIds,
    ) { rs -> CommentRow(rs.uuid("version_id"), rs.getString("element_key"), rs.getString("display_name"), rs.getString("body_md"), rs.instant("created_at")!!) }

    fun links(project: ProjectRow): List<LinkRow> =
        jdbc.sql("select link_system, url, label, version_no, element_key from external_link where project_id = ? order by created_at")
            .param(project.id)
            .query { rs, _ -> LinkRow(rs.getString("link_system"), rs.getString("url"), rs.getString("label"), rs.intOrNull("version_no"), rs.getString("element_key")) }
            .list()

    fun roles(): List<RoleRow> {
        val usage = jdbc.sql("select pr.role_id, p.project_key from project_role pr join project p on p.id = pr.project_id")
            .query { rs, _ -> rs.uuid("role_id") to rs.getString("project_key") }.list()
            .groupBy({ it.first }, { it.second })
        return jdbc.sql("select id, role_key, name, description_md from role order by name")
            .query { rs, _ -> RoleRow(rs.getString("role_key"), rs.getString("name"), rs.getString("description_md"), usage[rs.uuid("id")].orEmpty().sorted()) }
            .list()
    }

    /** The working copy, assembled the same way as ProjectDocumentService (sorted, normalized). */
    private fun workingCopy(project: ProjectRow): ProjectDocument {
        val basicInfo = jdbc.sql("select basic_info_md from project where id = ?").param(project.id).query(String::class.java).optional().orElse("")
        val tags = pairs("select t.element_id, t.tag from element_tag t join element e on e.id = t.element_id where e.project_id = ?", project.id)
        val links = jdbc.sql("select l.from_element_id, l.link_kind, l.to_key from trace_link l join element e on e.id = l.from_element_id where e.project_id = ?")
            .param(project.id).query { rs, _ -> rs.uuid("from_element_id") to DocLink(LinkKind.valueOf(rs.getString("link_kind")), rs.getString("to_key")) }
            .list().groupBy({ it.first }, { it.second })
        val elements = jdbc.sql("select id, element_type, element_key, title, body_md, priority, sort_order from element where project_id = ?")
            .param(project.id).query { rs, _ ->
                val id = rs.uuid("id")
                DocElement(
                    key = rs.getString("element_key"),
                    type = ElementType.valueOf(rs.getString("element_type")),
                    title = rs.getString("title"),
                    body = rs.getString("body_md"),
                    priority = rs.getString("priority")?.let(Priority::valueOf),
                    tags = tags[id].orEmpty().sorted(),
                    links = links[id].orEmpty().sortedWith(compareBy({ it.kind.ordinal }, { it.toKey })),
                    order = rs.getInt("sort_order"),
                )
            }.list().sortedWith(compareBy({ it.type.ordinal }, { it.order }))
        val roles = jdbc.sql(
            """select r.id, r.role_key, r.name, r.description_md, pr.context_md from project_role pr join role r on r.id = pr.role_id
               where pr.project_id = ? order by pr.sort_order""",
        ).param(project.id).query { rs, _ ->
            DocRole(rs.uuid("id"), rs.getString("role_key"), rs.getString("name"), rs.getString("description_md"), rs.getString("context_md"))
        }.list()
        return ProjectDocument(
            project = DocProject(project.key, project.title, project.summary, project.workspace, project.tags, project.domains),
            basicInfo = basicInfo.orEmpty(),
            elements = elements,
            roles = roles,
        )
    }

    private fun <T : Any> byVersion(sql: String, ids: Collection<UUID>, map: (ResultSet) -> T): List<T> =
        if (ids.isEmpty()) emptyList() else jdbc.sql(sql).param("ids", ids.toList()).query { rs, _ -> map(rs) }.list()

    private fun pairs(sql: String, vararg params: Any): Map<UUID, List<String>> =
        jdbc.sql(sql).params(*params).query { rs, _ -> rs.uuid(1) to rs.getString(2) }.list().groupBy({ it.first }, { it.second })

    private fun ResultSet.uuid(column: String): UUID = UUID.fromString(getString(column))
    private fun ResultSet.uuid(column: Int): UUID = UUID.fromString(getString(column))
    private fun ResultSet.intOrNull(column: String): Int? = getInt(column).takeUnless { wasNull() }
    private fun ResultSet.instant(column: String): Instant? = getObject(column, OffsetDateTime::class.java)?.toInstant()
}
