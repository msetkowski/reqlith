package io.reqlith.repository

import io.reqlith.model.entity.AssignmentId
import io.reqlith.model.entity.Decision
import io.reqlith.model.entity.ReviewAssignment
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface ReviewAssignmentRepository : JpaRepository<ReviewAssignment, AssignmentId> {
    fun findByIdVersionId(versionId: UUID): List<ReviewAssignment>

    fun findByIdReviewerIdAndDecision(reviewerId: UUID, decision: Decision): List<ReviewAssignment>
}
