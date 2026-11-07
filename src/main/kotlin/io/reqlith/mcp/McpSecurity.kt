package io.reqlith.mcp

import io.reqlith.config.ReqlithProperties
import io.reqlith.user.AppUserRepository
import io.reqlith.user.CurrentUser
import io.reqlith.user.UserService
import org.springframework.core.annotation.Order
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpStatus
import org.springframework.security.authentication.AbstractAuthenticationToken
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.AuthenticationManagerResolver
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.invoke
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtValidators
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthenticationToken
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationProvider
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.security.web.SecurityFilterChain
import jakarta.servlet.http.HttpServletRequest
import java.util.UUID

/** Who an MCP call runs for: the signed-in user behind a JWT or a personal access token. */
class McpPrincipal(
    override val userId: UUID,
    override val displayName: String,
    override val initials: String,
    override val emailAddress: String?,
    /** Set when authenticated with a personal access token. */
    val tokenId: UUID?,
) : CurrentUser {
    override val isAdmin: Boolean = false
}

class McpAuthentication(private val principal: McpPrincipal, private val credential: String) :
    AbstractAuthenticationToken(listOf(SimpleGrantedAuthority("ROLE_AGENT"))) {
    init {
        isAuthenticated = true
    }

    override fun getPrincipal(): McpPrincipal = principal

    override fun getCredentials(): String = credential
}

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
            val user = users.findById(token.userId).orElseThrow { BadCredentialsException("Token owner no longer exists") }
            McpAuthentication(McpPrincipal(user.id, user.displayName, user.initials, user.email, token.id), value)
        }
        val jwtProvider = JwtAuthenticationProvider(jwtDecoder)
        val jwtManager = AuthenticationManager { authentication ->
            val jwt = jwtProvider.authenticate(authentication).credentials as org.springframework.security.oauth2.jwt.Jwt
            val name = jwt.getClaimAsString("name") ?: jwt.getClaimAsString("preferred_username")
            val user = userService.provision(requireNotNull(jwt.subject) { "Token without subject" }, jwt.getClaimAsString("email"), name)
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
    fun jwtDecoder(): JwtDecoder =
        NimbusJwtDecoder.withJwkSetUri(properties.idp.internalUrl + "/protocol/openid-connect/certs").build().apply {
            setJwtValidator(JwtValidators.createDefaultWithIssuer(properties.idp.issuer))
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
