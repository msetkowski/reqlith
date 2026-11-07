package io.reqlith.service

import io.reqlith.exception.NotFoundException
import io.reqlith.exception.ValidationException
import io.reqlith.model.dto.CurrentUser
import io.reqlith.model.dto.NewToken
import io.reqlith.model.entity.AccessToken
import io.reqlith.repository.AccessTokenRepository
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Clock
import java.time.Instant
import java.util.Base64
import java.util.UUID
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class AccessTokenService(
    private val tokens: AccessTokenRepository,
    private val clock: Clock,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    private val random = SecureRandom()

    fun forUser(user: CurrentUser): List<AccessToken> = tokens.findByUserIdOrderByCreatedAtDesc(user.userId)

    @Transactional
    fun create(user: CurrentUser, name: String): NewToken {
        val label = name.trim()
        if (label.isEmpty() || label.length > 100) throw ValidationException("name", "Give the token a name (max. 100 characters), e.g. the agent using it")
        val bytes = ByteArray(32).also(random::nextBytes)
        val value = PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        val token = tokens.save(AccessToken(user.userId, label, sha256(value), value.take(12), Instant.now(clock)))
        logger.info("Access token created token={} name={}", token.id, label)
        return NewToken(token, value)
    }

    @Transactional
    fun revoke(user: CurrentUser, tokenId: UUID) {
        val token = tokens.findById(tokenId).orElseThrow { NotFoundException("Token not found") }
        if (token.userId != user.userId) throw NotFoundException("Token not found")
        if (token.revokedAt == null) {
            token.revokedAt = Instant.now(clock)
            logger.info("Access token revoked token={}", token.id)
        }
    }

    /** The active token with this value, marking it as used; null for unknown or revoked tokens. */
    @Transactional
    fun authenticate(value: String): AccessToken? {
        val token = tokens.findByTokenHash(sha256(value))?.takeIf { it.active }
        if (token == null) {
            logger.debug("Access token rejected: unknown or revoked (prefix {})", value.take(8))
            return null
        }
        token.lastUsedAt = Instant.now(clock)
        return token
    }

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }

    companion object {
        const val PREFIX = "rql_"
    }
}
