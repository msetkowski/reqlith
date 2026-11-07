package io.reqlith.service

import io.github.oshai.kotlinlogging.KotlinLogging
import io.reqlith.exception.NotFoundException
import io.reqlith.model.dto.ProjectDocument
import io.reqlith.model.entity.Project
import io.reqlith.model.entity.ProjectVersion
import io.reqlith.model.entity.VersionStatus
import io.reqlith.repository.ProjectVersionRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

private val logger = KotlinLogging.logger {}

/** Read access to the history of a project. */
@Service
@Transactional(readOnly = true)
class VersionService(private val versions: ProjectVersionRepository, private val codec: SnapshotCodec, private val documents: ProjectDocumentService) {
    /** Every candidate and approved version, newest first. */
    fun history(project: Project): List<ProjectVersion> = versions.findByProjectIdOrderBySubmittedAtDesc(project.id)

    fun approved(project: Project): List<ProjectVersion> = history(project).filter { it.status == VersionStatus.APPROVED }

    fun latestApproved(project: Project): ProjectVersion? = project.latestApprovedVersion?.let { versions.findByProjectIdAndVersionNo(project.id, it) }

    fun openCandidate(project: Project): ProjectVersion? = project.currentCandidateId?.let { versions.findById(it).orElse(null) }

    /** Looks a version up by its URL segment: "3" (approved) or "3-rc2" (candidate). */
    fun bySlug(project: Project, slug: String): ProjectVersion {
        val match = SLUG.matchEntire(slug) ?: throw NotFoundException("Version $slug not found")
        val (target, candidate) = match.destructured
        val version = if (candidate.isEmpty()) {
            versions.findByProjectIdAndVersionNo(project.id, target.toInt())
        } else {
            versions.findByProjectIdAndTargetVersionAndCandidateNo(project.id, target.toInt(), candidate.toInt())
        }
        return version ?: throw NotFoundException("Version v$slug of ${project.key} not found")
    }

    /** Reads the snapshot with its own query, so it works for versions loaded in an earlier transaction too. */
    fun document(version: ProjectVersion): ProjectDocument =
        codec.read(versions.findSnapshot(version.id) ?: throw NotFoundException("Version ${version.label} has no snapshot"))

    fun approvedDocument(project: Project, versionNo: Int): ProjectDocument? = versions.findByProjectIdAndVersionNo(project.id, versionNo)?.let(::document)

    /** True when the working copy differs from the latest approved version (or nothing is approved yet). */
    fun hasChangesSinceApproval(project: Project): Boolean {
        val approved = latestApproved(project) ?: return true
        val hash = codec.write(documents.fromWorkingCopy(project)).hash
        logger.debug { "Working copy of ${project.key} hash=$hash vs ${approved.label} hash=${approved.contentHash}" }
        return hash != approved.contentHash
    }

    companion object {
        private val SLUG = Regex("(\\d+)(?:-rc(\\d+))?")
    }
}
