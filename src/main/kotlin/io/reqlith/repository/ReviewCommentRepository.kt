package io.reqlith.repository

import io.reqlith.model.entity.ReviewComment
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface ReviewCommentRepository : JpaRepository<ReviewComment, UUID> {
    fun findByVersionIdOrderByCreatedAt(versionId: UUID): List<ReviewComment>
}
