package io.reqlith.user

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant

@Service
class UserService(
    private val users: AppUserRepository,
    private val clock: Clock,
) {
    /** Creates or refreshes the local user from IDP claims. Called on every login. */
    @Transactional
    fun provision(subject: String, email: String?, name: String?): AppUser {
        val now = Instant.now(clock)
        val displayName = name?.takeIf { it.isNotBlank() } ?: email ?: subject
        val user = users.findBySubject(subject)
            ?: AppUser(subject, email, displayName, AppUser.initialsOf(displayName), createdAt = now)
        user.email = email
        user.displayName = displayName
        user.initials = AppUser.initialsOf(displayName)
        user.lastLoginAt = now
        return users.save(user)
    }
}
