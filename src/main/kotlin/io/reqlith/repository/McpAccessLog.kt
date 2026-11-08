package io.reqlith.repository

import io.reqlith.model.dto.AccessLogEntry
import io.reqlith.model.dto.McpPrincipal
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Component
import tools.jackson.databind.json.JsonMapper
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

/** Every MCP tool call, so owners can see which agents read their specifications. */
@Component
class McpAccessLog(private val jdbc: JdbcClient, private val clock: Clock) {
    private val json = JsonMapper.builder().build()

    fun record(user: McpPrincipal, clientName: String?, tool: String, projectKey: String?, args: Map<String, Any?>) {
        jdbc.sql(
            """
            insert into mcp_access_log (occurred_at, client_name, user_id, token_id, tool, project_key, args_json)
            values (:at, :client, :user, :token, :tool, :project, :args)
            """,
        ).param("at", Instant.now(clock).atOffset(ZoneOffset.UTC))
            .param("client", clientName?.take(200))
            .param("user", user.userId)
            .param("token", user.tokenId)
            .param("tool", tool)
            .param("project", projectKey?.take(32))
            .param("args", json.writeValueAsString(args.filterValues { it != null }))
            .update()
    }

    fun recent(projectKey: String? = null, userId: UUID? = null, limit: Int = 20): List<AccessLogEntry> = jdbc.sql(
        """
            select top (:limit) l.occurred_at, l.client_name, u.display_name, t.name as token_name, l.tool, l.project_key, l.args_json
            from mcp_access_log l
                     join app_user u on u.id = l.user_id
                     left join access_token t on t.id = l.token_id
            where (:projectKey is null or l.project_key = :projectKey)
              and (:userId is null or l.user_id = :userId)
            order by l.occurred_at desc
            """,
    ).param("limit", limit).param("projectKey", projectKey).param("userId", userId)
        .query { rs, _ ->
            AccessLogEntry(
                rs.getObject("occurred_at", java.time.OffsetDateTime::class.java).toInstant(),
                rs.getString("client_name"),
                rs.getString("display_name"),
                rs.getString("token_name"),
                rs.getString("tool"),
                rs.getString("project_key"),
                rs.getString("args_json"),
            )
        }.list()
}
