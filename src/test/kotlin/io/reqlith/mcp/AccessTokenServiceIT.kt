package io.reqlith.mcp

import io.reqlith.common.NotFoundException
import io.reqlith.common.ValidationException
import io.reqlith.support.IntegrationTest
import io.reqlith.support.TestUsers
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.simple.JdbcClient
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@IntegrationTest
class AccessTokenServiceIT(
    @Autowired private val tokens: AccessTokenService,
    @Autowired private val users: TestUsers,
    @Autowired private val jdbc: JdbcClient,
) {
    @Test
    fun `tokens are stored hashed, authenticate until revoked, and only by their owner`() {
        val owner = users.create("Olive Owens")
        val other = users.create("Oscar Ortiz")
        val created = tokens.create(owner, "Backlog agent")

        assertTrue(created.value.startsWith("rql_") && created.value.length > 40)
        val stored = jdbc.sql("select count(*) from access_token where token_hash = ? or token_prefix = ?")
            .params(created.value, created.value).query(Int::class.java).single()
        assertEquals(0, stored, "the raw value is never stored")

        assertNotNull(tokens.authenticate(created.value)?.lastUsedAt)
        assertNull(tokens.authenticate(created.value + "x"))
        assertFailsWith<NotFoundException> { tokens.revoke(other, created.token.id) }
        tokens.revoke(owner, created.token.id)
        assertNull(tokens.authenticate(created.value))
        assertFailsWith<ValidationException> { tokens.create(owner, " ") }
    }
}
