package io.reqlith.model.dto

import java.util.UUID
import org.springframework.security.authentication.AbstractAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority

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
