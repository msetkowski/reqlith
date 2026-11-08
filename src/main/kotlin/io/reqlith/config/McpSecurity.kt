package io.reqlith.config

import io.github.oshai.kotlinlogging.KotlinLogging
import io.reqlith.model.dto.McpAuthentication
import io.reqlith.model.dto.McpPrincipal
import io.reqlith.repository.AppUserRepository
import io.reqlith.service.AccessTokenService
import io.reqlith.service.IdpClaims
import io.reqlith.service.UserService
import jakarta.servlet.http.HttpServletRequest
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.AuthenticationManagerResolver
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.invoke
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator
import org.springframework.security.oauth2.core.OAuth2TokenValidator
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtClaimNames
import org.springframework.security.oauth2.jwt.JwtClaimValidator
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtValidators
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthenticationToken
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationProvider
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.security.web.SecurityFilterChain

private val logger = KotlinLogging.logger {}

/**
 * Security for `/mcp`: stateless bearer authentication with either an access token from the IDP
 * (OAuth 2.1, validated against the configured issuer) or a personal access token (`rql_…`).
 */
@Configuration(proxyBeanMethods = false)
class McpSecurity(private val properties: ReqlithProperties) {

    @Bean
    @Order(1)
    fun mcpFilterChain(
        http: HttpSecurity,
        tokens: AccessTokenService,
        users: AppUserRepository,
        userService: UserService,
        jwtDecoder: JwtDecoder,
    ): SecurityFilterChain {
        val patManager = AuthenticationManager { authentication ->
            val value = (authentication as BearerTokenAuthenticationToken).token
            val token = tokens.authenticate(value) ?: throw InvalidBearerTokenException("Unknown or revoked access token")
            logger.debug { "MCP request authenticated with access token token=${token.id}" }
            val user = users.findById(token.userId).orElseThrow { BadCredentialsException("Token owner no longer exists") }
            McpAuthentication(McpPrincipal(user.id, user.displayName, user.initials, user.email, token.id), value)
        }
        val jwtProvider = JwtAuthenticationProvider(jwtDecoder)
        val jwtManager = AuthenticationManager { authentication ->
            val jwt = jwtProvider.authenticate(authentication).credentials as org.springframework.security.oauth2.jwt.Jwt
            val user = userService.provision(requireNotNull(jwt.subject) { "Token without subject" }, IdpClaims.email(jwt.claims), IdpClaims.name(jwt.claims))
            logger.debug { "MCP request authenticated with IDP token user=${user.id} client=${jwt.getClaimAsString("azp")}" }
            McpAuthentication(McpPrincipal(user.id, user.displayName, user.initials, user.email, null), jwt.tokenValue)
        }
        val resolver = AuthenticationManagerResolver<HttpServletRequest> { request ->
            val header = request.getHeader("Authorization").orEmpty()
            if (header.removePrefix("Bearer ").startsWith(AccessTokenService.PREFIX)) patManager else jwtManager
        }
        http {
            securityMatcher("/mcp", "/mcp/**")
            csrf { disable() }
            sessionManagement { sessionCreationPolicy = SessionCreationPolicy.STATELESS }
            authorizeHttpRequests { authorize(anyRequest, authenticated) }
            oauth2ResourceServer {
                authenticationManagerResolver = resolver
                authenticationEntryPoint = entryPoint()
            }
            exceptionHandling { authenticationEntryPoint = entryPoint() }
        }
        return http.build()
    }

    /** Access tokens from the IDP: signature from its keys, issuer must match. */
    @Bean
    fun jwtDecoder(endpoints: IdpEndpoints): JwtDecoder = NimbusJwtDecoder.withJwkSetUri(endpoints.jwks).build().apply {
        val validators = buildList<OAuth2TokenValidator<Jwt>> {
            add(JwtValidators.createDefaultWithIssuer(properties.idp.issuer))
            properties.idp.mcpAudience?.takeIf { it.isNotBlank() }?.let { audience ->
                add(JwtClaimValidator<List<String>>(JwtClaimNames.AUD) { aud -> aud != null && audience in aud })
            }
        }
        setJwtValidator(DelegatingOAuth2TokenValidator(validators))
    }

    /** 401 with a pointer to the protected resource metadata, as MCP clients expect (RFC 9728). */
    private fun entryPoint() = AuthenticationEntryPoint { _, response, _ ->
        response.status = HttpStatus.UNAUTHORIZED.value()
        response.setHeader(
            "WWW-Authenticate",
            """Bearer resource_metadata="${properties.baseUrl.trimEnd('/')}/.well-known/oauth-protected-resource"""",
        )
    }
}
