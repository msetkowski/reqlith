package io.reqlith.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.reqlith.exception.ConcurrentEditException
import io.reqlith.exception.NotFoundException
import io.reqlith.exception.ValidationException
import io.reqlith.model.dto.ProjectDetails
import io.reqlith.model.entity.MemberRole
import io.reqlith.repository.KeyCounter
import io.reqlith.repository.ProjectMemberRepository
import io.reqlith.repository.ProjectRepository
import io.reqlith.repository.WorkspaceRepository
import io.reqlith.support.UnitFixtures
import org.junit.jupiter.api.Test
import java.util.Optional
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ProjectServiceTest {
    private val projects = mockk<ProjectRepository> { every { save(any()) } answers { firstArg() } }
    private val workspaces = mockk<WorkspaceRepository>()
    private val members = mockk<ProjectMemberRepository> { every { save(any()) } answers { firstArg() } }
    private val memberships = mockk<MembershipService>()
    private val access = mockk<ProjectAccess>(relaxUnitFun = true)
    private val keyCounter = mockk<KeyCounter>()
    private val service = ProjectService(projects, workspaces, members, memberships, access, keyCounter, UnitFixtures.clock)

    private val owner = UnitFixtures.user()
    private val details = ProjectDetails(" Self-service refunds ", " ", setOf("refunds"), setOf("Payments"))

    @Test
    fun `a new project gets a workspace key, the basic information template and its creator as owner`() {
        val ws = UnitFixtures.workspace
        every { workspaces.findById(ws.id) } returns Optional.of(ws)
        every { keyCounter.next("PROJECT_KEY:${ws.id}", "PRJ") } returns 14
        every { memberships.invite(owner, any(), "carla@reqlith.test", MemberRole.CONTRIBUTOR) } returns mockk()

        val project = service.create(owner, ws.id, details, listOf("carla@reqlith.test" to MemberRole.CONTRIBUTOR))

        assertEquals("PRJ-PAY-014", project.key)
        assertEquals("Self-service refunds", project.title)
        assertEquals(null, project.summary)
        assertTrue(project.basicInfoMd!!.contains("## Success metrics"))
        verify { members.save(match { it.userId == owner.userId && it.role == MemberRole.OWNER }) }
        verify { memberships.invite(owner, project, "carla@reqlith.test", MemberRole.CONTRIBUTOR) }
    }

    @Test
    fun `names are required, workspaces must exist, and stale forms are refused`() {
        assertFailsWith<ValidationException> { service.create(owner, UUID.randomUUID(), details.copy(title = " ")) }
        every { workspaces.findById(any()) } returns Optional.empty()
        assertFailsWith<ValidationException> { service.create(owner, UUID.randomUUID(), details) }

        val project = UnitFixtures.project(owner)
        assertFailsWith<ConcurrentEditException> { service.updateDetails(owner, project, details, project.rowVersion + 1) }
        service.updateBasicInfo(owner, project, "a\r\nb", project.rowVersion)
        assertEquals("a\nb", project.basicInfoMd)
    }

    @Test
    fun `unknown project keys are reported as not found`() {
        every { projects.findByKey("PRJ-X-001") } returns null
        assertFailsWith<NotFoundException> { service.get("PRJ-X-001") }
    }

    @Test
    fun `tag lists accept commas, semicolons, new lines and hashes`() {
        assertEquals(sortedSetOf("Q1-2027", "refunds", "ux"), ProjectService.parseList("#refunds, ux;\n #Q1-2027 ,, "))
        assertEquals(emptySet(), ProjectService.parseList(null))
    }
}
