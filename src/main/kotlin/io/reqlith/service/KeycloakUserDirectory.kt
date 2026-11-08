package io.reqlith.service

import io.reqlith.config.IdpConfig
import io.reqlith.config.ReqlithProperties
import io.reqlith.model.dto.DirectoryUser
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager
import org.springframework.web.client.RestClient
import org.springframework.web.client.body

/**
 * The enabled users of the Keycloak realm, through its admin REST API. Reqlith authenticates with the
 * client credentials of its own client (registration `directory`); the client's service account needs the
 * `realm-management` role `view-users` and the client scope `kc-admin-api`. Enabled with
 * `reqlith.idp.directory=KEYCLOAK` (see IdpConfig).
 */
class KeycloakUserDirectory(properties: ReqlithProperties, private val clients: OAuth2AuthorizedClientManager) : UserDirectory {
    /** http://host/realms/acme -> http://host/admin/realms/acme */
    private val adminUrl = properties.idp.internalUrl.trimEnd('/').replace("/realms/", "/admin/realms/")
    private val http = RestClient.create()

    override fun search(text: String, limit: Int): List<DirectoryUser> {
        val token = clients.authorize(OAuth2AuthorizeRequest.withClientRegistrationId(IdpConfig.DIRECTORY).principal("reqlith").build())
            ?.accessToken?.tokenValue
            ?: error("No access token for the Keycloak user directory")
        val users = http.get()
            .uri("$adminUrl/users?search={text}&max={max}&briefRepresentation=true&enabled=true", text, limit)
            .header("Authorization", "Bearer $token")
            .retrieve()
            .body<List<KeycloakUser>>()
            .orEmpty()
        return users.filter { it.enabled != false && !it.email.isNullOrBlank() }.map { user ->
            val name = listOfNotNull(user.firstName, user.lastName).joinToString(" ").ifBlank { user.username ?: user.email!! }
            DirectoryUser(name, user.email!!)
        }
    }

    /** The fields Reqlith reads from Keycloak's UserRepresentation. */
    data class KeycloakUser(
        val username: String? = null,
        val firstName: String? = null,
        val lastName: String? = null,
        val email: String? = null,
        val enabled: Boolean? = null,
    )
}
