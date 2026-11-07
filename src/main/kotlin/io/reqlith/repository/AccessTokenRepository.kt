package io.reqlith.repository

import io.reqlith.model.entity.AccessToken
import java.util.UUID
import org.springframework.data.jpa.repository.JpaRepository

interface AccessTokenRepository : JpaRepository<AccessToken, UUID> {
    fun findByTokenHash(tokenHash: String): AccessToken?

    fun findByUserIdOrderByCreatedAtDesc(userId: UUID): List<AccessToken>
}
