package io.reqlith.model.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.time.Instant

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
