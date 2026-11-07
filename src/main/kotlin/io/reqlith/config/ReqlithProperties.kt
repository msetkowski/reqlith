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
) {
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
    )
}
