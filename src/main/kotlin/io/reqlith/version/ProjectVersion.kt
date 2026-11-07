package io.reqlith.version

import io.reqlith.common.AbstractEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import org.hibernate.annotations.DynamicUpdate
import org.springframework.data.jpa.repository.JpaRepository
import java.time.Instant
import java.util.UUID

enum class VersionStatus(val label: String) {
    IN_REVIEW("In review"),
    APPROVED("Approved"),
    CHANGES_REQUESTED("Changes requested"),
    REJECTED("Rejected"),
    WITHDRAWN("Withdrawn"),
}

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
    @Column(nullable = false, updatable = false)
    val snapshot: String,
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

interface ProjectVersionRepository : JpaRepository<ProjectVersion, UUID> {
    fun findByProjectIdOrderBySubmittedAtDesc(projectId: UUID): List<ProjectVersion>

    fun findByProjectIdAndVersionNo(projectId: UUID, versionNo: Int): ProjectVersion?

    fun findByProjectIdAndTargetVersionAndCandidateNo(projectId: UUID, targetVersion: Int, candidateNo: Int): ProjectVersion?

    fun findByProjectIdAndTargetVersion(projectId: UUID, targetVersion: Int): List<ProjectVersion>

    fun findByStatus(status: VersionStatus): List<ProjectVersion>
}
