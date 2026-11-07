package io.reqlith.config

import org.springframework.boot.EnvironmentPostProcessor
import org.springframework.boot.SpringApplication
import org.springframework.core.Ordered
import org.springframework.core.env.ConfigurableEnvironment

/**
 * Stops the start when environment-specific settings or secrets are missing, listing all of them at once.
 *
 * The default configuration has no development defaults for these values (application.yaml); they come from
 * the environment, or from the `local` profile for development. Runs right after the configuration files are
 * loaded, before any bean is created.
 */
class RequiredSettings : EnvironmentPostProcessor, Ordered {

    override fun getOrder(): Int = Ordered.LOWEST_PRECEDENCE

    override fun postProcessEnvironment(environment: ConfigurableEnvironment, application: SpringApplication) {
        val missing = REQUIRED.filter { (property, _) -> !isSet(environment, property) }
        if (missing.isEmpty()) return
        throw IllegalStateException(
            buildString {
                appendLine("Reqlith cannot start: required settings are missing.")
                missing.forEach { (property, variable) -> appendLine("  - $variable ($property)") }
                append("Set these environment variables, or start with the 'local' profile for local development ")
                append("(SPRING_PROFILES_ACTIVE=local).")
            },
        )
    }

    private fun isSet(environment: ConfigurableEnvironment, property: String): Boolean =
        try {
            !environment.getProperty(property).isNullOrBlank()
        } catch (_: IllegalArgumentException) {
            false // an unresolvable ${PLACEHOLDER}
        }

    companion object {
        /** Property -> environment variable that sets it. */
        val REQUIRED = listOf(
            "spring.datasource.url" to "REQLITH_DB_URL",
            "spring.datasource.password" to "REQLITH_DB_PASSWORD",
            "reqlith.mcp.datasource.password" to "REQLITH_MCP_DB_PASSWORD",
            "spring.security.oauth2.client.registration.idp.client-secret" to "REQLITH_IDP_CLIENT_SECRET",
            "reqlith.idp.issuer" to "REQLITH_IDP_ISSUER",
            "reqlith.base-url" to "REQLITH_BASE_URL",
            "reqlith.pdf.url" to "REQLITH_PDF_URL",
        )
    }
}
