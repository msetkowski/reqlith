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
    fun `name falls back to given and family name, then the user name`() {
        assertEquals("Rita Reyes", IdpClaims.name(mapOf("name" to "Rita Reyes", "given_name" to "R")))
        assertEquals("Rita Reyes", IdpClaims.name(mapOf("name" to " ", "given_name" to "Rita", "family_name" to "Reyes")))
        assertEquals("rita", IdpClaims.name(mapOf("preferred_username" to "rita")))
        assertNull(IdpClaims.name(emptyMap()))
    }
}
