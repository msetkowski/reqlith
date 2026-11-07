package io.reqlith.role

import io.reqlith.common.AbstractEntity
import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import jakarta.persistence.EmbeddedId
import jakarta.persistence.Entity
import jakarta.persistence.Table
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.io.Serializable
import java.time.Instant
import java.util.UUID

/** A group of users (e.g. "Customer"), shared by all projects. */
@Entity
@Table(name = "role")
class Role(
    @Column(name = "role_key", nullable = false, updatable = false)
    val key: String,
    @Column(nullable = false)
    var name: String,
    @Column(name = "description_md")
    var descriptionMd: String?,
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant,
    @Column(name = "updated_by", nullable = false)
    var updatedBy: UUID,
) : AbstractEntity()

@Embeddable
data class ProjectRoleId(
    @Column(name = "project_id") val projectId: UUID,
    @Column(name = "role_id") val roleId: UUID,
) : Serializable

/** What a global role does in the context of one project. */
@Entity
@Table(name = "project_role")
class ProjectRole(
    @EmbeddedId
    val id: ProjectRoleId,
    @Column(name = "context_md")
    var contextMd: String?,
    @Column(name = "sort_order", nullable = false)
    var sortOrder: Int,
)

interface RoleRepository : JpaRepository<Role, UUID> {
    fun findAllByOrderByName(): List<Role>

    fun findByNameIgnoreCase(name: String): Role?

    fun findByKey(key: String): Role?
}

interface ProjectRoleRepository : JpaRepository<ProjectRole, ProjectRoleId> {
    fun findByIdProjectIdOrderBySortOrder(projectId: UUID): List<ProjectRole>

    fun findByIdRoleId(roleId: UUID): List<ProjectRole>

    @Query("select coalesce(max(pr.sortOrder), 0) from ProjectRole pr where pr.id.projectId = :projectId")
    fun maxSortOrder(projectId: UUID): Int
}
