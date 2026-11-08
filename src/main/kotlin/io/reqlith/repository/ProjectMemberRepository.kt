package io.reqlith.repository

import io.reqlith.model.entity.MemberId
import io.reqlith.model.entity.ProjectMember
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface ProjectMemberRepository : JpaRepository<ProjectMember, MemberId> {
    fun findByIdProjectId(projectId: UUID): List<ProjectMember>

    fun findByIdUserId(userId: UUID): List<ProjectMember>

    fun findByIdProjectIdIn(projectIds: Collection<UUID>): List<ProjectMember>
}
