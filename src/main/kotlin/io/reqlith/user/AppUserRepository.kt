package io.reqlith.user

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface AppUserRepository : JpaRepository<AppUser, UUID> {
    fun findBySubject(subject: String): AppUser?

    fun findByEmailIgnoreCase(email: String): AppUser?
}
