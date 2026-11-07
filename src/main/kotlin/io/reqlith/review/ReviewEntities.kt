package io.reqlith.review

import io.reqlith.common.AbstractEntity
import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import jakarta.persistence.EmbeddedId
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import org.springframework.data.jpa.repository.JpaRepository
import java.io.Serializable
import java.time.Instant
import java.util.UUID

enum class Decision(val label: String) {
    PENDING("Pending"),
    ACCEPT("Accepted"),
    CHANGES("Changes requested"),
    REJECT("Rejected"),
}

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

enum class SuggestionStatus { OPEN, APPLIED, DISMISSED }

/** Proposed wording for one element (or the basic information), applied by authors after the round closes. */
@Entity
@Table(name = "suggestion")
class Suggestion(
    @Column(name = "version_id", nullable = false, updatable = false)
    val versionId: UUID,
    /** Element key, or [BASIC_INFO]. */
    @Column(name = "element_key", nullable = false, updatable = false)
    val elementKey: String,
    @Column(name = "proposed_md", updatable = false)
    val proposedMd: String?,
    @Column(name = "comment_md", updatable = false)
    val commentMd: String?,
    @Column(name = "author_id", nullable = false, updatable = false)
    val authorId: UUID,
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant,
) : AbstractEntity() {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: SuggestionStatus = SuggestionStatus.OPEN

    @Column(name = "resolved_at")
    var resolvedAt: Instant? = null

    @Column(name = "resolved_by")
    var resolvedBy: UUID? = null

    companion object {
        const val BASIC_INFO = "BASIC_INFO"
    }
}

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

interface ReviewAssignmentRepository : JpaRepository<ReviewAssignment, AssignmentId> {
    fun findByIdVersionId(versionId: UUID): List<ReviewAssignment>

    fun findByIdReviewerIdAndDecision(reviewerId: UUID, decision: Decision): List<ReviewAssignment>
}

interface SuggestionRepository : JpaRepository<Suggestion, UUID> {
    fun findByVersionIdOrderByCreatedAt(versionId: UUID): List<Suggestion>

    fun findByVersionIdInAndStatus(versionIds: Collection<UUID>, status: SuggestionStatus): List<Suggestion>
}

interface ReviewCommentRepository : JpaRepository<ReviewComment, UUID> {
    fun findByVersionIdOrderByCreatedAt(versionId: UUID): List<ReviewComment>
}
