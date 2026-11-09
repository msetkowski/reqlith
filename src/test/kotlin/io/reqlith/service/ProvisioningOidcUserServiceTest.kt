package io.reqlith.service

import io.mockk.mockk
import io.reqlith.config.ReqlithProperties
import io.reqlith.model.dto.CurrentUser
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class ProvisioningOidcUserServiceTest {
    private fun service(claim: String = "roles", architectRoles: List<String> = listOf("ARCHITECT")) = ProvisioningOidcUserService(
        mockk(),
        ReqlithProperties(idp = ReqlithProperties.Idp(issuer = "http://idp.test", rolesClaim = claim, architectRoles = architectRoles)),
    )

    private fun ProvisioningOidcUserService.authorityNames(claims: Map<String, Any?>) = appAuthorities(claims).map { it.authority }.toSet()

    @Test
    fun `an IDP role listed as architect role grants ARCHITECT`() {
        assertEquals(setOf("ROLE_USER", CurrentUser.ARCHITECT_AUTHORITY), service().authorityNames(mapOf("roles" to listOf("ARCHITECT"))))
        assertEquals(setOf("ROLE_USER"), service().authorityNames(mapOf("roles" to listOf("REVIEWER"))))
        assertEquals(setOf("ROLE_USER"), service().authorityNames(emptyMap()))
    }

    @Test
    fun `claim name and role names are configurable`() {
        val custom = service("realm_access.roles", listOf("reqlith-architect", "chief-architect"))
        val claims = mapOf("realm_access" to mapOf("roles" to listOf("Chief-Architect")))
        assertEquals(setOf("ROLE_USER", CurrentUser.ARCHITECT_AUTHORITY), custom.authorityNames(claims))
        assertEquals(setOf("ROLE_USER"), custom.authorityNames(mapOf("roles" to listOf("ARCHITECT"))))
    }
}
