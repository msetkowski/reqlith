package io.reqlith.service

import io.github.oshai.kotlinlogging.KotlinLogging
import io.reqlith.exception.NotFoundException
import io.reqlith.exception.ValidationException
import io.reqlith.model.dto.CurrentUser
import io.reqlith.model.dto.ElementContent
import io.reqlith.model.entity.AssignmentId
import io.reqlith.model.entity.Decision
import io.reqlith.model.entity.Project
import io.reqlith.model.entity.ProjectStatus
import io.reqlith.model.entity.ProjectVersion
import io.reqlith.model.entity.ReviewAssignment
import io.reqlith.model.entity.ReviewComment
import io.reqlith.model.entity.Suggestion
import io.reqlith.model.entity.SuggestionStatus
import io.reqlith.model.entity.VersionStatus
import io.reqlith.repository.ProjectRepository
import io.reqlith.repository.ProjectVersionRepository
import io.reqlith.repository.ReviewAssignmentRepository
import io.reqlith.repository.ReviewCommentRepository
import io.reqlith.repository.SuggestionRepository
import java.time.Clock
import java.time.Instant
import java.util.UUID
import org.springframework.security.access.AccessDeniedException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

private val logger = KotlinLogging.logger {}

/**
 * The review lifecycle of a project:
 *
 * ```
 * Draft --submit--> In review --all accept--> Approved vN (immutable) --any edit--> Draft (next version)
 *                   In review --changes / reject--> Draft
 *                   In review --withdraw--> Draft
 * ```
 *
 * Submitting freezes the working copy into a candidate snapshot (vN-rcK). Reviewers only ever see that
 * frozen candidate, and the working copy stays read-only until the round closes.
 */
