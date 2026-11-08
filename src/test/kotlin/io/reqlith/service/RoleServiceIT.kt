package io.reqlith.service

import io.reqlith.exception.ValidationException
import io.reqlith.support.Fixtures
import io.reqlith.support.IntegrationTest
import io.reqlith.support.TestUsers
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.access.AccessDeniedException
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@IntegrationTest
class RoleServiceIT(
    @Autowired private val roles: RoleService,
    @Autowired private val documents: ProjectDocumentService,
    @Autowired private val users: TestUsers,
    @Autowired private val fixtures: Fixtures,
) {
    @Test
    fun `a role is shared across projects with a context per project`() {
        val owner = users.create()
        val first = fixtures.project(owner, "First")
        val second = fixtures.project(owner, "Second")
        val name = "Customer " + UUID.randomUUID().toString().take(6)

        val role = roles.createAndAdd(owner, first, name, "Buys things", "Requests refunds")
        roles.addExisting(owner, second, role.id, "Tracks loyalty points")

        assertEquals("Requests refunds", documents.fromWorkingCopy(first).roles.single().contextInProject)
        val inSecond = documents.fromWorkingCopy(second).roles.single()
        assertEquals("Tracks loyalty points", inSecond.contextInProject)
        assertEquals("Buys things", inSecond.globalDescription)
        assertEquals(2, roles.usage()[role.id])

        assertFailsWith<ValidationException> { roles.createAndAdd(owner, second, name.uppercase(), null, null) }
        assertFailsWith<ValidationException> { roles.addExisting(owner, second, role.id, null) }
    }

    @Test
    fun `only editors of a project using the role change its global description`() {
        val owner = users.create()
        val outsider = users.create("Oscar Ortiz")
        val project = fixtures.project(owner)
        val role = roles.createAndAdd(owner, project, "Agent " + UUID.randomUUID().toString().take(6), "Old", null)

        assertFailsWith<AccessDeniedException> { roles.updateGlobal(outsider, role, role.name, "Hijacked") }
        roles.updateGlobal(owner, role, role.name, "New")
        assertEquals("New", roles.get(role.key).descriptionMd)
    }

    @Test
    fun `editors create standalone roles that stay editable until a project uses them`() {
        val editor = users.create("Olive Owens")
        val otherEditor = users.create("Oscar Ortiz")
        val viewer = users.create("Vera Voss")
        fixtures.project(editor)
        val otherProject = fixtures.project(otherEditor)

        val role = roles.create(editor, "Auditor " + UUID.randomUUID().toString().take(6), "Checks refunds")
        assertTrue(roles.canEditGlobal(otherEditor, role), "an unused role can be refined by any editor")
        assertFailsWith<AccessDeniedException> { roles.create(viewer, "Nope " + UUID.randomUUID(), null) }
        assertFailsWith<ValidationException> { roles.create(editor, role.name.lowercase(), null) }

        roles.addExisting(otherEditor, otherProject, role.id, "Reviews payouts")
        assertFalse(roles.canEditGlobal(editor, role), "once used, only that project's editors change it")
        assertTrue(roles.canEditGlobal(otherEditor, role))
    }
}
