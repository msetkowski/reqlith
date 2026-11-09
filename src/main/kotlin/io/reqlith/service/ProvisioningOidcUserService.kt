package io.reqlith.service

import io.github.oshai.kotlinlogging.KotlinLogging
import io.reqlith.config.ReqlithProperties
import io.reqlith.model.dto.CurrentUser
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

/** Loads the OIDC user, provisions the matching [AppUser] and maps IDP roles to application roles. */
@Component
class ProvisioningOidcUserService(private val userService: UserService, private val properties: ReqlithProperties) :
    OAuth2UserService<OidcUserRequest, OidcUser> {

    private val delegate = OidcUserService()

    override fun loadUser(userRequest: OidcUserRequest): OidcUser {
        val oidcUser = delegate.loadUser(userRequest)
        val appUser = userService.provision(
            subject = requireNotNull(oidcUser.subject) { "ID token without subject" },
            email = IdpClaims.email(oidcUser.claims),
            name = IdpClaims.name(oidcUser.claims),
        )
        val authorities = oidcUser.authorities + appAuthorities(oidcUser.claims)
        logger.info { "User signed in user=${appUser.id} architect=${authorities.any { it.authority == CurrentUser.ARCHITECT_AUTHORITY }}" }
        return ReqlithOidcUser(oidcUser, authorities, appUser.id, appUser.displayName, appUser.initials)
    }

    /** ROLE_USER for everyone; ROLE_ARCHITECT when the roles claim holds one of the configured architect roles. */
    fun appAuthorities(claims: Map<String, Any?>): List<GrantedAuthority> {
        val idpRoles = IdpClaims.roles(claims, properties.idp.rolesClaim)
        logger.debug { "IDP roles claim=${properties.idp.rolesClaim} roles=$idpRoles" }
        val architect = properties.idp.architectRoles.any { wanted -> idpRoles.any { it.equals(wanted.trim(), ignoreCase = true) } }
        return buildList {
            add(SimpleGrantedAuthority("ROLE_USER"))
            if (architect) add(SimpleGrantedAuthority(CurrentUser.ARCHITECT_AUTHORITY))
        }
    }
}
