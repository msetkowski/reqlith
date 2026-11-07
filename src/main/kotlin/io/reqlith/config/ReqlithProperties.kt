package io.reqlith.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("reqlith")
data class ReqlithProperties(
    /** Public base URL used for copyable links and the MCP client configuration. */
    val baseUrl: String = "http://localhost:8080",
    /** ID token claim carrying the IDP roles. */
    val rolesClaim: String = "roles",
    /** IDP role that grants workspace management. */
    val adminRole: String = "reqlith-admin",
    val idp: Idp,
    val mcp: Mcp = Mcp(),
    val pdf: Pdf = Pdf(),
) {
    /** The separate PDF rendering service (Gotenberg). */
    data class Pdf(
        val url: String = "http://localhost:3000",
        val timeout: java.time.Duration = java.time.Duration.ofSeconds(90),
    )

    data class Mcp(val datasource: Datasource = Datasource()) {
        data class Datasource(val url: String? = null, val username: String = "reqlith_mcp", val password: String = "")
    }

    data class Idp(
        /** Issuer as it appears in tokens; also where browsers are sent. */
        val issuer: String,
        val browserUrl: String = issuer,
        /** How the server reaches the IDP; differs from [issuer] inside containers. */
        val internalUrl: String = issuer,
        val endSessionPath: String = "/protocol/openid-connect/logout",
        /** Where invitation suggestions look up people besides users who signed in before. */
        val directory: DirectoryType = DirectoryType.NONE,
    )

    enum class DirectoryType {
        /** Only users who have signed in to Reqlith at least once. */
        NONE,

        /** Also the enabled users of the Keycloak realm (admin API, client credentials with view-users). */
        KEYCLOAK,
    }
}
