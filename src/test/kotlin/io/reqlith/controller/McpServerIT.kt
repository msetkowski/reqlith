package io.reqlith.controller

import io.modelcontextprotocol.client.McpClient
import io.modelcontextprotocol.client.McpSyncClient
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport
import io.modelcontextprotocol.spec.McpSchema
import io.reqlith.config.McpReadOnlyDatabase
import io.reqlith.model.dto.ElementContent
import io.reqlith.model.entity.Decision
import io.reqlith.model.entity.ElementType
import io.reqlith.model.entity.LinkKind
import io.reqlith.model.entity.MemberRole
import io.reqlith.model.entity.TraceLink
import io.reqlith.repository.McpAccessLog
import io.reqlith.service.AccessTokenService
import io.reqlith.service.ElementService
import io.reqlith.service.ProjectService
import io.reqlith.service.ReviewService
import io.reqlith.support.Fixtures
import io.reqlith.support.MsSqlContainerConfig
import io.reqlith.support.TestUsers
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Import
import org.springframework.dao.DataAccessException
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.test.context.ActiveProfiles

/** The MCP server over real HTTP, used by the MCP Java SDK client like an agent would. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(MsSqlContainerConfig::class, TestUsers::class, Fixtures::class)
class McpServerIT(
    @LocalServerPort private val port: Int,
    @Autowired private val users: TestUsers,
    @Autowired private val fixtures: Fixtures,
    @Autowired private val projects: ProjectService,
    @Autowired private val elements: ElementService,
    @Autowired private val reviews: ReviewService,
    @Autowired private val tokens: AccessTokenService,
    @Autowired private val accessLog: McpAccessLog,
    @Autowired private val readOnly: McpReadOnlyDatabase,
    @Autowired private val jdbc: JdbcClient,
) {
    private val clients = mutableListOf<McpSyncClient>()

    @AfterEach
    fun closeClients() = clients.forEach { it.closeGracefully() }

    private fun client(token: String): McpSyncClient {
        val transport = HttpClientStreamableHttpTransport.builder("http://localhost:$port")
            .endpoint("/mcp")
            .requestBuilder(HttpRequest.newBuilder().header("Authorization", "Bearer $token"))
            .build()
        return McpClient.sync(transport)
            .clientInfo(McpSchema.Implementation("backlog-agent", "1.0"))
            .requestTimeout(Duration.ofSeconds(30))
            .build()
            .also { it.initialize(); clients += it }
    }

    private fun McpSyncClient.call(tool: String, vararg args: Pair<String, Any>): McpSchema.CallToolResult =
        callTool(McpSchema.CallToolRequest(tool, mapOf(*args)))

    private fun McpSchema.CallToolResult.text(): String = content().filterIsInstance<McpSchema.TextContent>().joinToString("\n") { it.text() }

    /** v1 approved (BR-01, FR-01 "14 days"), then FR-01 changed to "30 days" in the draft. */
    private fun approvedProject(): Pair<String, String> {
        val owner = users.create("Olive Owens")
        val reviewer = users.create("Rita Reyes")
        val project = fixtures.project(owner)
        fixtures.addMember(project, reviewer, MemberRole.REVIEWER)
        elements.create(owner, project, ElementType.BR, ElementContent("Fewer contacts", "Goal", null, setOf("kpi"), emptySet()))
        val fr = elements.create(owner, project, ElementType.FR,
            ElementContent("Request refund", "Refund within 14 days", null, setOf("ux"), setOf(TraceLink(LinkKind.DERIVED_FROM, "BR-01"))))
        reviews.submit(owner, project, listOf(reviewer.userId))
        reviews.decide(reviewer, projects.get(project.key), Decision.ACCEPT, null)
        elements.update(owner, projects.get(project.key), fr.key,
            ElementContent("Request refund", "Refund within 30 days", null, setOf("ux"), setOf(TraceLink(LinkKind.DERIVED_FROM, "BR-01"))),
            elements.get(projects.get(project.key), fr.key).rowVersion)
        return project.key to tokens.create(owner, "Backlog agent").value
    }

    @Test
    fun `agents read approved content by default and the draft only on request`() {
        val (key, token) = approvedProject()
        val mcp = client(token)

        assertEquals(
            setOf("list_projects", "get_prd", "search_requirements", "get_requirement", "get_traceability", "list_versions",
                "diff_versions", "get_review_status", "get_external_links", "list_roles"),
            mcp.listTools().tools().map { it.name() }.toSet(),
        )
        assertTrue(mcp.listTools().tools().all { it.annotations()?.readOnlyHint() == true })

        val prd = mcp.call("get_prd", "project" to key).text()
        assertContains(prd, "Refund within 14 days")
        assertFalse(prd.contains("30 days"))

        val draft = mcp.call("get_requirement", "project" to key, "key" to "FR-01", "version" to "draft").text()
        assertContains(draft, "Refund within 30 days")

        val hits = mcp.call("search_requirements", "tag" to "ux").text()
        assertContains(hits, key)
        assertContains(hits, "FR-01")

        val diff = mcp.call("diff_versions", "project" to key, "from" to "1", "to" to "draft").text()
        assertContains(diff, "-Refund within 14 days")
        assertContains(diff, "+Refund within 30 days")

        val trace = mcp.call("get_traceability", "project" to key).text()
        assertContains(trace, "\"functionalWithoutScenario\":[\"FR-01\"]")

        assertContains(mcp.call("list_versions", "project" to key).text(), "\"version\":\"v1\"")
        assertContains(mcp.call("get_review_status", "project" to key).text(), "draft after v1")

        val unknown = mcp.call("get_requirement", "project" to key, "key" to "FR-99")
        assertTrue(unknown.isError)

        val log = accessLog.recent(projectKey = key)
        assertTrue(log.any { it.tool == "get_prd" && it.clientName == "backlog-agent" && it.tokenName == "Backlog agent" }, log.toString())
    }

    @Test
    fun `requests without a valid token are rejected with a pointer to the resource metadata`() {
        val (_, token) = approvedProject()
        val http = HttpClient.newHttpClient()
        fun post(auth: String?) = http.send(
            HttpRequest.newBuilder(URI("http://localhost:$port/mcp"))
                .header("Content-Type", "application/json").header("Accept", "application/json, text/event-stream")
                .apply { auth?.let { header("Authorization", it) } }
                .POST(HttpRequest.BodyPublishers.ofString("{}")).build(),
            HttpResponse.BodyHandlers.ofString(),
        )
        val anonymous = post(null)
        assertEquals(401, anonymous.statusCode())
        assertContains(anonymous.headers().firstValue("WWW-Authenticate").orElse(""), "/.well-known/oauth-protected-resource")
        assertEquals(401, post("Bearer rql_not-a-real-token").statusCode())

        val owner = users.create("Oscar Ortiz")
        val revoked = tokens.create(owner, "old")
        tokens.revoke(owner, revoked.token.id)
        assertEquals(401, post("Bearer ${revoked.value}").statusCode())
        assertEquals(401, post("Bearer $token-tampered").statusCode())

        val metadata = http.send(HttpRequest.newBuilder(URI("http://localhost:$port/.well-known/oauth-protected-resource")).build(), HttpResponse.BodyHandlers.ofString())
        assertEquals(200, metadata.statusCode())
        assertContains(metadata.body(), "authorization_servers")
    }

    @Test
    fun `the MCP database connection cannot write`() {
        assertTrue(readOnly.jdbc.sql("select count(*) from project").query(Int::class.java).single() >= 0)
        assertFailsWith<DataAccessException> { readOnly.jdbc.sql("update project set title = title").update() }
        assertFailsWith<DataAccessException> { readOnly.jdbc.sql("delete from mcp_access_log").update() }
        assertEquals("reqlith_mcp", readOnly.jdbc.sql("select user_name()").query(String::class.java).single())
        assertTrue(jdbc.sql("select count(*) from project").query(Int::class.java).single() >= 0)
    }
}
