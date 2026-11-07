package io.reqlith.support

import java.time.Duration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistrar
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.mssqlserver.MSSQLServerContainer
import org.testcontainers.utility.DockerImageName

/** Full application context against a real SQL Server (Testcontainers), shared by all integration tests. */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@SpringBootTest
@ActiveProfiles("test")
@Import(MsSqlContainerConfig::class, TestUsers::class, Fixtures::class)
annotation class IntegrationTest

/**
 * One SQL Server for the whole test JVM, shared by every Spring test context (the MCP tests run on a real
 * port and therefore get their own context). It is never stopped by a context: Ryuk removes it when the
 * JVM exits, also after a crash, and `src/main/dev/scripts/test.sh` sweeps up anything left (label below).
 */
@TestConfiguration(proxyBeanMethods = false)
class MsSqlContainerConfig {
    @Bean(destroyMethod = "")
    @ServiceConnection
    fun msSqlServer(): MSSQLServerContainer = shared

    @Bean
    fun mcpDatabaseProperties() = DynamicPropertyRegistrar { registry ->
        registry.add("reqlith.mcp.datasource.url", shared::getJdbcUrl)
        registry.add("reqlith.mcp.datasource.username") { "reqlith_mcp" }
        registry.add("reqlith.mcp.datasource.password") { "Reqlith_test_mcp_1" }
        registry.add("reqlith.pdf.url") { "http://${pdfRenderer.host}:${pdfRenderer.getMappedPort(3000)}" }
    }

    companion object {
        const val TEST_CONTAINER_LABEL = "io.reqlith.test"

        private val shared: MSSQLServerContainer by lazy {
            MSSQLServerContainer("mcr.microsoft.com/mssql/server:2022-latest")
                .acceptLicense()
                .withLabel(TEST_CONTAINER_LABEL, "true")
                .withStartupTimeout(Duration.ofMinutes(3))
                // Login for the read-only MCP connection; Flyway (V4) maps it to a db_datareader user.
                .withInitScript("db/mcp-login.sql")
                .apply { start() }
        }

        /** The PDF rendering service, configured like in production (no network for rendered pages). */
        private val pdfRenderer: PdfRendererContainer by lazy {
            PdfRendererContainer()
                .withLabel(TEST_CONTAINER_LABEL, "true")
                .apply { start() }
        }
    }
}

class PdfRendererContainer : GenericContainer<PdfRendererContainer>(DockerImageName.parse(IMAGE)) {
    init {
        withExposedPorts(3000)
        withCommand("gotenberg", "--chromium-allow-list=^file:///tmp/.*", "--api-timeout=90s")
        waitingFor(Wait.forHttp("/health").forPort(3000).withStartupTimeout(Duration.ofMinutes(3)))
    }

    companion object {
        const val IMAGE = "docker.io/gotenberg/gotenberg:8.40.0"
    }
}
