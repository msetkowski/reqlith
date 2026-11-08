package io.reqlith.repository

import io.reqlith.model.entity.ProjectVersion
import io.reqlith.model.entity.VersionStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.util.UUID

interface ProjectVersionRepository : JpaRepository<ProjectVersion, UUID> {
    fun findByProjectIdOrderBySubmittedAtDesc(projectId: UUID): List<ProjectVersion>

    fun findByProjectIdAndVersionNo(projectId: UUID, versionNo: Int): ProjectVersion?

    fun findByProjectIdAndTargetVersionAndCandidateNo(projectId: UUID, targetVersion: Int, candidateNo: Int): ProjectVersion?

    fun findByProjectIdAndTargetVersion(projectId: UUID, targetVersion: Int): List<ProjectVersion>

    fun findByProjectIdInAndStatus(projectIds: Collection<UUID>, status: VersionStatus): List<ProjectVersion>

    /** The snapshot of one version, read on its own (the entity loads it lazily). */
    @Query("select v.snapshot from ProjectVersion v where v.id = :id")
    fun findSnapshot(id: UUID): String?
}