@Service
class ReviewService(
    private val projects: ProjectRepository,
    private val projectService: ProjectService,
    private val versions: ProjectVersionRepository,
    private val versionService: VersionService,
    private val assignments: ReviewAssignmentRepository,
    private val suggestions: SuggestionRepository,
    private val comments: ReviewCommentRepository,
    private val documents: ProjectDocumentService,
    private val elements: ElementService,
    private val memberships: MembershipService,
    private val codec: SnapshotCodec,
    private val access: ProjectAccess,
    private val clock: Clock,
) {
    @Transactional
    fun submit(owner: CurrentUser, project: Project, reviewerIds: Collection<UUID>): ProjectVersion {
        access.requireOwner(project, owner)
        project.requireEditable()
        val reviewers = reviewerIds.toSet()
        if (reviewers.isEmpty()) throw ValidationException("reviewers", "Choose at least one reviewer")
        if (owner.userId in reviewers) throw ValidationException("reviewers", "You cannot review your own submission")
        val memberIds = memberships.members(project).map { it.userId }.toSet()
        if (!memberIds.containsAll(reviewers)) throw ValidationException("reviewers", "Reviewers must be members of the project")

        val snapshot = codec.write(documents.fromWorkingCopy(project))
        val approved = versionService.latestApproved(project)
        if (approved != null && approved.contentHash == snapshot.hash) {
            throw ValidationException(null, "Nothing changed since ${approved.label}. Edit the project before submitting it again.")
        }
        val target = (project.latestApprovedVersion ?: 0) + 1
        val candidateNo = (versions.findByProjectIdAndTargetVersion(project.id, target).maxOfOrNull { it.candidateNo } ?: 0) + 1
        val version = versions.save(
            ProjectVersion(
                projectId = project.id,
                targetVersion = target,
                candidateNo = candidateNo,
                snapshot = snapshot.json,
                schemaVersion = snapshot.schemaVersion,
                contentHash = snapshot.hash,
                basedOnVersion = project.latestApprovedVersion,
                submittedBy = owner.userId,
                submittedAt = Instant.now(clock),
            ),
        )
        reviewers.forEach { assignments.save(ReviewAssignment(AssignmentId(version.id, it))) }
        project.status = ProjectStatus.IN_REVIEW
        project.currentCandidateId = version.id
        projects.save(project)
        logger.info { "Review submitted project=${project.key} candidate=${version.label} reviewers=${reviewers.size} basedOn=${version.basedOnVersion?.let { "v$it" } ?: "-"}" }
        logger.debug { "Candidate snapshot project=${project.key} candidate=${version.label} hash=${snapshot.hash} schema=${snapshot.schemaVersion} bytes=${snapshot.json.length}" }
        return version
    }

    /**
     * Records a reviewer's decision. A single "changes" or "reject" closes the round; when every
     * assigned reviewer has accepted, the candidate becomes the next approved version.
     */
    @Transactional
    fun decide(reviewer: CurrentUser, project: Project, decision: Decision, comment: String?): ProjectVersion {
        require(decision != Decision.PENDING)
        val version = requireOpenCandidate(project)
        val assignment = assignments.findById(AssignmentId(version.id, reviewer.userId)).orElse(null)
            ?: throw AccessDeniedException("You are not a reviewer of ${version.label}")
        if (assignment.decision != Decision.PENDING) throw ValidationException(null, "You already decided on ${version.label}")
        val text = comment?.trim()?.ifEmpty { null }
        val hasSuggestions = suggestions.findByVersionIdOrderByCreatedAt(version.id).any { it.authorId == reviewer.userId }
        if (decision == Decision.REJECT && text == null) throw ValidationException("comment", "Explain why you reject this version")
        if (decision == Decision.CHANGES && text == null && !hasSuggestions) {
            throw ValidationException("comment", "Describe the changes you need, or suggest wording on the elements first")
        }

        val now = Instant.now(clock)
        logger.info { "Review decision project=${project.key} candidate=${version.label} decision=$decision" }
        assignment.decision = decision
        assignment.commentMd = text
        assignment.decidedAt = now

        val all = assignments.findByIdVersionId(version.id)
        when {
            decision == Decision.REJECT -> closeRound(project, version, VersionStatus.REJECTED, now)
            decision == Decision.CHANGES -> closeRound(project, version, VersionStatus.CHANGES_REQUESTED, now)
            all.all { it.decision == Decision.ACCEPT } -> {
                closeRound(project, version, VersionStatus.APPROVED, now)
                project.latestApprovedVersion = version.versionNo
                projects.save(project)
                logger.info { "Version approved project=${project.key} version=${version.label} hash=${version.contentHash}" }
            }
        }
        return version
    }

    @Transactional
    fun withdraw(owner: CurrentUser, project: Project) {
        access.requireOwner(project, owner)
        val version = requireOpenCandidate(project)
        closeRound(project, version, VersionStatus.WITHDRAWN, Instant.now(clock))
    }

    @Transactional
    fun suggest(reviewer: CurrentUser, project: Project, elementKey: String, proposedMd: String?, comment: String?): Suggestion {
        val version = requireOpenCandidate(project)
        requireAssigned(version, reviewer)
        val document = versionService.document(version)
        if (elementKey != Suggestion.BASIC_INFO && document.element(elementKey) == null) {
            throw NotFoundException("$elementKey is not part of ${version.label}")
        }
        val proposed = proposedMd?.replace("\r\n", "\n")?.ifBlank { null }
        val text = comment?.trim()?.ifEmpty { null }
        if (proposed == null && text == null) throw ValidationException("proposedMd", "Suggest new wording or add a comment")
        return suggestions.save(Suggestion(version.id, elementKey, proposed, text, reviewer.userId, Instant.now(clock)))
            .also { logger.info { "Suggestion added project=${project.key} candidate=${version.label} target=$elementKey wording=${proposed != null}" } }
    }

    /** Comments are open to project members and assigned reviewers while the review runs. */
    @Transactional
    fun comment(user: CurrentUser, project: Project, elementKey: String?, body: String): ReviewComment {
        val version = requireOpenCandidate(project)
        val assigned = assignments.existsById(AssignmentId(version.id, user.userId))
        if (!assigned && access.roleOf(project, user) == null) throw AccessDeniedException("Only members of ${project.key} can comment")
        if (body.isBlank()) throw ValidationException("body", "Write a comment first")
        return comments.save(ReviewComment(version.id, elementKey?.ifBlank { null }, user.userId, body.trim(), Instant.now(clock)))
            .also { logger.info { "Review comment added project=${project.key} candidate=${version.label} target=${it.elementKey ?: "general"}" } }
    }

    /**
     * After a round closes, authors apply suggestions to the working copy or dismiss them. The next
     * round then shows how earlier feedback was handled.
     */
    @Transactional
    fun resolve(user: CurrentUser, project: Project, suggestionId: UUID, apply: Boolean) {
        access.requireEditor(project, user)
        project.requireEditable()
        val suggestion = suggestions.findById(suggestionId).orElseThrow { NotFoundException("Suggestion not found") }
        val version = versions.findById(suggestion.versionId).orElseThrow()
        require(version.projectId == project.id)
        if (suggestion.status != SuggestionStatus.OPEN) throw ValidationException(null, "This suggestion was already handled")
        if (apply) {
            val proposed = suggestion.proposedMd ?: throw ValidationException(null, "This suggestion has no wording to apply")
            if (suggestion.elementKey == Suggestion.BASIC_INFO) {
                projectService.updateBasicInfo(user, project, proposed, project.rowVersion)
            } else {
                val element = elements.get(project, suggestion.elementKey)
                elements.update(
                    user, project, element.key,
                    ElementContent(element.title, proposed, element.priority, element.tags.toSet(), element.links.toSet()),
                    element.rowVersion,
                )
            }
        }
        suggestion.status = if (apply) SuggestionStatus.APPLIED else SuggestionStatus.DISMISSED
        suggestion.resolvedAt = Instant.now(clock)
        suggestion.resolvedBy = user.userId
        logger.info { "Suggestion ${if (apply) "applied" else "dismissed"} project=${project.key} round=${version.label} target=${suggestion.elementKey}" }
    }

    // ------------------------------------------------------------------ queries

    fun assignments(version: ProjectVersion): List<ReviewAssignment> = assignments.findByIdVersionId(version.id)

    fun suggestions(version: ProjectVersion): List<Suggestion> = suggestions.findByVersionIdOrderByCreatedAt(version.id)

    fun comments(version: ProjectVersion): List<ReviewComment> = comments.findByVersionIdOrderByCreatedAt(version.id)

    /** Suggestions from closed rounds that authors still have to apply or dismiss. */
    fun openSuggestions(project: Project): List<Suggestion> {
        val closed = versionService.history(project).filter { it.status != VersionStatus.IN_REVIEW }.map { it.id }
        return if (closed.isEmpty()) emptyList() else suggestions.findByVersionIdInAndStatus(closed, SuggestionStatus.OPEN)
    }

    /** Open review rounds waiting for this user's decision. */
    fun pendingFor(user: CurrentUser): List<ProjectVersion> {
        val pending = assignments.findByIdReviewerIdAndDecision(user.userId, Decision.PENDING).map { it.versionId }
        return versions.findAllById(pending).filter { it.status == VersionStatus.IN_REVIEW }
    }

    private fun closeRound(project: Project, version: ProjectVersion, status: VersionStatus, at: Instant) {
        version.close(status, at)
        versions.save(version)
        if (status != VersionStatus.APPROVED) logger.info { "Review round closed project=${project.key} candidate=${version.label} result=$status" }
        project.status = ProjectStatus.DRAFT
        project.currentCandidateId = null
        projects.save(project)
    }

    private fun requireOpenCandidate(project: Project): ProjectVersion {
        if (project.status != ProjectStatus.IN_REVIEW) throw ValidationException(null, "${project.key} is not in review")
        return versionService.openCandidate(project) ?: throw ValidationException(null, "${project.key} has no open review")
    }

    private fun requireAssigned(version: ProjectVersion, user: CurrentUser) {
        if (!assignments.existsById(AssignmentId(version.id, user.userId))) {
            throw AccessDeniedException("You are not a reviewer of ${version.label}")
        }
    }
}
