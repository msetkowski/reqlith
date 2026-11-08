package io.reqlith.model.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/** Discussion during a review, about one element or the project as a whole. */
@Entity
@Table(name = "review_comment")
class ReviewComment(
    @Column(name = "version_id", nullable = false, updatable = false)
    val versionId: UUID,
    @Column(name = "element_key", updatable = false)
    val elementKey: String?,
    @Column(name = "author_id", nullable = false, updatable = false)
    val authorId: UUID,
    @Column(name = "body_md", nullable = false, updatable = false)
    val bodyMd: String,
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant,
) : AbstractEntity()
