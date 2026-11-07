package io.reqlith.service

import io.reqlith.config.IdpConfig
import io.reqlith.model.dto.DirectoryUser
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager
import org.springframework.web.client.RestClient
import org.springframework.web.client.body

/**
 * People in Microsoft Entra ID, through Microsoft Graph with an application token (client credentials,
 * scope `https://graph.microsoft.com/.default`).
 *
 * - all enabled users of the tenant: application permission `User.Read.All`;
 * - [assignedOnly]: only users assigned directly to the Reqlith enterprise application (the users allowed to
 *   sign in when "Assignment required" is on), additionally `Application.Read.All`. Group assignments are
 *   not expanded, and matching is by display name.
 */
class EntraUserDirectory(
    private val clients: OAuth2AuthorizedClientManager,
    /** The application (client) id of Reqlith's app registration. */
    private val clientId: String,
    private val assignedOnly: Boolean,
    graphUrl: String = "https://graph.microsoft.com/v1.0",
) : UserDirectory {
    private val graph = RestClient.create(graphUrl)

    override fun search(text: String, limit: Int): List<DirectoryUser> {
        val token = clients.authorize(OAuth2AuthorizeRequest.withClientRegistrationId(IdpConfig.DIRECTORY).principal("reqlith").build())
            ?.accessToken?.tokenValue
            ?: error("No access token for Microsoft Graph")
        val term = text.replace("\"", "").replace("\\", "")
        val users = if (assignedOnly) assignedUsers(token, term, limit) else searchUsers(token, term, limit)
        return users.filter { it.accountEnabled != false }.mapNotNull { user ->
            val email = user.mail?.takeIf { it.isNotBlank() } ?: user.userPrincipalName?.takeIf { '@' in it } ?: return@mapNotNull null
            DirectoryUser(user.displayName?.takeIf { it.isNotBlank() } ?: email, email)
        }
    }

    /** Advanced query: $search needs the ConsistencyLevel header. */
    private fun searchUsers(token: String, term: String, limit: Int): List<GraphUser> = graph.get()
        .uri { uri ->
            uri.path("/users")
                .queryParam("\$search", "{search}")
                .queryParam("\$filter", "accountEnabled eq true")
                .queryParam("\$select", "displayName,mail,userPrincipalName,accountEnabled")
                .queryParam("\$top", limit)
                .build("\"displayName:$term\" OR \"mail:$term\" OR \"userPrincipalName:$term\"")
        }
        .header("Authorization", "Bearer $token")
        .header("ConsistencyLevel", "eventual")
        .retrieve()
        .body<GraphPage<GraphUser>>()
        ?.value.orEmpty()

    private fun assignedUsers(token: String, term: String, limit: Int): List<GraphUser> {
        val assigned = graph.get()
            .uri { uri ->
                uri.path("/servicePrincipals(appId='{appId}')/appRoleAssignedTo")
                    .queryParam("\$select", "principalId,principalType,principalDisplayName")
                    .queryParam("\$top", 999)
                    .build(clientId)
            }
            .header("Authorization", "Bearer $token")
            .retrieve()
            .body<GraphPage<Assignment>>()
            ?.value.orEmpty()
        val ids = assigned
            .filter { it.principalType == "User" && it.principalDisplayName.orEmpty().contains(term, ignoreCase = true) }
            .map { it.principalId }
            .distinct()
            .take(limit)
        // Graph allows up to 15 values in an `in` filter.
        return ids.chunked(15).flatMap { chunk ->
            graph.get()
                .uri { uri ->
                    uri.path("/users")
                        .queryParam("\$filter", "{filter}")
                        .queryParam("\$select", "displayName,mail,userPrincipalName,accountEnabled")
                        .build("id in (${chunk.joinToString(",") { "'$it'" }})")
                }
                .header("Authorization", "Bearer $token")
                .retrieve()
                .body<GraphPage<GraphUser>>()
                ?.value.orEmpty()
        }
    }

    data class GraphPage<T>(val value: List<T> = emptyList())

    data class GraphUser(val displayName: String? = null, val mail: String? = null, val userPrincipalName: String? = null, val accountEnabled: Boolean? = null)

    data class Assignment(val principalId: String = "", val principalType: String? = null, val principalDisplayName: String? = null)
}
