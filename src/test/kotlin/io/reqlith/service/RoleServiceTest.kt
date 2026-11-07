package io.reqlith.service

import io.mockk.every
import io.mockk.mockk
import io.reqlith.exception.ValidationException
import io.reqlith.model.entity.MemberRole
import io.reqlith.model.entity.ProjectRole
import io.reqlith.model.entity.ProjectRoleId
import io.reqlith.model.entity.Role
import io.reqlith.repository.KeyCounter
import io.reqlith.repository.ProjectRoleRepository
import io.reqlith.repository.RoleRepository
import io.reqlith.support.UnitFixtures
import io.reqlith.support.UnitFixtures.NOW
import org.junit.jupiter.api.Test
import org.springframework.security.access.AccessDeniedException
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RoleServiceTest {
    private val roles = mockk<RoleRepository> { every { save(any()) } answers { firstArg() } }
    private val projectRoles = mockk<ProjectRoleRepository> { every { save(any()) } answers { firstArg() } }
    private val projectService = mockk<ProjectService>(relaxUnitFun = true)
    private val access = mockk<ProjectAccess>(relaxUnitFun = true)
    private val keyCounter = mockk<KeyCounter> { every { next("ROLE_KEY", "RO") } returns 3 }
    private val service = RoleService(roles, projectRoles, projectService, access, keyCounter, UnitFixtures.clock)

    private val editor = UnitFixtures.user("Olive Owens")
    private val viewer = UnitFixtures.user("Vera Voss")
    private val role = Role("RO-01", "Customer", "Buys", NOW, editor.userId)

    init {
        every { access.isEditorAnywhere(editor) } returns true
        every { access.isEditorAnywhere(viewer) } returns false
        every { roles.findByName(any()) } returns null
    }

    @Test
    fun `editors create roles with the next global key`() {
        assertEquals("RO-03", service.create(editor, "  Claims handler ", "Handles claims").key)
        assertFailsWith<AccessDeniedException> { service.create(viewer, "Auditor", null) }
    }

    @Test
    fun `role names are required, bounded and unique regardless of case`() {
        assertFailsWith<ValidationException> { service.create(editor, " ", null) }
        assertFailsWith<ValidationException> { service.create(editor, "x".repeat(201), null) }
        every { roles.findByName("customer") } returns role
        assertFailsWith<ValidationException> { service.create(editor, "customer", null) }
    }

    @Test
    fun `unused roles are editable by role creators, used ones only by their projects' editors`() {
        every { projectRoles.findByIdRoleId(role.id) } returns emptyList()
        assertTrue(service.canEditGlobal(editor, role))
        assertFalse(service.canEditGlobal(viewer, role))

        val projectId = UUID.randomUUID()
        every { projectRoles.findByIdRoleId(role.id) } returns listOf(ProjectRole(ProjectRoleId(projectId, role.id), null, 1))
        every { access.roleOf(projectId, editor.userId) } returns null
        every { access.roleOf(projectId, viewer.userId) } returns MemberRole.CONTRIBUTOR
        assertFalse(service.canEditGlobal(editor, role))
        assertTrue(service.canEditGlobal(viewer, role))
        assertFailsWith<AccessDeniedException> { service.updateGlobal(editor, role, "Customer", "Rewritten") }
    }

    @Test
    fun `a role is added to a project once`() {
        val project = UnitFixtures.project(editor)
        every { roles.findById(role.id) } returns java.util.Optional.of(role)
        every { projectRoles.existsById(ProjectRoleId(project.id, role.id)) } returnsMany listOf(false, true)
        every { projectRoles.maxSortOrder(project.id) } returns 0
        service.addExisting(editor, project, role.id, "Requests refunds")
        assertFailsWith<ValidationException> { service.addExisting(editor, project, role.id, null) }
    }

    @Test
    fun `project roles combine the global role with its context and can be changed or removed`() {
        val project = UnitFixtures.project(editor)
        val link = ProjectRole(ProjectRoleId(project.id, role.id), "Requests refunds", 1)
        every { projectRoles.maxSortOrder(project.id) } returns 1
        every { projectRoles.findByIdProjectIdOrderBySortOrder(project.id) } returns listOf(link)
        every { roles.findAllById(listOf(role.id)) } returns listOf(role)
        every { projectRoles.findById(link.id) } returns java.util.Optional.of(link)
        every { projectRoles.deleteById(link.id) } returns Unit
        every { projectRoles.findAll() } returns listOf(link)

        val created = service.createAndAdd(editor, project, "Clerk", "Back office", "Approves refunds")
        assertEquals("RO-03", created.key)
        io.mockk.verify { projectRoles.save(match { it.contextMd == "Approves refunds" && it.sortOrder == 2 }) }

        assertEquals(listOf("Customer" to "Requests refunds"), service.forProject(project).map { it.role.name to it.contextMd })
        assertEquals(mapOf(role.id to 1), service.usage())

        service.updateContext(editor, project, role.id, "  ")
        assertEquals(null, link.contextMd)
        service.removeFromProject(editor, project, role.id)
        io.mockk.verify {
            projectRoles.deleteById(link.id)
            projectService.touch(project, editor)
        }
    }
}
