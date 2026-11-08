package io.reqlith.service

import io.mockk.every
import io.mockk.mockk
import io.reqlith.exception.NotFoundException
import io.reqlith.exception.ValidationException
import io.reqlith.model.entity.AccessToken
import io.reqlith.model.entity.AppUser
import io.reqlith.model.entity.LinkSystem
import io.reqlith.model.entity.MemberId
import io.reqlith.model.entity.MemberRole
import io.reqlith.model.entity.ProjectMember
import io.reqlith.repository.AccessTokenRepository
import io.reqlith.repository.AppUserRepository
import io.reqlith.repository.ExternalLinkRepository
import io.reqlith.repository.ProjectMemberRepository
import io.reqlith.repository.WorkspaceRepository
import io.reqlith.support.UnitFixtures
import io.reqlith.support.UnitFixtures.NOW
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.security.access.AccessDeniedException
import java.security.MessageDigest
import java.util.Optional
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Unit tests of the smaller services, grouped per service. */
class SmallServicesTest {

    @Nested
    inner class AccessTokens {
        private val repository = mockk<AccessTokenRepository> { every { save(any()) } answers { firstArg() } }
        private val service = AccessTokenService(repository, UnitFixtures.clock)
        private val owner = UnitFixtures.user()

        @Test
        fun `only a hash of the token is stored and the value starts with rql_`() {
            val created = service.create(owner, "  Backlog agent ")
            assertTrue(created.value.startsWith("rql_"))
            assertEquals("Backlog agent", created.token.name)
            assertEquals(created.value.take(12), created.token.tokenPrefix)
            assertNotEquals(created.value, created.token.tokenHash)
            assertEquals(sha256(created.value), created.token.tokenHash)
            assertNotEquals(created.value, service.create(owner, "Other").value)
            assertFailsWith<ValidationException> { service.create(owner, " ") }
        }

        @Test
        fun `authentication accepts active tokens only and records their use`() {
            val token = AccessToken(owner.userId, "agent", sha256("rql_value"), "rql_value", NOW)
            every { repository.findByTokenHash(sha256("rql_value")) } returns token
            every { repository.findByTokenHash(sha256("rql_other")) } returns null
            assertEquals(NOW, service.authenticate("rql_value")?.lastUsedAt)
            assertNull(service.authenticate("rql_other"))
            token.revokedAt = NOW
            assertNull(service.authenticate("rql_value"))
        }

        @Test
        fun `tokens are revoked by their owner only and hidden afterwards`() {
            val token = AccessToken(owner.userId, "agent", "h", "rql_", NOW)
            every { repository.findById(token.id) } returns Optional.of(token)
            every { repository.findByUserIdOrderByCreatedAtDesc(owner.userId) } returns listOf(token)
            assertFailsWith<NotFoundException> { service.revoke(UnitFixtures.user("Other"), token.id) }
            service.revoke(owner, token.id)
            assertEquals(NOW, token.revokedAt)
            assertTrue(service.forUser(owner).isEmpty())
        }

        private fun sha256(value: String) = MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    @Nested
    inner class ExternalLinks {
        private val repository = mockk<ExternalLinkRepository> { every { save(any()) } answers { firstArg() } }
        private val access = mockk<ProjectAccess>(relaxUnitFun = true)
        private val service = ExternalLinkService(repository, access, UnitFixtures.clock)
        private val owner = UnitFixtures.user()
        private val project = UnitFixtures.project(owner).also { it.latestApprovedVersion = 2 }

        @Test
        fun `only absolute http(s) links are accepted`() {
            listOf("javascript:alert(1)", "ftp://host/x", "/relative", "https://", "not a url").forEach { url ->
                assertFailsWith<ValidationException>(url) { service.add(owner, project, LinkSystem.URL, url, null, null, null) }
            }
        }

        @Test
        fun `labels default to the last path segment and scopes are normalized`() {
            val link = service.add(owner, project, LinkSystem.JIRA, " https://jira.example.com/browse/PAY-2291 ", " ", null, " fr-12 ")
            assertEquals("PAY-2291", link.label)
            assertEquals("FR-12", link.scope)
            assertEquals("v2", service.add(owner, project, LinkSystem.ADO, "https://dev.azure.com/x/1", "Feature", 2, null).scope)
            assertFailsWith<ValidationException> { service.add(owner, project, LinkSystem.ADO, "https://dev.azure.com/x/1", null, 3, null) }
        }
    }

    @Nested
    inner class Workspaces {
        private val repository = mockk<WorkspaceRepository> { every { save(any()) } answers { firstArg() } }
        private val service = WorkspaceService(repository, UnitFixtures.clock)

        @Test
        fun `any signed-in user creates workspaces with short, unique upper-case keys`() {
            every { repository.existsByKey(any()) } returns false
            assertEquals("PAY", service.create(UnitFixtures.user(), " pay ", " Payments ").key)
            assertFailsWith<ValidationException> { service.create(UnitFixtures.user(), "P", "Too short") }
            assertFailsWith<ValidationException> { service.create(UnitFixtures.user(), "PAY-1", "Dash") }
            assertFailsWith<ValidationException> { service.create(UnitFixtures.user(), "OPS", " ") }
            every { repository.existsByKey("FIN") } returns true
            assertFailsWith<ValidationException> { service.create(UnitFixtures.user(), "FIN", "Finance") }
        }
    }

    @Nested
    inner class Users {
        private val repository = mockk<AppUserRepository> { every { save(any()) } answers { firstArg() } }
        private val service = UserService(repository, UnitFixtures.clock)

        @Test
        fun `first login creates the user, later logins refresh name and e-mail`() {
            every { repository.findBySubject("sub-1") } returns null
            val created = service.provision("sub-1", "olive@reqlith.test", "Olive Owens")
            assertEquals("OO", created.initials)
            assertEquals(NOW, created.lastLoginAt)

            every { repository.findBySubject("sub-1") } returns created
            val refreshed = service.provision("sub-1", "olive.owens@reqlith.test", " ")
            assertEquals(created.id, refreshed.id)
            assertEquals("olive.owens@reqlith.test", refreshed.displayName, "falls back to the e-mail when the name is blank")
        }

        @Test
        fun `users are looked up in one query, and not at all without ids`() {
            assertEquals(emptyMap(), service.byIds(emptyList()))
            val user = AppUser("sub", null, "Vera Voss", "VV", NOW)
            every { repository.findAllById(setOf(user.id)) } returns listOf(user)
            assertEquals(mapOf(user.id to user), service.byIds(listOf(user.id, user.id)))
        }
    }

    @Nested
    inner class Access {
        private val members = mockk<ProjectMemberRepository>()
        private val access = ProjectAccess(members)
        private val project = UnitFixtures.project()
        private val user = UnitFixtures.user()

        private fun memberAs(role: MemberRole?) {
            every { members.findById(MemberId(project.id, user.userId)) } returns
                Optional.ofNullable(role?.let { ProjectMember(MemberId(project.id, user.userId), it, NOW) })
        }

        @Test
        fun `owners and contributors edit, owners manage, everybody else only reads`() {
            memberAs(MemberRole.OWNER)
            assertTrue(access.canEdit(project, user) && access.isOwner(project, user))
            memberAs(MemberRole.CONTRIBUTOR)
            assertTrue(access.canEdit(project, user))
            assertFailsWith<AccessDeniedException> { access.requireOwner(project, user) }
            memberAs(MemberRole.REVIEWER)
            assertFalse(access.canEdit(project, user))
            memberAs(null)
            assertFailsWith<AccessDeniedException> { access.requireEditor(project, user) }
        }

        @Test
        fun `editor anywhere means owner or contributor in at least one project`() {
            every { members.findByIdUserId(user.userId) } returns listOf(ProjectMember(MemberId(project.id, user.userId), MemberRole.REVIEWER, NOW))
            assertFalse(access.isEditorAnywhere(user))
            every { members.findByIdUserId(user.userId) } returns listOf(ProjectMember(MemberId(project.id, user.userId), MemberRole.CONTRIBUTOR, NOW))
            assertTrue(access.isEditorAnywhere(user))
        }
    }
}
