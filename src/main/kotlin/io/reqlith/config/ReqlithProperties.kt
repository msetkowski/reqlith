package io.reqlith.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("reqlith")
data class ReqlithProperties(
    /** Public base URL used for copyable links and the MCP client configuration. */
    val baseUrl: String = "http://localhost:8080",
    val idp: Idp,
    val mcp: Mcp = Mcp(),
    val pdf: Pdf = Pdf(),
) {
    /** The separate PDF rendering service (Gotenberg). */
    data class Pdf(val url: String = "http://localhost:3000", val timeout: java.time.Duration = java.time.Duration.ofSeconds(90))

    data class Mcp(val datasource: Datasource = Datasource()) {
        data class Datasource(val url: String? = null, val username: String = "reqlith_mcp", val password: String = "")
    }

    /**
     * The OAuth 2 / OIDC identity provider. [type] fills in the endpoints from [issuer]; each endpoint can be
     * overridden, and `GENERIC` providers must set authorization, token and JWKS endpoints explicitly.
     */
    data class Idp(
        /** Issuer as it appears in tokens; also where browsers are sent. */
        val issuer: String,
        val type: IdpType = IdpType.KEYCLOAK,
        val browserUrl: String = issuer,
        /** How the server reaches the IDP; differs from [issuer] inside containers (Keycloak). */
        val internalUrl: String = issuer,
        val clientId: String = "reqlith-app",
        val clientSecret: String = "",
        val scopes: List<String> = listOf("openid", "profile", "email"),
        /** Endpoint overrides (full URLs); blank means "derived from [type]". */
        val authorizationUri: String? = null,
        val tokenUri: String? = null,
        val jwksUri: String? = null,
        val userInfoUri: String? = null,
        val endSessionUri: String? = null,
        /** Where invitation suggestions look up people besides users who signed in before. */
        val directory: DirectoryType = DirectoryType.NONE,
        /** Required `aud` of IDP access tokens sent to /mcp (Entra: the API's application ID URI); blank = not checked. */
        val mcpAudience: String? = null,
    )

    enum class IdpType {
        /** Keycloak realm: issuer `https://host/realms/<realm>`. */
        KEYCLOAK,

        /** Microsoft Entra ID: issuer `https://login.microsoftonline.com/<tenant-id>/v2.0`. */
        ENTRA,

        /** Any other OIDC provider: endpoints are configured explicitly. */
        GENERIC,
    }

    enum class DirectoryType {
        /** Only users who have signed in to Reqlith at least once. */
        NONE,

        /** Also the enabled users of the Keycloak realm (admin API, client credentials with view-users). */
        KEYCLOAK,

        /** Also the enabled users of the Entra tenant (Microsoft Graph, application permission User.Read.All). */
        ENTRA,

        /** Only users assigned to the Reqlith enterprise application (adds Application.Read.All). */
        ENTRA_ASSIGNED,
    }
}
