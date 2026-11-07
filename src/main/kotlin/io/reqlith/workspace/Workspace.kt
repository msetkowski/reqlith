package io.reqlith.workspace

import io.reqlith.common.AbstractEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import org.springframework.data.jpa.repository.JpaRepository
import java.time.Instant
import java.util.UUID

/** Groups projects; its key is part of every project key (PRJ-PAY-014). */
@Entity
@Table(name = "workspace")
class Workspace(
    @Column(name = "ws_key", nullable = false, updatable = false)
    val key: String,
    @Column(nullable = false)
    var name: String,
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant,
) : AbstractEntity()

interface WorkspaceRepository : JpaRepository<Workspace, UUID> {
    fun findAllByOrderByName(): List<Workspace>

    fun existsByKey(key: String): Boolean
}
