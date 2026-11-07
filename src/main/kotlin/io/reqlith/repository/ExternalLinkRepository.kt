package io.reqlith.repository

import io.reqlith.model.entity.ExternalLink
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface ExternalLinkRepository : JpaRepository<ExternalLink, UUID> {
    fun findByProjectIdOrderByCreatedAt(projectId: UUID): List<ExternalLink>
}
