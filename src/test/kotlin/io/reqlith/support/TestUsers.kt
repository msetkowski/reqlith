package io.reqlith.support

import io.reqlith.model.dto.ReqlithOidcUser
import io.reqlith.service.UserService
import java.time.Instant
import java.util.UUID
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.core.oidc.OidcIdToken
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser
import org.springframework.stereotype.Component

/** Creates provisioned users and the matching session principal for tests. */
@Component
class TestUsers(private val userService: UserService) {

    fun create(name: String = "Test User", admin: Boolean = false): ReqlithOidcUser {
        val unique = UUID.randomUUID().toString().take(8)
        val email = name.lowercase().replace(' ', '.') + ".$unique@reqlith.test"
        val subject = "sub-$unique"
        val user = userService.provision(subject, email, name)
        val token = OidcIdToken.withTokenValue("id-token-$unique").subject(subject).claim("email", email)
            .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(600)).build()
        val authorities = buildList {
            add(SimpleGrantedAuthority("ROLE_USER"))
            if (admin) add(SimpleGrantedAuthority(ReqlithOidcUser.ROLE_ADMIN))
        }
        return ReqlithOidcUser(DefaultOidcUser(authorities, token), authorities, user.id, user.displayName, user.initials)
    }
}
