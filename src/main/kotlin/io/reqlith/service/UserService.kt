package io.reqlith.service

import io.reqlith.model.entity.AppUser
import io.reqlith.repository.AppUserRepository
import java.time.Clock
import java.time.Instant
import java.util.UUID
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class UserService(
    private val users: AppUserRepository,
    private val clock: Clock,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /** Users by id, for showing names next to ids stored in projects, elements and reviews. */
    fun byIds(ids: Collection<UUID>): Map<UUID, AppUser> =
        if (ids.isEmpty()) emptyMap() else users.findAllById(ids.toSet()).associateBy { it.id }

    /** Creates or refreshes the local user from IDP claims. Called on every login. */
    @Transactional
    fun provision(subject: String, email: String?, name: String?): AppUser {
        val now = Instant.now(clock)
        val displayName = name?.takeIf { it.isNotBlank() } ?: email ?: subject
        val existing = users.findBySubject(subject)
        val user = existing
            ?: AppUser(subject, email, displayName, AppUser.initialsOf(displayName), createdAt = now)
        user.email = email
        user.displayName = displayName
        user.initials = AppUser.initialsOf(displayName)
        user.lastLoginAt = now
        val saved = users.save(user)
        if (existing == null) logger.info("User provisioned user={}", saved.id) else logger.debug("User refreshed from IDP claims user={}", saved.id)
        return saved
    }
}
