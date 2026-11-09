package io.reqlith.controller

import io.reqlith.model.dto.ReqlithOidcUser
import io.reqlith.model.entity.BaselineCategory
import io.reqlith.service.BaselineService
import io.reqlith.support.Fixtures
import io.reqlith.support.IntegrationTest
import io.reqlith.support.TestUsers
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
import java.util.UUID
import kotlin.test.assertEquals

@IntegrationTest
@AutoConfigureMockMvc
class BaselinePagesIT(
    @Autowired private val mvc: MockMvc,
    @Autowired private val users: TestUsers,
    @Autowired private val fixtures: Fixtures,
    @Autowired private val baseline: BaselineService,
) {
    private fun login(user: ReqlithOidcUser) = oidcLogin().oidcUser(user)

    @Test
    fun `architects add and edit baseline requirements from the baseline pages`() {
        val architect = users.create("Ada Archer", architect = true)
        val title = "Pages respond within 2 s " + UUID.randomUUID().toString().take(6)

        mvc.get("/") { with(login(architect)) }.andExpect { content { string(containsString("""href="/baseline"""")) } }
        mvc.get("/baseline") { with(login(architect)) }.andExpect {
            status { isOk() }
            content { string(containsString("Add to baseline")) }
        }
        val location = mvc.post("/baseline") {
            with(login(architect))
            with(csrf())
            param("category", "PERFORMANCE")
            param("title", title)
            param("body", "Measured at the 95th percentile")
        }.andExpect { status { is3xxRedirection() } }.andReturn().response.redirectedUrl!!
        val key = location.substringAfterLast('/')

        mvc.get(location) { with(login(architect)) }.andExpect {
            status { isOk() }
            content { string(containsString(title)) }
            content { string(containsString("95th percentile")) }
        }
        mvc.post(location) {
            with(login(architect))
            with(csrf())
            param("category", "SECURITY")
            param("title", "")
        }.andExpect {
            status { isOk() }
            content { string(containsString("Title is required")) }
        }
        mvc.post("$location/delete") {
            with(login(architect))
            with(csrf())
        }.andExpect { status { is3xxRedirection() } }
        assertEquals(null, baseline.all().firstOrNull { it.key == key })
    }

    @Test
    fun `other users read the baseline but cannot change it`() {
        val architect = users.create("Ada Archer", architect = true)
        val owner = users.create("Olive Owens")
        val requirement = baseline.create(architect, BaselineCategory.SECURITY, "Single sign-on " + UUID.randomUUID().toString().take(6), null)

        mvc.get("/baseline") { with(login(owner)) }.andExpect {
            status { isOk() }
            content { string(containsString(requirement.title)) }
            content { string(not(containsString("Add to baseline"))) }
        }
        mvc.get("/baseline/${requirement.key}") { with(login(owner)) }.andExpect {
            status { isOk() }
            content { string(not(containsString("Remove ${requirement.key}"))) }
        }
        mvc.post("/baseline") {
            with(login(owner))
            with(csrf())
            param("category", "OTHER")
            param("title", "Sneaky")
        }.andExpect { status { isForbidden() } }
        mvc.post("/baseline/${requirement.key}/delete") {
            with(login(owner))
            with(csrf())
        }.andExpect { status { isForbidden() } }
    }

    @Test
    fun `every project shows the baseline as read-only inherited items`() {
        val architect = users.create("Ada Archer", architect = true)
        val owner = users.create("Olive Owens")
        val project = fixtures.project(owner)
        val requirement = baseline.create(architect, BaselineCategory.AVAILABILITY, "99.9% uptime " + UUID.randomUUID().toString().take(6), "Monthly")

        listOf("/p/${project.key}/draft", "/p/${project.key}/draft?type=NFR", "/p/${project.key}/draft/document").forEach { page ->
            mvc.get(page) { with(login(owner)) }.andExpect {
                status { isOk() }
                content { string(containsString(requirement.title)) }
                content { string(containsString("/baseline/${requirement.key}")) }
                content { string(containsString("Baseline NFRs")) }
            }
        }
        mvc.get("/p/${project.key}/draft?type=FR") { with(login(owner)) }.andExpect {
            content { string(not(containsString(requirement.title))) }
        }
        mvc.get("/p/${project.key}/draft/prd.md") { with(login(owner)) }.andExpect {
            content { string(containsString("#### ${requirement.key} · ${requirement.title}")) }
        }
    }
}
