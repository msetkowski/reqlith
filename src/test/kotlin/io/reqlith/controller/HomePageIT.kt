package io.reqlith.controller

import io.reqlith.model.dto.ReqlithOidcUser
import io.reqlith.service.UserService
import io.reqlith.support.IntegrationTest
import java.time.Instant
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.core.oidc.OidcIdToken
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

@IntegrationTest
@AutoConfigureMockMvc
class HomePageIT(
    @Autowired private val mvc: MockMvc,
    @Autowired private val userService: UserService,
) {
    @Test
    fun `anonymous users are sent to the IDP`() {
        mvc.get("/").andExpect {
            status { is3xxRedirection() }
            redirectedUrl("/oauth2/authorization/idp")
        }
    }

    @Test
    fun `signed-in user sees the projects home page`() {
        val user = userService.provision("sub-home", "owner1@reqlith.local", "Olive Owens")
        val token = OidcIdToken.withTokenValue("id-token").subject("sub-home").issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(60)).build()
        val authorities = listOf(SimpleGrantedAuthority("ROLE_USER"))
        val principal = ReqlithOidcUser(DefaultOidcUser(authorities, token), authorities, user.id, user.displayName, user.initials)

        mvc.get("/") { with(oidcLogin().oidcUser(principal)) }.andExpect {
            status { isOk() }
            content { string(org.hamcrest.Matchers.containsString("Projects")) }
            content { string(org.hamcrest.Matchers.containsString("OO")) }
        }
    }
}
