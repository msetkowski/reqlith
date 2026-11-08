package io.reqlith.repository

import io.reqlith.model.entity.MemberRole
import io.reqlith.model.entity.Project
import io.reqlith.model.entity.ProjectStatus
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import java.time.Instant
import java.util.UUID

interface ProjectRepository : JpaRepository<Project, UUID> {
    fun findByKey(key: String): Project?

    fun findAllByOrderByUpdatedAtDesc(): List<Project>

    /**
     * One page of the project list, most recently modified first. Null filters match everything; [pattern] is a
     * lower-case LIKE pattern matched against title, key and tags; [role] filters on the role of [userId].
     */
    @Query(
        """
        select p from Project p
        where (:workspaceId is null or p.workspace.id = :workspaceId)
          and (:status is null or p.status = :status)
          and (:role is null or exists (
                select m from ProjectMember m where m.id.projectId = p.id and m.id.userId = :userId and m.role = :role))
          and (:pattern is null or lower(p.title) like :pattern escape '!' or lower(p.key) like :pattern escape '!'
                or exists (select t from Project tp join tp.tags t where tp = p and lower(t) like :pattern escape '!'))
        order by p.updatedAt desc, p.id
        """,
    )
    fun search(workspaceId: UUID?, status: ProjectStatus?, userId: UUID, role: MemberRole?, pattern: String?, pageable: Pageable): Slice<Project>

    /**
     * Records "last modified" without bumping the optimistic lock version, so edits of different
     * elements in the same project never conflict with each other.
     */
    @Modifying
    @Query("update Project p set p.updatedAt = :at, p.updatedBy = :by where p.id = :id")
    fun touch(id: UUID, at: Instant, by: UUID): Int
}
