package io.reqlith.model.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/** A group of users (e.g. "Customer"), shared by all projects. */
@Entity
@Table(name = "role")
class Role(
    @Column(name = "role_key", nullable = false, updatable = false)
    val key: String,
    @Column(nullable = false)
    var name: String,
    @Column(name = "description_md")
    var descriptionMd: String?,
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant,
    @Column(name = "updated_by", nullable = false)
    var updatedBy: UUID,
) : AbstractEntity()
