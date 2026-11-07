package io.reqlith.service

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager
import org.springframework.security.oauth2.client.registration.ClientRegistration
import org.springframework.security.oauth2.core.AuthorizationGrantType
import org.springframework.security.oauth2.core.OAuth2AccessToken
import java.net.InetSocketAddress
import java.net.URLDecoder
import java.time.Instant
import kotlin.test.assertContains
import kotlin.test.assertEquals

/** Microsoft Graph client, against a local server returning documented Graph responses. */
class EntraUserDirectoryTest {
    private val clients = mockk<OAuth2AuthorizedClientManager> {
        val registration = ClientRegistration.withRegistrationId("directory").clientId("app-id")
            .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS).tokenUri("https://login.microsoftonline.com/t/oauth2/v2.0/token").build()
        val token = OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, "graph-token", Instant.now(), Instant.now().plusSeconds(60))
        every { authorize(any()) } returns OAuth2AuthorizedClient(registration, "reqlith", token)
    }

    private data class Seen(val path: String, val query: String, val consistency: String?, val auth: String?)

    private fun graph(responses: Map<String, String>, test: (url: String, seen: List<Seen>) -> Unit) {
        val seen = mutableListOf<Seen>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/v1.0/") { exchange: HttpExchange ->
            val uri = exchange.requestURI
            seen += Seen(uri.path, URLDecoder.decode(uri.rawQuery.orEmpty(), Charsets.UTF_8),
                exchange.requestHeaders.getFirst("ConsistencyLevel"), exchange.requestHeaders.getFirst("Authorization"))
            val key = responses.keys.first { uri.path.contains(it) }
            val body = responses.getValue(key).toByteArray()
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.start()
        try {
            test("http://127.0.0.1:${server.address.port}/v1.0", seen)
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `searches enabled tenant users with an advanced query`() = graph(
        mapOf("/users" to """{"value":[
            {"displayName":"Rita Reyes","mail":"rita@acme.test","userPrincipalName":"rita@acme.onmicrosoft.com","accountEnabled":true},
            {"displayName":"Raj Rao","mail":null,"userPrincipalName":"raj@acme.onmicrosoft.com","accountEnabled":true},
            {"displayName":"Room 4","mail":null,"userPrincipalName":"room4#EXT#","accountEnabled":true}
        ]}"""),
    ) { url, seen ->
        val people = EntraUserDirectory(clients, "app-id", assignedOnly = false, graphUrl = url).search("r\"a", 10)

        assertEquals(listOf("Rita Reyes" to "rita@acme.test", "Raj Rao" to "raj@acme.onmicrosoft.com"), people.map { it.name to it.email })
        val request = seen.single()
        assertEquals("eventual", request.consistency)
        assertEquals("Bearer graph-token", request.auth)
        assertContains(request.query, "\$search=\"displayName:ra\" OR \"mail:ra\" OR \"userPrincipalName:ra\"")
        assertContains(request.query, "\$filter=accountEnabled eq true")
        assertContains(request.query, "\$top=10")
    }

    @Test
    fun `assigned-only mode reads the enterprise application's user assignments`() = graph(
        mapOf(
            "/appRoleAssignedTo" to """{"value":[
                {"principalId":"u1","principalType":"User","principalDisplayName":"Rita Reyes"},
                {"principalId":"g1","principalType":"Group","principalDisplayName":"Refund team"},
                {"principalId":"u2","principalType":"User","principalDisplayName":"Oscar Ortiz"}
            ]}""",
            "/users" to """{"value":[{"displayName":"Rita Reyes","mail":"rita@acme.test","accountEnabled":true}]}""",
        ),
    ) { url, seen ->
        val people = EntraUserDirectory(clients, "app-id", assignedOnly = true, graphUrl = url).search("rit", 10)

        assertEquals(listOf("rita@acme.test"), people.map { it.email })
        assertEquals("/v1.0/servicePrincipals(appId='app-id')/appRoleAssignedTo", seen[0].path)
        assertContains(seen[1].query, "\$filter=id in ('u1')")
    }
}
