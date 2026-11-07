package io.reqlith.repository

import io.reqlith.model.entity.ReviewComment
import java.util.UUID
import org.springframework.data.jpa.repository.JpaRepository

interface ReviewCommentRepository : JpaRepository<ReviewComment, UUID> {
    fun findByVersionIdOrderByCreatedAt(versionId: UUID): List<ReviewComment>
}
