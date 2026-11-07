package io.reqlith.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import io.reqlith.exception.NotFoundException
import io.reqlith.exception.ProjectLockedException
import io.reqlith.exception.ValidationException
import io.reqlith.model.entity.AssignmentId
import io.reqlith.model.entity.Decision
import io.reqlith.model.entity.MemberId
import io.reqlith.model.entity.MemberRole
import io.reqlith.model.entity.ProjectMember
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
import io.reqlith.support.UnitFixtures
import io.reqlith.support.UnitFixtures.NOW
import io.reqlith.support.UnitFixtures.TestUser
import org.junit.jupiter.api.Test
import org.springframework.security.access.AccessDeniedException
import java.util.Optional
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class ReviewServiceTest {
    private val projects = mockk<ProjectRepository> { every { save(any()) } answers { firstArg() } }
    private val projectService = mockk<ProjectService>(relaxUnitFun = true)
    private val versions = mockk<ProjectVersionRepository> { every { save(any()) } answers { firstArg() } }
    private val versionService = mockk<VersionService>()
    private val assignments = mockk<ReviewAssignmentRepository> { every { save(any()) } answers { firstArg() } }
    private val suggestions = mockk<SuggestionRepository> { every { save(any()) } answers { firstArg() } }
    private val comments = mockk<ReviewCommentRepository> { every { save(any()) } answers { firstArg() } }
    private val documents = mockk<ProjectDocumentService>()
    private val elements = mockk<ElementService>()
    private val memberships = mockk<MembershipService>()
    private val access = mockk<ProjectAccess>(relaxUnitFun = true)
    private val codec = SnapshotCodec()

    private val service = ReviewService(
        projects, projectService, versions, versionService, assignments, suggestions, comments,
        documents, elements, memberships, codec, access, UnitFixtures.clock,
    )

    private val owner = UnitFixtures.user("Olive Owens")
    private val r1 = UnitFixtures.user("Rita Reyes")
    private val r2 = UnitFixtures.user("Raj Rao")
    private val project = UnitFixtures.project(owner)
    private val document = UnitFixtures.document(project, UnitFixtures.element("BR-01"))

    init {
        every { memberships.members(project) } returns listOf(owner, r1, r2).map { ProjectMember(MemberId(project.id, it.userId), MemberRole.REVIEWER, NOW) }
        every { documents.fromWorkingCopy(project) } returns document
        every { versionService.latestApproved(project) } returns null
        every { versions.findByProjectIdAndTargetVersion(project.id, any()) } returns emptyList()
    }

    private fun candidate(target: Int = 1, rc: Int = 1, basedOn: Int? = null): ProjectVersion {
        val snapshot = codec.write(document)
        return ProjectVersion(project.id, target, rc, snapshot.json, 1, snapshot.hash, basedOn, owner.userId, NOW)
    }

    /** The project is in review of [version] with these reviewers and decisions. */
    private fun inReview(version: ProjectVersion, vararg reviewers: Pair<TestUser, Decision>) {
        project.status = ProjectStatus.IN_REVIEW
        project.currentCandidateId = version.id
        every { versionService.openCandidate(project) } returns version
        every { versionService.document(version) } returns document
        val rows = reviewers.map { (user, decision) -> ReviewAssignment(AssignmentId(version.id, user.userId)).also { it.decision = decision } }
        every { assignments.findByIdVersionId(version.id) } returns rows
        every { assignments.findById(any()) } answers { Optional.ofNullable(rows.firstOrNull { it.id == firstArg<AssignmentId>() }) }
        every { assignments.existsById(any()) } answers { rows.any { it.id == firstArg<AssignmentId>() } }
        every { suggestions.findByVersionIdOrderByCreatedAt(version.id) } returns emptyList()
    }

    // ------------------------------------------------------------------ submit

    @Test
    fun `submit freezes the working copy as the next candidate and locks the project`() {
        every { versions.findByProjectIdAndTargetVersion(project.id, 1) } returns listOf(candidate(rc = 1), candidate(rc = 2))
        val saved = slot<ProjectVersion>()
        every { versions.save(capture(saved)) } answers { firstArg() }

        val version = service.submit(owner, project, listOf(r1.userId, r2.userId))

        assertEquals("v1-rc3", version.label)
        assertEquals(codec.write(document).hash, saved.captured.contentHash)
        assertNull(saved.captured.basedOnVersion)
        assertEquals(ProjectStatus.IN_REVIEW, project.status)
        assertEquals(version.id, project.currentCandidateId)
        verify(exactly = 2) { assignments.save(any()) }
    }

    @Test
    fun `submit needs reviewers who are other members of the project`() {
        assertFailsWith<ValidationException> { service.submit(owner, project, emptyList()) }
        assertFailsWith<ValidationException> { service.submit(owner, project, listOf(owner.userId)) }
        assertFailsWith<ValidationException> { service.submit(owner, project, listOf(UUID.randomUUID())) }
    }

    @Test
    fun `unchanged content cannot be submitted again after approval`() {
        val approved = candidate().also { it.close(VersionStatus.APPROVED, NOW) }
        project.latestApprovedVersion = 1
        every { versionService.latestApproved(project) } returns approved

        val e = assertFailsWith<ValidationException> { service.submit(owner, project, listOf(r1.userId)) }
        assertEquals("Nothing changed since v1. Edit the project before submitting it again.", e.message)
    }

    @Test
    fun `a project already in review cannot be submitted`() {
        project.status = ProjectStatus.IN_REVIEW
        assertFailsWith<ProjectLockedException> { service.submit(owner, project, listOf(r1.userId)) }
    }

    // ------------------------------------------------------------------ decide

    @Test
    fun `the version is approved only when every reviewer accepted`() {
        val version = candidate()
        inReview(version, r1 to Decision.PENDING, r2 to Decision.ACCEPT)

        service.decide(r1, project, Decision.ACCEPT, null)

        assertEquals(VersionStatus.APPROVED, version.status)
        assertEquals("v1", version.label)
        assertEquals(1, project.latestApprovedVersion)
        assertEquals(ProjectStatus.DRAFT, project.status)
        assertNull(project.currentCandidateId)
    }

    @Test
    fun `a single accept of two keeps the review open`() {
        val version = candidate()
        inReview(version, r1 to Decision.PENDING, r2 to Decision.PENDING)

        service.decide(r1, project, Decision.ACCEPT, "fine")

        assertEquals(VersionStatus.IN_REVIEW, version.status)
        assertEquals(ProjectStatus.IN_REVIEW, project.status)
    }

    @Test
    fun `one request for changes closes the round and unlocks the working copy`() {
        val version = candidate()
        inReview(version, r1 to Decision.PENDING, r2 to Decision.PENDING)

        service.decide(r1, project, Decision.CHANGES, "Add scenarios")

        assertEquals(VersionStatus.CHANGES_REQUESTED, version.status)
        assertEquals(ProjectStatus.DRAFT, project.status)
        assertNull(version.versionNo)
    }

    @Test
    fun `reject needs a reason, changes need a reason or a suggestion`() {
        val version = candidate()
        inReview(version, r1 to Decision.PENDING)

        assertFailsWith<ValidationException> { service.decide(r1, project, Decision.REJECT, "  ") }
        assertFailsWith<ValidationException> { service.decide(r1, project, Decision.CHANGES, null) }

        every { suggestions.findByVersionIdOrderByCreatedAt(version.id) } returns
            listOf(Suggestion(version.id, "BR-01", "Better", null, r1.userId, NOW))
        service.decide(r1, project, Decision.CHANGES, null)
        assertEquals(VersionStatus.CHANGES_REQUESTED, version.status)
    }

    @Test
    fun `only assigned reviewers decide, and only once`() {
        val version = candidate()
        inReview(version, r1 to Decision.ACCEPT, r2 to Decision.PENDING)

        assertFailsWith<AccessDeniedException> { service.decide(owner, project, Decision.ACCEPT, null) }
        assertFailsWith<ValidationException> { service.decide(r1, project, Decision.ACCEPT, null) }
    }

    @Test
    fun `deciding without an open review is refused`() {
        assertFailsWith<ValidationException> { service.decide(r1, project, Decision.ACCEPT, null) }
    }

    @Test
    fun `withdraw closes the round`() {
        val version = candidate()
        inReview(version, r1 to Decision.PENDING)

        service.withdraw(owner, project)

        assertEquals(VersionStatus.WITHDRAWN, version.status)
        assertEquals(ProjectStatus.DRAFT, project.status)
        verify { access.requireOwner(project, owner) }
    }

    // ------------------------------------------------------------------ feedback

    @Test
    fun `suggestions target elements of the candidate and need content`() {
        val version = candidate()
        inReview(version, r1 to Decision.PENDING)

        assertFailsWith<NotFoundException> { service.suggest(r1, project, "FR-99", "x", null) }
        assertFailsWith<ValidationException> { service.suggest(r1, project, "BR-01", " ", " ") }
        assertFailsWith<AccessDeniedException> { service.suggest(r2, project, "BR-01", "x", null) }

        val suggestion = service.suggest(r1, project, Suggestion.BASIC_INFO, "New\\r\\nwording".replace("\\r\\n", "\r\n"), "why")
        assertEquals("New\nwording", suggestion.proposedMd)
    }

    @Test
    fun `members and reviewers may comment, outsiders may not`() {
        val version = candidate()
        inReview(version, r1 to Decision.PENDING)
        val outsider = UnitFixtures.user("Out Sider")
        every { access.roleOf(project, outsider) } returns null
        every { access.roleOf(project, owner) } returns MemberRole.OWNER

        assertEquals("Looks good", service.comment(r1, project, null, " Looks good ").bodyMd)
        assertEquals("BR-01", service.comment(owner, project, "BR-01", "Thanks").elementKey)
        assertFailsWith<AccessDeniedException> { service.comment(outsider, project, null, "hi") }
        assertFailsWith<ValidationException> { service.comment(r1, project, null, " ") }
        verify(exactly = 2) { comments.save(any<ReviewComment>()) }
    }

    @Test
    fun `applying a suggestion to the basic information updates the working copy`() {
        val version = candidate().also { it.close(VersionStatus.CHANGES_REQUESTED, NOW) }
        val suggestion = Suggestion(version.id, Suggestion.BASIC_INFO, "## Problem statement\nBetter", null, r1.userId, NOW)
        every { suggestions.findById(suggestion.id) } returns Optional.of(suggestion)
        every { versions.findById(version.id) } returns Optional.of(version)

        service.resolve(owner, project, suggestion.id, apply = true)

        verify { projectService.updateBasicInfo(owner, project, "## Problem statement\nBetter", project.rowVersion) }
        assertEquals(SuggestionStatus.APPLIED, suggestion.status)
        assertEquals(owner.userId, suggestion.resolvedBy)
        assertFailsWith<ValidationException> { service.resolve(owner, project, suggestion.id, apply = false) }
    }

    @Test
    fun `comment-only suggestions can be dismissed but not applied`() {
        val version = candidate().also { it.close(VersionStatus.REJECTED, NOW) }
        val suggestion = Suggestion(version.id, "BR-01", null, "Unclear", r1.userId, NOW)
        every { suggestions.findById(suggestion.id) } returns Optional.of(suggestion)
        every { versions.findById(version.id) } returns Optional.of(version)

        assertFailsWith<ValidationException> { service.resolve(owner, project, suggestion.id, apply = true) }
        service.resolve(owner, project, suggestion.id, apply = false)
        assertEquals(SuggestionStatus.DISMISSED, suggestion.status)
    }

}
