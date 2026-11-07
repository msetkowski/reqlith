package io.reqlith.model.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.time.Instant
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
