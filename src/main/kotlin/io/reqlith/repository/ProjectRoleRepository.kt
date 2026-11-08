package io.reqlith.repository

import io.reqlith.model.entity.ProjectRole
import io.reqlith.model.entity.ProjectRoleId
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.util.UUID

interface ProjectRoleRepository : JpaRepository<ProjectRole, ProjectRoleId> {
    fun findByIdProjectIdOrderBySortOrder(projectId: UUID): List<ProjectRole>

    fun findByIdRoleId(roleId: UUID): List<ProjectRole>

    @Query("select coalesce(max(pr.sortOrder), 0) from ProjectRole pr where pr.id.projectId = :projectId")
    fun maxSortOrder(projectId: UUID): Int
}
