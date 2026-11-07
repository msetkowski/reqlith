package io.reqlith.config

import io.github.oshai.kotlinlogging.KotlinLogging
import io.reqlith.config.ReqlithProperties.DirectoryType
import io.reqlith.service.EntraUserDirectory
import io.reqlith.service.KeycloakUserDirectory
import io.reqlith.service.UserDirectory
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager
import org.springframework.security.oauth2.client.InMemoryOAuth2AuthorizedClientService
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService
import org.springframework.security.oauth2.client.registration.ClientRegistration
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository
import org.springframework.security.oauth2.core.AuthorizationGrantType
import org.springframework.security.oauth2.core.ClientAuthenticationMethod

private val logger = KotlinLogging.logger {}

/**
 * OAuth 2 / OIDC client registrations built from `reqlith.idp.*` (Keycloak, Entra ID or any OIDC provider):
 * `idp` signs users in (authorization code), `directory` lets the server read the user directory
 * (client credentials). Endpoints are configured, not discovered, so the app starts while the IDP is down.
 */
@Configuration(proxyBeanMethods = false)
class IdpConfig(private val properties: ReqlithProperties) {

    @Bean
    fun idpEndpoints(): IdpEndpoints = IdpEndpoints.of(properties.idp).also {
        logger.info { "Identity provider type=${properties.idp.type} issuer=${properties.idp.issuer} directory=${properties.idp.directory}" }
        logger.debug { "Identity provider endpoints $it" }
    }

    @Bean
    fun clientRegistrationRepository(endpoints: IdpEndpoints): ClientRegistrationRepository {
        val idp = properties.idp
        val login = ClientRegistration.withRegistrationId(LOGIN)
            .clientName("Reqlith IDP")
            .clientId(idp.clientId)
            .clientSecret(idp.clientSecret)
            .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
            .scope(idp.scopes)
            .authorizationUri(endpoints.authorization)
            .tokenUri(endpoints.token)
            .jwkSetUri(endpoints.jwks)
            .userInfoUri(endpoints.userInfo)
            .userNameAttributeName("sub")
            .issuerUri(idp.issuer)
            .build()
        val directory = ClientRegistration.withRegistrationId(DIRECTORY)
            .clientId(idp.clientId)
            .clientSecret(idp.clientSecret)
            .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
            .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
            .scope(listOfNotNull(endpoints.directoryScope))
            .tokenUri(endpoints.token)
            .build()
        return InMemoryClientRegistrationRepository(login, directory)
    }

    @Bean
    fun authorizedClientService(registrations: ClientRegistrationRepository): OAuth2AuthorizedClientService =
        InMemoryOAuth2AuthorizedClientService(registrations)

    /** Client credentials tokens for server-to-server calls (user directory); cached until they expire. */
    @Bean
    fun serverClientManager(registrations: ClientRegistrationRepository, clients: OAuth2AuthorizedClientService): OAuth2AuthorizedClientManager =
        AuthorizedClientServiceOAuth2AuthorizedClientManager(registrations, clients).apply {
            setAuthorizedClientProvider(OAuth2AuthorizedClientProviderBuilder.builder().clientCredentials().build())
        }

    /** The IDP's user directory for invitation suggestions, unless `reqlith.idp.directory` is NONE. */
    @Bean
    @ConditionalOnExpression("'\${reqlith.idp.directory:NONE}'.toUpperCase() != 'NONE'")
    fun userDirectory(clients: OAuth2AuthorizedClientManager): UserDirectory = when (properties.idp.directory) {
        DirectoryType.KEYCLOAK -> KeycloakUserDirectory(properties, clients)
        DirectoryType.ENTRA -> EntraUserDirectory(clients, properties.idp.clientId, assignedOnly = false)
        DirectoryType.ENTRA_ASSIGNED -> EntraUserDirectory(clients, properties.idp.clientId, assignedOnly = true)
        DirectoryType.NONE -> error("unreachable")
    }

    companion object {
        const val LOGIN = "idp"
        const val DIRECTORY = "directory"
    }
}
