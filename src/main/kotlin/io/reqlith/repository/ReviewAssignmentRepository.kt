package io.reqlith.repository

import io.reqlith.model.entity.AssignmentId
import io.reqlith.model.entity.Decision
import io.reqlith.model.entity.ReviewAssignment
import java.util.UUID
import org.springframework.data.jpa.repository.JpaRepository

interface ReviewAssignmentRepository : JpaRepository<ReviewAssignment, AssignmentId> {
    fun findByIdVersionId(versionId: UUID): List<ReviewAssignment>

    fun findByIdReviewerIdAndDecision(reviewerId: UUID, decision: Decision): List<ReviewAssignment>
}
