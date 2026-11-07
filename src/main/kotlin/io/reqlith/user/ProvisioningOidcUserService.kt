package io.reqlith.user

import io.reqlith.config.ReqlithProperties
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService
import org.springframework.security.oauth2.core.oidc.user.OidcUser
import org.springframework.stereotype.Component

/** Loads the OIDC user, provisions the matching [AppUser] and maps IDP roles to authorities. */
@Component
class ProvisioningOidcUserService(
    private val userService: UserService,
    private val properties: ReqlithProperties,
) : OAuth2UserService<OidcUserRequest, OidcUser> {

    private val delegate = OidcUserService()

    override fun loadUser(userRequest: OidcUserRequest): OidcUser {
        val oidcUser = delegate.loadUser(userRequest)
        val appUser = userService.provision(
            subject = requireNotNull(oidcUser.subject) { "ID token without subject" },
            email = oidcUser.email,
            name = oidcUser.fullName ?: listOfNotNull(oidcUser.givenName, oidcUser.familyName).joinToString(" "),
        )
        val authorities = buildList<GrantedAuthority> {
            addAll(oidcUser.authorities)
            add(SimpleGrantedAuthority("ROLE_USER"))
            if (properties.adminRole in idpRoles(oidcUser)) add(SimpleGrantedAuthority(ReqlithOidcUser.ROLE_ADMIN))
        }
        return ReqlithOidcUser(oidcUser, authorities, appUser.id, appUser.displayName, appUser.initials)
    }

    private fun idpRoles(user: OidcUser): Set<String> =
        (user.getClaimAsStringList(properties.rolesClaim) ?: emptyList()).toSet()
}
