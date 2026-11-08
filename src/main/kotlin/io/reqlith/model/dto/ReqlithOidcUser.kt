package io.reqlith.model.dto

import io.reqlith.model.entity.AppUser
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
) : DefaultOidcUser(authorities, delegate.idToken, delegate.userInfo, "sub"),
    CurrentUser {

    override val emailAddress: String? get() = email

    companion object {
        private const val serialVersionUID = 1L
    }
}
