package io.reqlith.model.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/** A global non-functional requirement (BL-03) that applies to every project and is maintained by architects. */
@Entity
@Table(name = "baseline_requirement")
class BaselineRequirement(
    @Column(name = "baseline_key", nullable = false, updatable = false)
    val key: String,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var category: BaselineCategory,
    @Column(nullable = false)
    var title: String,
    @Column(name = "body_md")
    var bodyMd: String?,
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant,
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant,
    @Column(name = "updated_by", nullable = false)
    var updatedBy: UUID,
) : AbstractEntity()
