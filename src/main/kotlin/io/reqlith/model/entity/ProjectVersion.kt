package io.reqlith.model.entity

import io.reqlith.model.dto.Snapshot
import jakarta.persistence.Basic
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID
import org.hibernate.annotations.DynamicUpdate

/**
 * A frozen copy of the whole project: a review candidate (vN-rcK) and, once approved, version vN.
 * Snapshot and hash are never updated; a database trigger also refuses any change to an approved row.
 */
@Entity
@Table(name = "project_version")
@DynamicUpdate
class ProjectVersion(
    @Column(name = "project_id", nullable = false, updatable = false)
    val projectId: UUID,
    /** The version number this candidate becomes when approved. */
    @Column(name = "target_version", nullable = false, updatable = false)
    val targetVersion: Int,
    @Column(name = "candidate_no", nullable = false, updatable = false)
    val candidateNo: Int,
    snapshot: String,
    @Column(name = "schema_version", nullable = false, updatable = false)
    val schemaVersion: Int,
    @Column(name = "content_hash", nullable = false, updatable = false)
    val contentHash: String,
    /** The approved version this candidate was compared against. */
    @Column(name = "based_on_version", updatable = false)
    val basedOnVersion: Int?,
    @Column(name = "submitted_by", nullable = false, updatable = false)
    val submittedBy: UUID,
    @Column(name = "submitted_at", nullable = false, updatable = false)
    val submittedAt: Instant,
) : AbstractEntity() {

    /** The whole project as canonical JSON; loaded only on access (bytecode-enhanced lazy column). */
    @Basic(fetch = FetchType.LAZY)
    @Column(nullable = false, updatable = false)
    var snapshot: String = snapshot
        protected set

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: VersionStatus = VersionStatus.IN_REVIEW

    /** Set only on approval, so approved versions are numbered without gaps. */
    @Column(name = "version_no")
    var versionNo: Int? = null

    @Column(name = "closed_at")
    var closedAt: Instant? = null

    /** "v3" once approved, "v3-rc2" while a candidate. */
    val label: String get() = versionNo?.let { "v$it" } ?: "v$targetVersion-rc$candidateNo"

    /** URL segment: "3" or "3-rc2". */
    val slug: String get() = versionNo?.toString() ?: "$targetVersion-rc$candidateNo"

    fun close(status: VersionStatus, at: Instant) {
        check(this.status == VersionStatus.IN_REVIEW) { "$label is already closed" }
        this.status = status
        closedAt = at
        if (status == VersionStatus.APPROVED) versionNo = targetVersion
    }
}
