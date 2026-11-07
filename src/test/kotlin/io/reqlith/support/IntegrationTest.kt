package io.reqlith.support

import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.testcontainers.mssqlserver.MSSQLServerContainer

/** Full application context against a real SQL Server (Testcontainers), shared by all integration tests. */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@SpringBootTest
@ActiveProfiles("test")
@Import(MsSqlContainerConfig::class, TestUsers::class, Fixtures::class)
annotation class IntegrationTest

@TestConfiguration(proxyBeanMethods = false)
class MsSqlContainerConfig {
    @Bean
    @ServiceConnection
    fun msSqlServer(): MSSQLServerContainer =
        MSSQLServerContainer("mcr.microsoft.com/mssql/server:2022-latest")
            .acceptLicense()
            .withReuse(true)
}
