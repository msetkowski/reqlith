package io.reqlith.version

import io.reqlith.common.NotFoundException
import io.reqlith.document.ProjectDocument
import io.reqlith.document.ProjectDocumentService
import io.reqlith.project.Project
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** Read access to the history of a project. */
@Service
@Transactional(readOnly = true)
class VersionService(
    private val versions: ProjectVersionRepository,
    private val codec: SnapshotCodec,
    private val documents: ProjectDocumentService,
) {
    /** Every candidate and approved version, newest first. */
    fun history(project: Project): List<ProjectVersion> = versions.findByProjectIdOrderBySubmittedAtDesc(project.id)

    fun approved(project: Project): List<ProjectVersion> = history(project).filter { it.status == VersionStatus.APPROVED }

    fun latestApproved(project: Project): ProjectVersion? =
        project.latestApprovedVersion?.let { versions.findByProjectIdAndVersionNo(project.id, it) }

    fun openCandidate(project: Project): ProjectVersion? =
        project.currentCandidateId?.let { versions.findById(it).orElse(null) }

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

    fun document(version: ProjectVersion): ProjectDocument = codec.read(version.snapshot)

    fun approvedDocument(project: Project, versionNo: Int): ProjectDocument? =
        versions.findByProjectIdAndVersionNo(project.id, versionNo)?.let(::document)

    /** True when the working copy differs from the latest approved version (or nothing is approved yet). */
    fun hasChangesSinceApproval(project: Project): Boolean {
        val approved = latestApproved(project) ?: return true
        return codec.write(documents.fromWorkingCopy(project)).hash != approved.contentHash
    }

    companion object {
        private val SLUG = Regex("(\\d+)(?:-rc(\\d+))?")
    }
}
