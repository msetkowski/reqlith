package io.reqlith.config

import io.reqlith.config.ReqlithProperties.IdpType

/** The endpoints Reqlith uses at the identity provider. */
data class IdpEndpoints(
    val authorization: String,
    val token: String,
    val jwks: String,
    /** Null: names and e-mail come from the ID token only. */
    val userInfo: String?,
    /** Null: signing out ends the Reqlith session only. */
    val endSession: String?,
    /** Scope of the client credentials token for the user directory. */
    val directoryScope: String?,
) {
    companion object {
        fun of(idp: ReqlithProperties.Idp): IdpEndpoints {
            val derived = when (idp.type) {
                IdpType.KEYCLOAK -> {
                    val browser = idp.browserUrl.trimEnd('/') + "/protocol/openid-connect"
                    val internal = idp.internalUrl.trimEnd('/') + "/protocol/openid-connect"
                    IdpEndpoints("$browser/auth", "$internal/token", "$internal/certs", "$internal/userinfo", "$browser/logout", "kc-admin-api")
                }

                IdpType.ENTRA -> {
                    // https://login.microsoftonline.com/<tenant>/v2.0 -> https://login.microsoftonline.com/<tenant>
                    val tenant = idp.issuer.trimEnd('/').removeSuffix("/v2.0")
                    require(tenant.startsWith("https://")) { "REQLITH_IDP_ISSUER must be https://login.microsoftonline.com/<tenant-id>/v2.0 for Entra" }
                    IdpEndpoints(
                        "$tenant/oauth2/v2.0/authorize",
                        "$tenant/oauth2/v2.0/token",
                        "$tenant/discovery/v2.0/keys",
                        userInfo = null,
                        endSession = "$tenant/oauth2/v2.0/logout",
                        directoryScope = "https://graph.microsoft.com/.default",
                    )
                }

                IdpType.GENERIC -> {
                    val missing = listOf(
                        "REQLITH_IDP_AUTHORIZATION_URI" to idp.authorizationUri,
                        "REQLITH_IDP_TOKEN_URI" to idp.tokenUri,
                        "REQLITH_IDP_JWKS_URI" to idp.jwksUri,
                    ).filter { it.second.isNullOrBlank() }.map { it.first }
                    check(missing.isEmpty()) { "REQLITH_IDP_TYPE=GENERIC needs ${missing.joinToString()}" }
                    IdpEndpoints("", "", "", null, null, null)
                }
            }
            return IdpEndpoints(
                authorization = idp.authorizationUri.orNull() ?: derived.authorization,
                token = idp.tokenUri.orNull() ?: derived.token,
                jwks = idp.jwksUri.orNull() ?: derived.jwks,
                userInfo = idp.userInfoUri.orNull() ?: derived.userInfo,
                endSession = idp.endSessionUri.orNull() ?: derived.endSession,
                directoryScope = derived.directoryScope,
            )
        }

        private fun String?.orNull() = this?.takeIf { it.isNotBlank() }
    }
}
