package io.reqlith.repository

import io.reqlith.model.entity.AppUser
import java.util.UUID
import org.springframework.data.jpa.repository.JpaRepository

interface AppUserRepository : JpaRepository<AppUser, UUID> {
    fun findBySubject(subject: String): AppUser?

    fun findByEmailIgnoreCase(email: String): AppUser?
}
