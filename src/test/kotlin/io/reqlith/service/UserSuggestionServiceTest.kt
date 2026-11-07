package io.reqlith.service

import io.mockk.every
import io.mockk.mockk
import io.reqlith.model.dto.DirectoryUser
import io.reqlith.model.entity.AppUser
import io.reqlith.model.entity.Invitation
import io.reqlith.model.entity.MemberId
import io.reqlith.model.entity.MemberRole
import io.reqlith.model.entity.ProjectMember
import io.reqlith.repository.AppUserRepository
import io.reqlith.support.UnitFixtures
import io.reqlith.support.UnitFixtures.NOW
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.ObjectProvider
import kotlin.test.assertEquals

class UserSuggestionServiceTest {
    private val users = mockk<AppUserRepository>()
    private val memberships = mockk<MembershipService>()
    private val me = UnitFixtures.user("Olive Owens", email = "owner1@reqlith.local")
    private val project = UnitFixtures.project(me)

    private fun provider(directory: UserDirectory?) = mockk<ObjectProvider<UserDirectory>> { every { ifAvailable } returns directory }
    private fun known(name: String, email: String) = AppUser("sub-$email", email, name, "XX", NOW)

    init {
        every { users.search(any(), any()) } returns listOf(known("Rita Reyes", "reviewer1@reqlith.local"), known("Olive Owens", "owner1@reqlith.local"))
    }

    @Test
    fun `known users and the directory are merged, sorted and deduplicated by e-mail`() {
        val directory = UserDirectory { _, _ -> listOf(DirectoryUser("Raj Rao", "reviewer2@reqlith.local"), DirectoryUser("Rita R.", "REVIEWER1@reqlith.local")) }
        val result = UserSuggestionService(users, provider(directory), memberships).suggest(me, " re ", null)
        assertEquals(listOf("Raj Rao", "Rita Reyes"), result.map { it.name }, "the inviter is left out")
    }

    @Test
    fun `members and pending invitations of the project are left out`() {
        val member = known("Rita Reyes", "reviewer1@reqlith.local")
        every { memberships.members(project) } returns listOf(ProjectMember(MemberId(project.id, member.id), MemberRole.REVIEWER, NOW))
        every { users.findAllById(listOf(member.id)) } returns listOf(member)
        every { memberships.invitations(project) } returns listOf(Invitation(project.id, "reviewer2@reqlith.local", MemberRole.REVIEWER, me.userId, NOW))
        val directory = UserDirectory { _, _ -> listOf(DirectoryUser("Raj Rao", "reviewer2@reqlith.local"), DirectoryUser("Carla Conti", "contrib1@reqlith.local")) }

        assertEquals(listOf("contrib1@reqlith.local"), UserSuggestionService(users, provider(directory), memberships).suggest(me, "re", project).map { it.email })
    }

    @Test
    fun `short input gives no suggestions and a failing directory falls back to known users`() {
        val failing = UserDirectory { _, _ -> error("Keycloak down") }
        val service = UserSuggestionService(users, provider(failing), memberships)
        assertEquals(emptyList(), service.suggest(me, "r", null))
        assertEquals(listOf("Rita Reyes"), service.suggest(me, "re", null).map { it.name })
        assertEquals(listOf("Rita Reyes"), UserSuggestionService(users, provider(null), memberships).suggest(me, "re", null).map { it.name })
    }
}
