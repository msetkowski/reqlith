package io.reqlith.repository

import io.reqlith.model.entity.Invitation
import java.util.UUID
import org.springframework.data.jpa.repository.JpaRepository

interface InvitationRepository : JpaRepository<Invitation, UUID> {
    fun findByProjectId(projectId: UUID): List<Invitation>

    fun findByEmailIgnoreCase(email: String): List<Invitation>

    fun findByProjectIdAndEmailIgnoreCase(projectId: UUID, email: String): Invitation?
}
