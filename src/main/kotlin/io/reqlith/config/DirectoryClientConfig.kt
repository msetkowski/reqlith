package io.reqlith.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository

/** Client credentials tokens for server-to-server calls to the IDP (user directory); cached until they expire. */
@Configuration(proxyBeanMethods = false)
class DirectoryClientConfig {
    @Bean
    fun serverClientManager(registrations: ClientRegistrationRepository, clients: OAuth2AuthorizedClientService) =
        AuthorizedClientServiceOAuth2AuthorizedClientManager(registrations, clients).apply {
            setAuthorizedClientProvider(OAuth2AuthorizedClientProviderBuilder.builder().clientCredentials().build())
        }
}
