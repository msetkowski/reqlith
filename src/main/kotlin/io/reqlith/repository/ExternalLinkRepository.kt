package io.reqlith.repository

import io.reqlith.model.entity.ExternalLink
import java.util.UUID
import org.springframework.data.jpa.repository.JpaRepository

interface ExternalLinkRepository : JpaRepository<ExternalLink, UUID> {
    fun findByProjectIdOrderByCreatedAt(projectId: UUID): List<ExternalLink>
}
