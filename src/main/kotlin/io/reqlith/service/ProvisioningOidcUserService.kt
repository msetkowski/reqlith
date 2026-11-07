package io.reqlith.service

import io.github.oshai.kotlinlogging.KotlinLogging
import io.reqlith.model.dto.ReqlithOidcUser
import io.reqlith.model.entity.AppUser
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService
import org.springframework.security.oauth2.core.oidc.user.OidcUser
import org.springframework.stereotype.Component

private val logger = KotlinLogging.logger {}

/** Loads the OIDC user and provisions the matching [AppUser]. */
@Component
class ProvisioningOidcUserService(
    private val userService: UserService,
) : OAuth2UserService<OidcUserRequest, OidcUser> {

    private val delegate = OidcUserService()

    override fun loadUser(userRequest: OidcUserRequest): OidcUser {
        val oidcUser = delegate.loadUser(userRequest)
        val appUser = userService.provision(
            subject = requireNotNull(oidcUser.subject) { "ID token without subject" },
            email = IdpClaims.email(oidcUser.claims),
            name = IdpClaims.name(oidcUser.claims),
        )
        val authorities = buildList<GrantedAuthority> {
            addAll(oidcUser.authorities)
            add(SimpleGrantedAuthority("ROLE_USER"))
        }
        logger.info { "User signed in user=${appUser.id}" }
        return ReqlithOidcUser(oidcUser, authorities, appUser.id, appUser.displayName, appUser.initials)
    }

}
