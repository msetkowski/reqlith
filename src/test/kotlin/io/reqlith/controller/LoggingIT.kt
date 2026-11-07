package io.reqlith.controller

import io.reqlith.repository.WorkspaceRepository
import io.reqlith.support.IntegrationTest
import io.reqlith.support.TestUsers
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.test.system.CapturedOutput
import org.springframework.boot.test.system.OutputCaptureExtension
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin
import org.springframework.stereotype.Controller
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.web.bind.annotation.GetMapping
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Business actions at INFO with request id and user, defects at ERROR with an id the user can report. */
@IntegrationTest
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension::class)
@Import(LoggingIT.FailingController::class)
class LoggingIT(@Autowired private val mvc: MockMvc, @Autowired private val users: TestUsers, @Autowired private val workspaces: WorkspaceRepository) {
    @TestConfiguration
    @Controller
    class FailingController {
        @GetMapping("/test/boom")
        fun boom(): String = throw IllegalStateException("simulated defect")
    }

    @Test
    fun `business actions are logged at INFO with request id and user`(output: CapturedOutput) {
        val owner = users.create("Olive Owens")
        val general = workspaces.findAll().first { it.key == "GEN" }

        val result = mvc.post("/projects") {
            with(oidcLogin().oidcUser(owner))
            with(csrf())
            header("X-Request-Id", "req-4711")
            param("workspaceId", general.id.toString())
            param("title", "Logged project")
        }.andExpect { header { string("X-Request-Id", "req-4711") } }.andReturn()

        val key = result.response.redirectedUrl!!.removePrefix("/p/").removeSuffix("/draft")
        val line = output.out.lines().first { it.contains("Project created project=$key") }
        assertContains(line, "INFO")
        assertContains(line, "[req-4711 ${owner.userId}]")
    }

    @Test
    fun `unexpected errors are logged at ERROR and the page shows the error id`(output: CapturedOutput) {
        val user = users.create("Vera Voss")
        val page = mvc.get("/test/boom") { with(oidcLogin().oidcUser(user)) }
            .andExpect { status { isInternalServerError() } }
            .andReturn().response.contentAsString

        val errorId = Regex("error id ([0-9a-f]{8})").find(page)?.groupValues?.get(1)
        assertTrue(errorId != null, page)
        val line = output.out.lines().first { it.contains("Unexpected error id=$errorId") }
        assertContains(line, "ERROR")
        assertContains(output.out, "java.lang.IllegalStateException: simulated defect")
        assertFalse(page.contains("simulated defect"), "internal details are not shown to users")
    }

    @Test
    fun `expected problems are not logged as errors`(output: CapturedOutput) {
        val user = users.create("Vera Voss")
        mvc.get("/p/PRJ-NOPE-999/draft") { with(oidcLogin().oidcUser(user)) }.andExpect { status { isNotFound() } }
        assertFalse(output.out.lines().any { it.contains("ERROR") && it.contains("PRJ-NOPE-999") })
    }
}
