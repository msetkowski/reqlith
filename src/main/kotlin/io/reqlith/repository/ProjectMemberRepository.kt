package io.reqlith.repository

import io.reqlith.model.entity.MemberId
import io.reqlith.model.entity.ProjectMember
import java.util.UUID
import org.springframework.data.jpa.repository.JpaRepository

interface ProjectMemberRepository : JpaRepository<ProjectMember, MemberId> {
    fun findByIdProjectId(projectId: UUID): List<ProjectMember>

    fun findByIdUserId(userId: UUID): List<ProjectMember>
}
