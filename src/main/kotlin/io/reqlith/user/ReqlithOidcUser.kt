package io.reqlith.user

import org.springframework.security.core.GrantedAuthority
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser
import org.springframework.security.oauth2.core.oidc.user.OidcUser
import java.util.UUID

/** Browser session principal: the IDP's OIDC user enriched with the provisioned [AppUser]. */
class ReqlithOidcUser(
    delegate: OidcUser,
    authorities: Collection<GrantedAuthority>,
    override val userId: UUID,
    override val displayName: String,
    override val initials: String,
) : DefaultOidcUser(authorities, delegate.idToken, delegate.userInfo, "sub"), CurrentUser {

    override val emailAddress: String? get() = email

    override val isAdmin: Boolean get() = authorities.any { it.authority == ROLE_ADMIN }

    companion object {
        const val ROLE_ADMIN = "ROLE_ADMIN"
        private const val serialVersionUID = 1L
    }
}
