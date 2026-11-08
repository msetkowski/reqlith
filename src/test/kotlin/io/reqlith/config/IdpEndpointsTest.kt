package io.reqlith.config

import io.reqlith.config.ReqlithProperties.Idp
import io.reqlith.config.ReqlithProperties.IdpType
import org.junit.jupiter.api.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class IdpEndpointsTest {

    @Test
    fun `keycloak endpoints use the browser address for redirects and the internal one for server calls`() {
        val e = IdpEndpoints.of(Idp(issuer = "http://localhost:8180/realms/acme", internalUrl = "http://keycloak:8080/realms/acme/"))
        assertEquals("http://localhost:8180/realms/acme/protocol/openid-connect/auth", e.authorization)
        assertEquals("http://keycloak:8080/realms/acme/protocol/openid-connect/token", e.token)
        assertEquals("http://keycloak:8080/realms/acme/protocol/openid-connect/certs", e.jwks)
        assertEquals("http://keycloak:8080/realms/acme/protocol/openid-connect/userinfo", e.userInfo)
        assertEquals("http://localhost:8180/realms/acme/protocol/openid-connect/logout", e.endSession)
        assertEquals("kc-admin-api", e.directoryScope)
    }

    @Test
    fun `entra endpoints are derived from the v2 issuer`() {
        val e = IdpEndpoints.of(Idp(type = IdpType.ENTRA, issuer = "https://login.microsoftonline.com/0f1e2d3c-tenant/v2.0"))
        assertEquals("https://login.microsoftonline.com/0f1e2d3c-tenant/oauth2/v2.0/authorize", e.authorization)
        assertEquals("https://login.microsoftonline.com/0f1e2d3c-tenant/oauth2/v2.0/token", e.token)
        assertEquals("https://login.microsoftonline.com/0f1e2d3c-tenant/discovery/v2.0/keys", e.jwks)
        assertEquals("https://login.microsoftonline.com/0f1e2d3c-tenant/oauth2/v2.0/logout", e.endSession)
        assertNull(e.userInfo, "names and e-mail come from the ID token")
        assertEquals("https://graph.microsoft.com/.default", e.directoryScope)
    }

    @Test
    fun `generic providers need explicit endpoints, and overrides win for every type`() {
        val error =
            assertFailsWith<IllegalStateException> {
                IdpEndpoints.of(Idp(type = IdpType.GENERIC, issuer = "https://idp.example.com", tokenUri = "https://idp.example.com/t"))
            }
        assertContains(error.message!!, "REQLITH_IDP_AUTHORIZATION_URI")
        assertContains(error.message!!, "REQLITH_IDP_JWKS_URI")

        val generic = IdpEndpoints.of(
            Idp(
                type = IdpType.GENERIC,
                issuer = "https://idp.example.com",
                authorizationUri = "https://idp.example.com/a",
                tokenUri = "https://idp.example.com/t",
                jwksUri = "https://idp.example.com/k",
                userInfoUri = " ",
            ),
        )
        assertEquals("https://idp.example.com/a", generic.authorization)
        assertNull(generic.userInfo, "blank overrides count as not set")
        assertNull(generic.endSession)

        val overridden = IdpEndpoints.of(Idp(issuer = "http://kc/realms/acme", endSessionUri = "https://sso.example.com/logout"))
        assertEquals("https://sso.example.com/logout", overridden.endSession)
    }

    @Test
    fun `entra issuers must be https`() {
        assertFailsWith<IllegalArgumentException> { IdpEndpoints.of(Idp(type = IdpType.ENTRA, issuer = "tenant-id")) }
    }
}
