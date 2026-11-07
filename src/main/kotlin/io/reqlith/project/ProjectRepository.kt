package io.reqlith.project

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import java.time.Instant
import java.util.UUID

interface ProjectRepository : JpaRepository<Project, UUID> {
    fun findByKey(key: String): Project?

    fun findAllByOrderByUpdatedAtDesc(): List<Project>

    /**
     * Records "last modified" without bumping the optimistic lock version, so edits of different
     * elements in the same project never conflict with each other.
     */
    @Modifying
    @Query("update Project p set p.updatedAt = :at, p.updatedBy = :by where p.id = :id")
    fun touch(id: UUID, at: Instant, by: UUID): Int
}
