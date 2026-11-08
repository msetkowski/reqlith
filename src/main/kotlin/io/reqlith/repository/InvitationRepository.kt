package io.reqlith.repository

import io.reqlith.model.entity.Invitation
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface InvitationRepository : JpaRepository<Invitation, UUID> {
    fun findByProjectId(projectId: UUID): List<Invitation>

    /** E-mail addresses are stored lowercase; pass a lowercase address so the index is used. */
    fun findByEmail(email: String): List<Invitation>

    fun findByProjectIdAndEmail(projectId: UUID, email: String): Invitation?
}
