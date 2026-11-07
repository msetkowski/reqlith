package io.reqlith.controller

import io.reqlith.support.Fixtures
import io.reqlith.support.MsSqlContainerConfig
import io.reqlith.support.TestUsers
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalManagementPort
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles

/**
 * Actuator runs on its own management port: Kubernetes probes and Prometheus scraping work there without
 * authentication, nothing else is exposed, and the application port does not serve actuator at all.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(MsSqlContainerConfig::class, TestUsers::class, Fixtures::class)
class ActuatorIT(
    @LocalServerPort private val port: Int,
    @LocalManagementPort private val managementPort: Int,
) {
    private val http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build()

    private fun get(port: Int, path: String): HttpResponse<String> =
        http.send(HttpRequest.newBuilder(URI("http://localhost:$port$path")).build(), HttpResponse.BodyHandlers.ofString())

    @Test
    fun `liveness and readiness probes are open on the management port and report UP`() {
        assertNotEquals(port, managementPort)
        listOf("/actuator/health", "/actuator/health/liveness", "/actuator/health/readiness").forEach { path ->
            val response = get(managementPort, path)
            assertEquals(200, response.statusCode(), path)
            assertContains(response.body(), "\"status\":\"UP\"")
            assertFalse(response.body().contains("components"), "no details for anonymous callers")
        }
    }

    @Test
    fun `prometheus metrics are open on the management port`() {
        get(managementPort, "/actuator/health")
        val response = get(managementPort, "/actuator/prometheus")
        assertEquals(200, response.statusCode())
        assertContains(response.headers().firstValue("Content-Type").orElse(""), "text/plain")
        assertContains(response.body(), "jvm_memory_used_bytes")
        assertContains(response.body(), "hikaricp_connections")
        assertContains(response.body(), """application="reqlith"""")
    }

    @Test
    fun `other endpoints are not exposed, and the application port serves no actuator`() {
        listOf("/actuator/env", "/actuator/beans", "/actuator/metrics").forEach { path ->
            assertFalse(get(managementPort, path).statusCode() == 200, path)
        }
        listOf("/actuator/health", "/actuator/prometheus").forEach { path ->
            assertNotEquals(200, get(port, path).statusCode(), "$path on the application port")
        }
    }
}
