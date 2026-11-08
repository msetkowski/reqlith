package io.reqlith.controller

import io.reqlith.model.dto.ProjectDetails
import io.reqlith.model.dto.ReqlithOidcUser
import io.reqlith.repository.WorkspaceRepository
import io.reqlith.service.ProjectService
import io.reqlith.service.UserService
import io.reqlith.support.Fixtures
import io.reqlith.support.IntegrationTest
import io.reqlith.support.TestUsers
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.not
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.core.oidc.OidcIdToken
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals

@IntegrationTest
@AutoConfigureMockMvc
class HomePageIT(
    @Autowired private val mvc: MockMvc,
    @Autowired private val userService: UserService,
    @Autowired private val users: TestUsers,
    @Autowired private val fixtures: Fixtures,
    @Autowired private val projects: ProjectService,
    @Autowired private val workspaces: WorkspaceRepository,
) {
    private fun rowCount(html: String) = Regex("class=\"cell-link\"").findAll(html).count()

    @Test
    fun `anonymous users are sent to the IDP`() {
        mvc.get("/").andExpect {
            status { is3xxRedirection() }
            redirectedUrl("/oauth2/authorization/idp")
        }
    }

    @Test
    fun `signed-in user sees the projects home page`() {
        val user = userService.provision("sub-home", "owner1@reqlith.local", "Olive Owens")
        val token = OidcIdToken.withTokenValue("id-token").subject("sub-home").issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(60)).build()
        val authorities = listOf(SimpleGrantedAuthority("ROLE_USER"))
        val principal = ReqlithOidcUser(DefaultOidcUser(authorities, token), authorities, user.id, user.displayName, user.initials)

        mvc.get("/") { with(oidcLogin().oidcUser(principal)) }.andExpect {
            status { isOk() }
            content { string(org.hamcrest.Matchers.containsString("Projects")) }
            content { string(org.hamcrest.Matchers.containsString("OO")) }
        }
    }

    @Test
    fun `the project list shows 20 projects and loads the rest on scroll with the same filters`() {
        val owner = users.create("Olive Owens")
        val token = "paging${UUID.randomUUID().toString().take(8)}"
        repeat(23) { fixtures.project(owner, "$token project $it") }

        val first = mvc.get("/?q=$token") { with(oidcLogin().oidcUser(owner)) }.andExpect {
            status { isOk() }
            content { string(containsString("/projects/rows?page=1")) }
        }.andReturn().response.contentAsString
        assertEquals(20, rowCount(first))

        val rest = mvc.get("/projects/rows?page=1&q=$token") { with(oidcLogin().oidcUser(owner)) }.andExpect {
            status { isOk() }
            content { string(not(containsString("/projects/rows?page="))) }
            content { string(not(containsString("<html"))) }
        }.andReturn().response.contentAsString
        assertEquals(3, rowCount(rest))
    }

    @Test
    fun `search matches tags, which are shown as badges, and treats wildcards literally`() {
        val owner = users.create("Olive Owens")
        val tag = "tag${UUID.randomUUID().toString().take(8)}"
        val general = workspaces.findAll().first { it.key == "GEN" }
        projects.create(owner, general.id, ProjectDetails("Tagged project", null, setOf(tag), emptySet()))

        mvc.get("/?q=$tag") { with(oidcLogin().oidcUser(owner)) }.andExpect {
            content { string(containsString("""<span class="tag">#$tag</span>""")) }
        }
        val none = mvc.get("/?q=%25%25") { with(oidcLogin().oidcUser(owner)) }.andReturn().response.contentAsString
        assertEquals(0, rowCount(none))
    }

    @Test
    fun `the theme choice is stored in a cookie and rendered on every page`() {
        val user = users.create("Olive Owens")
        mvc.post("/account/theme") {
            with(oidcLogin().oidcUser(user))
            with(csrf())
            param("theme", "dark")
            param("back", "/roles")
        }.andExpect {
            redirectedUrl("/roles")
            cookie { value("reqlith-theme", "dark") }
        }
        mvc.get("/") {
            with(oidcLogin().oidcUser(user))
            cookie(jakarta.servlet.http.Cookie("reqlith-theme", "dark"))
        }.andExpect { content { string(containsString("""data-theme="dark"""")) } }

        mvc.post("/account/theme") {
            with(oidcLogin().oidcUser(user))
            with(csrf())
            param("theme", "system")
            param("back", "//evil.example.com")
        }.andExpect {
            redirectedUrl("/")
            cookie { maxAge("reqlith-theme", 0) }
        }
        mvc.get("/") { with(oidcLogin().oidcUser(user)) }.andExpect { content { string(not(containsString("data-theme="))) } }
    }
}
