package io.reqlith.web

import io.reqlith.element.ElementContent
import io.reqlith.element.ElementService
import io.reqlith.element.ElementType
import io.reqlith.element.Priority
import io.reqlith.project.MemberRole
import io.reqlith.project.ProjectService
import io.reqlith.review.Decision
import io.reqlith.review.ReviewService
import io.reqlith.support.Fixtures
import io.reqlith.support.IntegrationTest
import io.reqlith.support.TestUsers
import io.reqlith.user.ReqlithOidcUser
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.not
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Compare, export and external links, on a project with an approved v1 and changes since. */
@IntegrationTest
@AutoConfigureMockMvc
class ExportPagesIT(
    @Autowired private val mvc: MockMvc,
    @Autowired private val users: TestUsers,
    @Autowired private val fixtures: Fixtures,
    @Autowired private val projects: ProjectService,
    @Autowired private val elements: ElementService,
    @Autowired private val reviews: ReviewService,
    @Autowired private val jdbc: JdbcClient,
) {
    private fun login(user: ReqlithOidcUser) = oidcLogin().oidcUser(user)

    private fun approvedProjectWithChanges(): Triple<String, ReqlithOidcUser, ReqlithOidcUser> {
        val owner = users.create("Olive Owens")
        val reviewer = users.create("Rita Reyes")
        val project = fixtures.project(owner)
        fixtures.addMember(project, reviewer, MemberRole.REVIEWER)
        val fr = elements.create(
            owner, project, ElementType.FR,
            ElementContent("Request refund", "Refund within 14 days\n\n```mermaid\nflowchart LR\n  A[Order] --> B[Refund]\n```", Priority.MUST, emptySet(), emptySet()),
        )
        elements.create(owner, project, ElementType.BR, ElementContent("Fewer contacts", "Goal", null, emptySet(), emptySet()))
        reviews.submit(owner, project, listOf(reviewer.userId))
        reviews.decide(reviewer, projects.get(project.key), Decision.ACCEPT, null)
        elements.update(owner, projects.get(project.key), fr.key,
            ElementContent("Request a refund", "Refund within 30 days\n\n```mermaid\nflowchart LR\n  A[Order] --> B[Refund]\n```", Priority.MUST, emptySet(), emptySet()),
            elements.get(projects.get(project.key), fr.key).rowVersion)
        elements.delete(owner, projects.get(project.key), "BR-01")
        return Triple(project.key, owner, reviewer)
    }

    @Test
    fun `compare shows word level changes between v1 and the working copy`() {
        val (key, owner, reviewer) = approvedProjectWithChanges()
        mvc.get("/p/$key/diff") { with(login(owner)) }.andExpect {
            status { isOk() }
            content { string(containsString("<del>14</del>")) }
            content { string(containsString("<ins>30</ins>")) }
            content { string(containsString("1 changed, 1 removed")) }
        }
        mvc.get("/p/$key/diff?from=1&to=draft&view=rendered") { with(login(owner)) }.andExpect {
            status { isOk() }
            content { string(containsString("""<pre class="mermaid">""")) }
        }

        // The review of v2 shows what changed in each changed element.
        reviews.submit(owner, projects.get(key), listOf(reviewer.userId))
        mvc.get("/p/$key/review") { with(login(reviewer)) }.andExpect {
            content { string(containsString("What changed")) }
            content { string(containsString("<ins>30</ins>")) }
            content { string(containsString("Removed")) }
        }
    }

    @Test
    fun `markdown export of a version and of the draft`() {
        val (key, owner) = approvedProjectWithChanges()
        mvc.get("/p/$key/export") { with(login(owner)) }.andExpect {
            status { isOk() }
            content { string(containsString("v1 · Approved (recommended)")) }
        }
        val v1 = mvc.get("/p/$key/v/1/prd.md") { with(login(owner)) }.andExpect {
            status { isOk() }
            header { string("Content-Disposition", containsString("PRD-GEN-")) }
        }.andReturn().response.contentAsString
        assertTrue(v1.contains("Refund within 14 days") && v1.contains("BR-01 · Fewer contacts") && !v1.contains("DRAFT"), v1)

        mvc.get("/p/$key/export/download?version=draft&format=md&traceability=true") { with(login(owner)) }.andExpect {
            content { string(containsString("**DRAFT**")) }
            content { string(containsString("Refund within 30 days")) }
            content { string(containsString("Traceability matrix")) }
            content { string(not(containsString("BR-01 · Fewer contacts"))) }
        }
    }

    @Test
    fun `pdf export draws mermaid in chromium and is cached for closed versions`() {
        val (key, owner) = approvedProjectWithChanges()
        val first = mvc.get("/p/$key/v/1/prd.pdf") { with(login(owner)) }.andExpect {
            status { isOk() }
            content { contentType("application/pdf") }
        }.andReturn().response.contentAsByteArray
        assertEquals("%PDF", String(first.copyOfRange(0, 4)))
        val cached = jdbc.sql("select count(*) from export_cache where options_key like '%|v1|APPROVED'").query(Int::class.java).single()
        assertTrue(cached >= 1)
        val second = mvc.get("/p/$key/v/1/prd.pdf") { with(login(owner)) }.andReturn().response.contentAsByteArray
        assertTrue(first.contentEquals(second))
    }

    @Test
    fun `external links are validated and shown where they apply`() {
        val (key, owner, reviewer) = approvedProjectWithChanges()
        mvc.post("/p/$key/links") {
            with(login(owner)); with(csrf())
            param("system", "JIRA"); param("url", "javascript:alert(1)"); param("scope", "project")
        }.andExpect { flash { attributeExists("flashError") } }
        mvc.post("/p/$key/links") {
            with(login(owner)); with(csrf())
            param("system", "ADO"); param("url", "https://dev.azure.com/org/proj/_workitems/edit/48120"); param("label", "Feature 48120"); param("scope", "FR-01")
            param("back", "/p/$key/e/FR-01")
        }.andExpect { redirectedUrl("/p/$key/e/FR-01") }
        mvc.post("/p/$key/links") {
            with(login(owner)); with(csrf())
            param("system", "JIRA"); param("url", "https://jira.example.com/browse/PAY-2291"); param("label", "PAY-2291 epic"); param("scope", "v1")
        }.andExpect { redirectedUrl("/p/$key/export#links") }
        mvc.post("/p/$key/links") {
            with(login(reviewer)); with(csrf())
            param("system", "URL"); param("url", "https://example.com")
        }.andExpect { status { isForbidden() } }

        mvc.get("/p/$key/e/FR-01") { with(login(owner)) }.andExpect { content { string(containsString("Feature 48120")) } }
        mvc.get("/p/$key/draft") { with(login(owner)) }.andExpect {
            content { string(containsString("PAY-2291 epic")) }
            content { string(containsString("Feature 48120")) }
        }
    }
}
