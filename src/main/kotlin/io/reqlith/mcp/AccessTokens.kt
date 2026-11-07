package io.reqlith.mcp

import io.reqlith.common.AbstractEntity
import io.reqlith.common.NotFoundException
import io.reqlith.common.ValidationException
import io.reqlith.user.CurrentUser
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Clock
import java.time.Instant
import java.util.Base64
import java.util.UUID

/** A personal access token for agents. Only its SHA-256 is stored; the value is shown once. */
@Entity
@Table(name = "access_token")
class AccessToken(
    @Column(name = "user_id", nullable = false, updatable = false)
    val userId: UUID,
    @Column(nullable = false, updatable = false)
    val name: String,
    @Column(name = "token_hash", nullable = false, updatable = false)
    val tokenHash: String,
    @Column(name = "token_prefix", nullable = false, updatable = false)
    val tokenPrefix: String,
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant,
) : AbstractEntity() {
    @Column(name = "last_used_at")
    var lastUsedAt: Instant? = null

    @Column(name = "revoked_at")
    var revokedAt: Instant? = null

    val active: Boolean get() = revokedAt == null
}

interface AccessTokenRepository : JpaRepository<AccessToken, UUID> {
    fun findByTokenHash(tokenHash: String): AccessToken?

    fun findByUserIdOrderByCreatedAtDesc(userId: UUID): List<AccessToken>
}

/** The raw token, returned exactly once when it is created. */
class NewToken(val token: AccessToken, val value: String)

@Service
class AccessTokenService(
    private val tokens: AccessTokenRepository,
    private val clock: Clock,
) {
    private val random = SecureRandom()

    fun forUser(user: CurrentUser): List<AccessToken> = tokens.findByUserIdOrderByCreatedAtDesc(user.userId)

    @Transactional
    fun create(user: CurrentUser, name: String): NewToken {
        val label = name.trim()
        if (label.isEmpty() || label.length > 100) throw ValidationException("name", "Give the token a name (max. 100 characters), e.g. the agent using it")
        val bytes = ByteArray(32).also(random::nextBytes)
        val value = PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        val token = tokens.save(AccessToken(user.userId, label, sha256(value), value.take(12), Instant.now(clock)))
        return NewToken(token, value)
    }

    @Transactional
    fun revoke(user: CurrentUser, tokenId: UUID) {
        val token = tokens.findById(tokenId).orElseThrow { NotFoundException("Token not found") }
        if (token.userId != user.userId) throw NotFoundException("Token not found")
        if (token.revokedAt == null) token.revokedAt = Instant.now(clock)
    }

    /** The active token with this value, marking it as used; null for unknown or revoked tokens. */
    @Transactional
    fun authenticate(value: String): AccessToken? {
        val token = tokens.findByTokenHash(sha256(value))?.takeIf { it.active } ?: return null
        token.lastUsedAt = Instant.now(clock)
        return token
    }

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }

    companion object {
        const val PREFIX = "rql_"
    }
}
