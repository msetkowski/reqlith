package io.reqlith.service

import io.reqlith.repository.AppUserRepository
import io.reqlith.support.IntegrationTest
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

@IntegrationTest
class UserServiceIT(
    @Autowired private val userService: UserService,
    @Autowired private val users: AppUserRepository,
) {
    @Test
    fun `provisioning creates the user once and refreshes claims on later logins`() {
        val subject = "sub-" + UUID.randomUUID()
        val first = userService.provision(subject, "rita@reqlith.local", "Rita Reyes")
        val second = userService.provision(subject, "rita.reyes@reqlith.local", "Rita M. Reyes")

        assertEquals(first.id, second.id)
        val stored = assertNotNull(users.findBySubject(subject))
        assertEquals("rita.reyes@reqlith.local", stored.email)
        assertEquals("Rita M. Reyes", stored.displayName)
        assertEquals("RR", stored.initials)
        assertNotNull(stored.lastLoginAt)
    }
}
