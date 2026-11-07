package io.reqlith.model.entity

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import jakarta.persistence.EmbeddedId
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import java.io.Serializable
import java.time.Instant
import java.util.UUID

@Embeddable
data class MemberId(
    @Column(name = "project_id") val projectId: UUID,
    @Column(name = "user_id") val userId: UUID,
) : Serializable


@Entity
@Table(name = "project_member")
class ProjectMember(
    @EmbeddedId
    val id: MemberId,
    @Enumerated(EnumType.STRING)
    @Column(name = "member_role", nullable = false)
    var role: MemberRole,
    @Column(name = "added_at", nullable = false, updatable = false)
    val addedAt: Instant,
) {
    val projectId: UUID get() = id.projectId
    val userId: UUID get() = id.userId
}
