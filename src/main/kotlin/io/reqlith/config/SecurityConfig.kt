package io.reqlith.config

import io.reqlith.service.ProvisioningOidcUserService
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.invoke
import org.springframework.security.oauth2.client.oidc.authentication.OidcIdTokenDecoderFactory
import org.springframework.security.oauth2.client.oidc.authentication.OidcIdTokenValidator
import org.springframework.security.oauth2.client.registration.ClientRegistration
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator
import org.springframework.security.oauth2.core.oidc.user.OidcUser
import org.springframework.security.oauth2.jwt.JwtDecoderFactory
import org.springframework.security.oauth2.jwt.JwtIssuerValidator
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler
import org.springframework.web.util.UriComponentsBuilder

@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
class SecurityConfig(private val properties: ReqlithProperties, private val endpoints: IdpEndpoints) {

    /** Browser UI: every page requires an authenticated user from the OAuth 2 / OIDC IDP. */
    @Bean
    fun webSecurity(http: HttpSecurity, oidcUserService: ProvisioningOidcUserService): SecurityFilterChain {
        http {
            authorizeHttpRequests {
                authorize("/css/**", permitAll)
                authorize("/js/**", permitAll)
                authorize("/vendor/**", permitAll)
                authorize("/favicon.svg", permitAll)
                // Kubernetes probes (/actuator/health/liveness, /readiness) and Prometheus scraping.
                authorize("/actuator/health", permitAll)
                authorize("/actuator/health/**", permitAll)
                authorize("/actuator/info", permitAll)
                authorize("/actuator/prometheus", permitAll)
                authorize("/.well-known/oauth-protected-resource/**", permitAll)
                authorize("/error", permitAll)
                authorize(anyRequest, authenticated)
            }
            oauth2Login {
                userInfoEndpoint {
                    this.oidcUserService = oidcUserService
                }
            }
            logout {
                logoutSuccessHandler = idpLogout()
            }
        }
        return http.build()
    }

    /** Discovery is not used (see IdpConfig), so the ID token issuer is checked against the configured issuer. */
    @Bean
    fun idTokenDecoderFactory(): JwtDecoderFactory<ClientRegistration> =
        OidcIdTokenDecoderFactory().apply {
            setJwtValidatorFactory { registration ->
                DelegatingOAuth2TokenValidator(
                    OidcIdTokenValidator(registration),
                    JwtIssuerValidator(properties.idp.issuer),
                )
            }
        }

    /** Ends the IDP session too (RP-initiated logout) when the IDP has an end-session endpoint, then returns home. */
    private fun idpLogout() = LogoutSuccessHandler { request, response, authentication ->
        val home = UriComponentsBuilder.fromUriString(request.requestURL.toString())
            .replacePath(request.contextPath + "/").replaceQuery(null).toUriString()
        val endSession = endpoints.endSession ?: return@LogoutSuccessHandler response.sendRedirect(home)
        val target = UriComponentsBuilder.fromUriString(endSession)
            .queryParam("post_logout_redirect_uri", home)
        (authentication?.principal as? OidcUser)?.let { target.queryParam("id_token_hint", it.idToken.tokenValue) }
        response.sendRedirect(target.encode().toUriString())
    }
}
