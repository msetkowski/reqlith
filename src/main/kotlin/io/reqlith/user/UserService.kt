package io.reqlith.user

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant
import java.util.UUID

@Service
class UserService(
    private val users: AppUserRepository,
    private val clock: Clock,
) {
    /** Users by id, for showing names next to ids stored in projects, elements and reviews. */
    fun byIds(ids: Collection<UUID>): Map<UUID, AppUser> =
        if (ids.isEmpty()) emptyMap() else users.findAllById(ids.toSet()).associateBy { it.id }

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
