package io.reqlith.model.entity

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import jakarta.persistence.EmbeddedId
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import java.io.Serializable
import java.time.Instant
import java.util.UUID

@Embeddable
data class AssignmentId(
    @Column(name = "version_id") val versionId: UUID,
    @Column(name = "reviewer_id") val reviewerId: UUID,
) : Serializable


/** A reviewer assigned to one review round (candidate version) and their decision. */
@Entity
@Table(name = "review_assignment")
class ReviewAssignment(
    @EmbeddedId
    val id: AssignmentId,
) {
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var decision: Decision = Decision.PENDING

    @Column(name = "comment_md")
    var commentMd: String? = null

    @Column(name = "decided_at")
    var decidedAt: Instant? = null

    val versionId: UUID get() = id.versionId
    val reviewerId: UUID get() = id.reviewerId
}
