package io.reqlith.service

import io.mockk.every
import io.mockk.mockk
import io.reqlith.exception.NotFoundException
import io.reqlith.model.entity.ProjectVersion
import io.reqlith.model.entity.VersionStatus
import io.reqlith.repository.ProjectVersionRepository
import io.reqlith.support.UnitFixtures
import io.reqlith.support.UnitFixtures.NOW
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class VersionServiceTest {
    private val versions = mockk<ProjectVersionRepository>()
    private val documents = mockk<ProjectDocumentService>()
    private val codec = SnapshotCodec()
    private val service = VersionService(versions, codec, documents)

    private val project = UnitFixtures.project()
    private val document = UnitFixtures.document(project, UnitFixtures.element("BR-01"))
    private val v1 = codec.write(document).let { ProjectVersion(project.id, 1, 1, it.json, 1, it.hash, null, project.createdBy, NOW) }
        .also { it.close(VersionStatus.APPROVED, NOW) }

    @Test
    fun `versions are found by slug, approved or candidate`() {
        val rc = ProjectVersion(project.id, 2, 3, v1.snapshot, 1, v1.contentHash, 1, project.createdBy, NOW)
        every { versions.findByProjectIdAndVersionNo(project.id, 1) } returns v1
        every { versions.findByProjectIdAndTargetVersionAndCandidateNo(project.id, 2, 3) } returns rc
        every { versions.findByProjectIdAndVersionNo(project.id, 9) } returns null
        every { versions.findSnapshot(v1.id) } returns v1.snapshot

        assertSame(v1, service.bySlug(project, "1"))
        assertSame(rc, service.bySlug(project, "2-rc3"))
        assertFailsWith<NotFoundException> { service.bySlug(project, "9") }
        assertFailsWith<NotFoundException> { service.bySlug(project, "latest") }
        assertEquals(document, service.document(v1))
    }

    @Test
    fun `the working copy has changes when its hash differs from the approved one`() {
        every { versions.findByProjectIdAndVersionNo(project.id, 1) } returns v1
        assertTrue(service.hasChangesSinceApproval(project), "nothing approved yet")

        project.latestApprovedVersion = 1
        every { documents.fromWorkingCopy(project) } returns document
        assertFalse(service.hasChangesSinceApproval(project))
        every { documents.fromWorkingCopy(project) } returns document.copy(basicInfo = "changed")
        assertTrue(service.hasChangesSinceApproval(project))
    }
}
