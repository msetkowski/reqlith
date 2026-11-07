package io.reqlith.repository

import io.reqlith.model.entity.AppUser
import java.util.UUID
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface AppUserRepository : JpaRepository<AppUser, UUID> {
    fun findBySubject(subject: String): AppUser?

    /** Users whose name or e-mail contains [text] (case-insensitive). */
    @Query(
        """select u from AppUser u where u.email is not null
           and (lower(u.displayName) like lower(concat('%', :text, '%')) or lower(u.email) like lower(concat('%', :text, '%')))
           order by u.displayName""",
    )
    fun search(text: String, page: Pageable): List<AppUser>
}
