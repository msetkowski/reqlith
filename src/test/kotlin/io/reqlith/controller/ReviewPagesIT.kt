package io.reqlith.controller

import io.reqlith.model.dto.ElementContent
import io.reqlith.model.dto.ReqlithOidcUser
import io.reqlith.model.entity.Decision
import io.reqlith.model.entity.ElementType
import io.reqlith.model.entity.MemberRole
import io.reqlith.service.ElementService
import io.reqlith.service.ProjectService
import io.reqlith.service.ReviewService
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

@IntegrationTest
@AutoConfigureMockMvc
class ReviewPagesIT(
    @Autowired private val mvc: MockMvc,
    @Autowired private val users: TestUsers,
    @Autowired private val fixtures: Fixtures,
    @Autowired private val projects: ProjectService,
    @Autowired private val elements: ElementService,
    @Autowired private val reviews: ReviewService,
) {
    private fun login(user: ReqlithOidcUser) = oidcLogin().oidcUser(user)

    @Test
    fun `review flow through the UI, from submit to approved version pages`() {
        val owner = users.create("Olive Owens")
        val reviewer = users.create("Rita Reyes")
        val project = fixtures.project(owner)
        val key = project.key
        fixtures.addMember(project, reviewer, MemberRole.REVIEWER)
        elements.create(
            owner,
            project,
            ElementType.FR,
            ElementContent("Request refund", "See `FR-01`\n```mermaid\nflowchart LR\n A-->B\n```", null, emptySet(), emptySet()),
        )

        mvc.get("/p/$key/review") { with(login(owner)) }.andExpect {
            status { isOk() }
            content { string(containsString("Submit v1 for review")) }
        }
        mvc.post("/p/$key/review/submit") {
            with(login(owner))
            with(csrf())
            param("reviewers", reviewer.userId.toString())
        }.andExpect { redirectedUrl("/p/$key/review") }

        mvc.get("/") { with(login(reviewer)) }.andExpect {
            content { string(containsString("Review requested")) }
            content { string(containsString("v1-rc1")) }
        }
        mvc.get("/p/$key/review") { with(login(reviewer)) }.andExpect {
            status { isOk() }
            content { string(containsString("Accept version")) }
            content { string(containsString("""href="#FR-01"""")) }
        }
        mvc.get("/p/$key/review") { with(login(owner)) }.andExpect {
            content { string(not(containsString("Accept version"))) }
            content { string(containsString("Withdraw review")) }
        }
        mvc.get("/p/$key/e/FR-01/edit") { with(login(owner)) }.andExpect { status { isConflict() } }

        mvc.post("/p/$key/review/suggest") {
            with(login(reviewer))
            with(csrf())
            param("elementKey", "FR-01")
            param("proposedMd", "Better")
            param("comment", "Clarify")
        }.andExpect { redirectedUrl("/p/$key/review#FR-01") }
        mvc.post("/p/$key/review/decide") {
            with(login(reviewer))
            with(csrf())
            param("decision", "ACCEPT")
        }.andExpect { redirectedUrl("/p/$key/review") }

        mvc.get("/p/$key") { with(login(owner)) }.andExpect {
            status { isOk() }
            content { string(containsString("v1 · Approved")) }
            content { string(containsString("never changes")) }
        }
        mvc.get("/p/$key/v/1-rc1") { with(login(owner)) }.andExpect { status { isOk() } }
        mvc.get("/p/$key/v/9") { with(login(owner)) }.andExpect { status { isNotFound() } }
        mvc.get("/p/$key/draft") { with(login(owner)) }.andExpect {
            content { string(containsString("Approved v1")) }
        }
        mvc.get("/p/$key/review") { with(login(owner)) }.andExpect {
            content { string(containsString("Feedback to resolve")) }
            content { string(containsString("Nothing changed since v")) }
        }
        mvc.get("/p/$key/e/FR-01/edit") { with(login(owner)) }.andExpect {
            content { string(containsString("Review suggestions")) }
        }
    }

    @Test
    fun `decision errors come back as a message`() {
        val owner = users.create("Olive Owens")
        val reviewer = users.create("Rita Reyes")
        val project = fixtures.project(owner)
        fixtures.addMember(project, reviewer, MemberRole.REVIEWER)
        reviews.submit(owner, project, listOf(reviewer.userId))

        mvc.post("/p/${project.key}/review/decide") {
            with(login(reviewer))
            with(csrf())
            param("decision", "REJECT")
        }.andExpect { flash { attribute("flashError", "Explain why you reject this version") } }
        reviews.decide(reviewer, projects.get(project.key), Decision.CHANGES, "Needs scenarios")
        mvc.get("/") { with(login(owner)) }.andExpect { content { string(containsString("Changes requested")) } }
    }
}
