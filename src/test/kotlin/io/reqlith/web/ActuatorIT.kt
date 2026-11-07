package io.reqlith.web

import io.reqlith.support.IntegrationTest
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.not
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

/** Kubernetes probes and Prometheus scraping work without authentication; nothing else does. */
@IntegrationTest
@AutoConfigureMockMvc
class ActuatorIT(@Autowired private val mvc: MockMvc) {

    @Test
    fun `liveness and readiness probes are open and report UP`() {
        listOf("/actuator/health", "/actuator/health/liveness", "/actuator/health/readiness").forEach { path ->
            mvc.get(path).andExpect {
                status { isOk() }
                jsonPath("$.status") { value("UP") }
                jsonPath("$.components") { doesNotExist() }
            }
        }
    }

    @Test
    fun `prometheus metrics are open and in the Prometheus text format`() {
        mvc.get("/actuator/health")
        mvc.get("/actuator/prometheus").andExpect {
            status { isOk() }
            content { contentTypeCompatibleWith("text/plain") }
            content { string(containsString("jvm_memory_used_bytes")) }
            content { string(containsString("hikaricp_connections")) }
            content { string(containsString("""application="reqlith"""")) }
            content { string(not(containsString("<html"))) }
        }
    }

    @Test
    fun `other actuator endpoints are neither exposed nor open`() {
        listOf("/actuator/env", "/actuator/beans", "/actuator/metrics").forEach { path ->
            mvc.get(path).andExpect { status { is3xxRedirection() } }
        }
    }
}
