package io.reqlith.model.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

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
