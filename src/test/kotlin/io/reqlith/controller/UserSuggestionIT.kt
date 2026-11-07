package io.reqlith.controller

import io.reqlith.model.entity.MemberRole
import io.reqlith.support.Fixtures
import io.reqlith.support.IntegrationTest
import io.reqlith.support.TestUsers
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.not
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import java.util.UUID

/** Invitation suggestions from users who signed in before (no IDP directory in tests). */
@IntegrationTest
@AutoConfigureMockMvc
class UserSuggestionIT(
    @Autowired private val mvc: MockMvc,
    @Autowired private val users: TestUsers,
    @Autowired private val fixtures: Fixtures,
) {
    @Test
    fun `typing a name suggests known users who are not yet in the project`() {
        val marker = UUID.randomUUID().toString().take(6)
        val owner = users.create("Olive Owens")
        val member = users.create("Zed $marker Member")
        val candidate = users.create("Zed $marker Candidate")
        val project = fixtures.project(owner)
        fixtures.addMember(project, member, MemberRole.REVIEWER)

        mvc.get("/users/suggest?q=zed $marker&project=${project.key}") { with(oidcLogin().oidcUser(owner)) }.andExpect {
            status { isOk() }
            content { string(containsString("""value="${candidate.emailAddress}"""")) }
            content { string(containsString("Zed $marker Candidate")) }
            content { string(not(containsString(member.emailAddress!!))) }
        }
        mvc.get("/users/suggest?q=z") { with(oidcLogin().oidcUser(owner)) }.andExpect {
            content { string(not(containsString("<option"))) }
        }
        mvc.get("/p/${project.key}/people") { with(oidcLogin().oidcUser(owner)) }.andExpect {
            content { string(containsString("""list="user-suggestions"""")) }
        }
    }
}
