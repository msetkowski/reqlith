package io.reqlith.controller

import io.reqlith.support.IntegrationTest
import io.reqlith.support.TestUsers
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import kotlin.test.assertTrue

/** The application configured for Microsoft Entra ID: sign-in and sign-out go to the tenant's v2 endpoints. */
@IntegrationTest
@AutoConfigureMockMvc
@TestPropertySource(
    properties = [
        "reqlith.idp.type=ENTRA",
        "reqlith.idp.issuer=https://login.microsoftonline.com/11111111-2222-3333-4444-555555555555/v2.0",
        "reqlith.idp.client-id=6a7b8c9d-app",
        "reqlith.idp.directory=ENTRA",
    ],
)
class EntraConfigurationIT(
    @Autowired private val mvc: MockMvc,
    @Autowired private val users: TestUsers,
) {
    private val tenant = "https://login.microsoftonline.com/11111111-2222-3333-4444-555555555555"

    @Test
    fun `sign-in redirects to the tenant's authorize endpoint with the app's client id`() {
        val location = mvc.get("/oauth2/authorization/idp").andReturn().response.redirectedUrl!!
        assertTrue(location.startsWith("$tenant/oauth2/v2.0/authorize?"), location)
        assertTrue("client_id=6a7b8c9d-app" in location && "scope=openid%20profile%20email" in location, location)
    }

    @Test
    fun `sign-out ends the Entra session`() {
        val location = mvc.post("/logout") { with(oidcLogin().oidcUser(users.create())); with(csrf()) }.andReturn().response.redirectedUrl!!
        assertTrue(location.startsWith("$tenant/oauth2/v2.0/logout?post_logout_redirect_uri="), location)
    }
}
