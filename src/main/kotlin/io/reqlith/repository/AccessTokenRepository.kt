package io.reqlith.repository

import io.reqlith.model.entity.AccessToken
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface AccessTokenRepository : JpaRepository<AccessToken, UUID> {
    fun findByTokenHash(tokenHash: String): AccessToken?

    fun findByUserIdOrderByCreatedAtDesc(userId: UUID): List<AccessToken>
}
