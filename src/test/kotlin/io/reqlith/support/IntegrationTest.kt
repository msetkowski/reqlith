package io.reqlith.support

import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.testcontainers.mssqlserver.MSSQLServerContainer
import java.time.Duration

/** Full application context against a real SQL Server (Testcontainers), shared by all integration tests. */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@SpringBootTest
@ActiveProfiles("test")
@Import(MsSqlContainerConfig::class, TestUsers::class, Fixtures::class)
annotation class IntegrationTest

/**
 * One SQL Server per test JVM. It is stopped when the Spring context closes, and Ryuk removes it if the JVM
 * dies. The label lets `src/main/dev/scripts/test.sh` sweep up anything that still survives.
 */
@TestConfiguration(proxyBeanMethods = false)
class MsSqlContainerConfig {
    @Bean
    @ServiceConnection
    fun msSqlServer(): MSSQLServerContainer =
        MSSQLServerContainer("mcr.microsoft.com/mssql/server:2022-latest")
            .acceptLicense()
            .withLabel(TEST_CONTAINER_LABEL, "true")
            .withStartupTimeout(Duration.ofMinutes(3))

    companion object {
        const val TEST_CONTAINER_LABEL = "io.reqlith.test"
    }
}
