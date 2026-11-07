package io.reqlith.controller

import io.reqlith.model.dto.ElementContent
import io.reqlith.model.dto.ReqlithOidcUser
import io.reqlith.model.entity.ElementType
import io.reqlith.model.entity.LinkKind
import io.reqlith.model.entity.MemberRole
import io.reqlith.model.entity.Priority
import io.reqlith.model.entity.TraceLink
import io.reqlith.repository.WorkspaceRepository
import io.reqlith.service.ElementService
import io.reqlith.service.ProjectService
import io.reqlith.service.RoleService
import io.reqlith.support.Fixtures
import io.reqlith.support.IntegrationTest
import io.reqlith.support.TestUsers
import java.util.UUID
import kotlin.test.assertEquals
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.not
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post

/** Renders every M2 page for real (templates included) and exercises the main form posts. */
@IntegrationTest
@AutoConfigureMockMvc
class ProjectPagesIT(
    @Autowired private val mvc: MockMvc,
    @Autowired private val users: TestUsers,
    @Autowired private val fixtures: Fixtures,
    @Autowired private val projects: ProjectService,
    @Autowired private val elements: ElementService,
    @Autowired private val roles: RoleService,
    @Autowired private val workspaces: WorkspaceRepository,
) {
    private fun login(user: ReqlithOidcUser) = oidcLogin().oidcUser(user)

    @Test
    fun `every project page renders for owners and readers`() {
        val owner = users.create("Olive Owens", admin = true)
        val reader = users.create("Vera Voss")
        val project = fixtures.project(owner)
        val sr = elements.create(owner, project, ElementType.SR, ElementContent("Status visible", "Body", null, emptySet(), emptySet()))
        val fr = elements.create(
            owner, project, ElementType.FR,
            ElementContent(
                "Request a refund", "## Flow\n```mermaid\nflowchart LR\n  A --> B\n```\nSee `${sr.key}`",
                Priority.MUST, setOf("ux"), setOf(TraceLink(LinkKind.DERIVED_FROM, sr.key), TraceLink(LinkKind.VERIFIED_BY, "SC-09")),
            ),
        )
        roles.createAndAdd(owner, projects.get(project.key), "Customer " + UUID.randomUUID().toString().take(6), "Buys", "Refunds")
        val key = project.key

        val pages = listOf(
            "/", "/projects/new", "/p/$key/draft", "/p/$key/draft?type=FR", "/p/$key/draft/document",
            "/p/$key/e/${fr.key}", "/p/$key/e/${fr.key}/edit", "/p/$key/elements/new?type=SC", "/p/$key/details",
            "/p/$key/basic-info/edit", "/p/$key/people", "/p/$key/roles", "/roles", "/workspaces",
        )
        pages.forEach { page ->
            mvc.get(page) { with(login(owner)) }.andExpect { status { isOk() } }
        }
        mvc.get("/p/$key/e/${fr.key}") { with(login(owner)) }.andExpect {
            content { string(containsString("""<pre class="mermaid">""")) }
            content { string(containsString("""class="ref" rel="noopener noreferrer"><code>${sr.key}</code>""")) }
            content { string(containsString("SC-09 (missing)")) }
        }
        mvc.get("/p/$key/draft") { with(login(reader)) }.andExpect {
            status { isOk() }
            content { string(not(containsString("New element"))) }
        }
        mvc.get("/p/$key/e/${fr.key}") { with(login(reader)) }.andExpect {
            content { string(not(containsString("/edit\""))) }
        }
    }

    @Test
    fun `create a project, add an element and preview markdown through the UI`() {
        val owner = users.create("Olive Owens")
        val general = workspaces.findAll().first { it.key == "GEN" }

        val result = mvc.post("/projects") {
            with(login(owner)); with(csrf())
            param("workspaceId", general.id.toString())
            param("title", "Loyalty points ledger")
            param("tags", "loyalty, #Q1-2027")
            param("inviteEmails", "invitee@reqlith.test", "")
            param("inviteRoles", "REVIEWER", "CONTRIBUTOR")
        }.andExpect { status { is3xxRedirection() } }.andReturn()
        val key = result.response.redirectedUrl!!.removePrefix("/p/").removeSuffix("/draft")
        assertEquals(setOf("loyalty", "Q1-2027"), projects.get(key).tags.toSet())

        mvc.post("/p/$key/elements") {
            with(login(owner)); with(csrf())
            param("type", "BR")
            param("title", "Fewer support contacts")
            param("bodyMd", "Reduce contacts by **30%**")
            param("priority", "MUST")
            param("linkKinds", "DERIVED_FROM")
            param("linkTargets", "")
        }.andExpect { redirectedUrl("/p/$key/e/BR-01") }

        mvc.post("/p/$key/preview") {
            with(login(owner)); with(csrf())
            param("bodyMd", "See `BR-01`\n\n<script>x</script>")
        }.andExpect {
            content { string(containsString("""href="/p/$key/e/BR-01"""")) }
            content { string(not(containsString("<script>"))) }
        }

        mvc.post("/p/$key/elements") {
            with(login(owner)); with(csrf())
            param("type", "BR")
            param("title", "")
        }.andExpect {
            status { isOk() }
            content { string(containsString("Title is required")) }
        }
    }

    @Test
    fun `readers cannot change a project`() {
        val owner = users.create("Olive Owens")
        val reader = users.create("Vera Voss")
        val reviewer = users.create("Rita Reyes")
        val project = fixtures.project(owner)
        fixtures.addMember(project, reviewer, MemberRole.REVIEWER)

        listOf(reader, reviewer).forEach { user ->
            mvc.post("/p/${project.key}/elements") {
                with(login(user)); with(csrf())
                param("type", "BR"); param("title", "Sneaky")
            }.andExpect { status { isForbidden() } }
        }
    }
}
