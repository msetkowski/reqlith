package io.reqlith.service

import com.sun.net.httpserver.HttpServer
import io.mockk.every
import io.mockk.mockk
import io.reqlith.config.ReqlithProperties
import org.junit.jupiter.api.Test
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager
import org.springframework.security.oauth2.client.registration.ClientRegistration
import org.springframework.security.oauth2.core.AuthorizationGrantType
import org.springframework.security.oauth2.core.OAuth2AccessToken
import java.net.InetSocketAddress
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The Keycloak admin API client, against a local HTTP server that plays Keycloak. */
class KeycloakUserDirectoryTest {

    @Test
    fun `searches enabled realm users with a client credentials token and maps names`() {
        val requests = mutableListOf<Pair<String, String?>>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/admin/realms/acme/users") { exchange ->
            requests += exchange.requestURI.toString() to exchange.requestHeaders.getFirst("Authorization")
            val body = """[
                {"username":"rita","firstName":"Rita","lastName":"Reyes","email":"rita@acme.test","enabled":true},
                {"username":"robot","enabled":true},
                {"username":"rex","email":"rex@acme.test","enabled":true},
                {"username":"gone","firstName":"Old","email":"gone@acme.test","enabled":false}
            ]""".toByteArray()
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.start()
        try {
            val url = "http://127.0.0.1:${server.address.port}/realms/acme"
            val registration = ClientRegistration.withRegistrationId("directory").clientId("reqlith-app")
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS).tokenUri("$url/token").build()
            val token = OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, "service-token", Instant.now(), Instant.now().plusSeconds(60))
            val clients = mockk<OAuth2AuthorizedClientManager> {
                every { authorize(any()) } returns OAuth2AuthorizedClient(registration, "reqlith", token)
            }
            val directory = KeycloakUserDirectory(ReqlithProperties(idp = ReqlithProperties.Idp(url)), clients)

            val people = directory.search("re x", 10)

            assertEquals(listOf("Rita Reyes" to "rita@acme.test", "rex" to "rex@acme.test"), people.map { it.name to it.email })
            val (uri, auth) = requests.single()
            assertEquals("Bearer service-token", auth)
            assertTrue(uri.contains("search=re%20x") && uri.contains("max=10") && uri.contains("enabled=true"), uri)
        } finally {
            server.stop(0)
        }
    }
}
