package io.reqlith.service

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class IdpClaimsTest {
    @Test
    fun `e-mail falls back to preferred_username or upn when they are addresses (Entra)`() {
        assertEquals("rita@acme.test", IdpClaims.email(mapOf("email" to "rita@acme.test", "preferred_username" to "other@acme.test")))
        assertEquals("rita@acme.onmicrosoft.com", IdpClaims.email(mapOf("preferred_username" to "rita@acme.onmicrosoft.com")))
        assertEquals("raj@acme.test", IdpClaims.email(mapOf("preferred_username" to "raj", "upn" to "raj@acme.test")))
        assertNull(IdpClaims.email(mapOf("preferred_username" to "rita")))
    }

    @Test
    fun `roles come from a list, a space-separated string or a nested claim`() {
        assertEquals(setOf("ARCHITECT", "USER"), IdpClaims.roles(mapOf("roles" to listOf("ARCHITECT", "USER")), "roles"))
        assertEquals(setOf("ARCHITECT", "USER"), IdpClaims.roles(mapOf("roles" to "ARCHITECT USER"), "roles"))
        assertEquals(setOf("architect"), IdpClaims.roles(mapOf("realm_access" to mapOf("roles" to listOf("architect"))), "realm_access.roles"))
        assertEquals(emptySet(), IdpClaims.roles(mapOf("roles" to listOf("ARCHITECT")), "groups"))
        assertEquals(emptySet(), IdpClaims.roles(mapOf("realm_access" to "x"), "realm_access.roles"))
    }

    @Test
    fun `name falls back to given and family name, then the user name`() {
        assertEquals("Rita Reyes", IdpClaims.name(mapOf("name" to "Rita Reyes", "given_name" to "R")))
        assertEquals("Rita Reyes", IdpClaims.name(mapOf("name" to " ", "given_name" to "Rita", "family_name" to "Reyes")))
        assertEquals("rita", IdpClaims.name(mapOf("preferred_username" to "rita")))
        assertNull(IdpClaims.name(emptyMap()))
    }
}
