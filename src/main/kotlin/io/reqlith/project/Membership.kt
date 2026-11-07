package io.reqlith.project

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import jakarta.persistence.EmbeddedId
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import io.reqlith.common.AbstractEntity
import org.springframework.data.jpa.repository.JpaRepository
import java.io.Serializable
import java.time.Instant
import java.util.UUID

enum class MemberRole(val label: String) {
    OWNER("Owner"),
    CONTRIBUTOR("Contributor"),
    REVIEWER("Reviewer"),
}

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

interface ProjectMemberRepository : JpaRepository<ProjectMember, MemberId> {
    fun findByIdProjectId(projectId: UUID): List<ProjectMember>

    fun findByIdUserId(userId: UUID): List<ProjectMember>
}

/** Pending invitation by e-mail; the invitee accepts or declines it on the home page. */
@Entity
@Table(name = "invitation")
class Invitation(
    @Column(name = "project_id", nullable = false, updatable = false)
    val projectId: UUID,
    @Column(nullable = false, updatable = false)
    val email: String,
    @Enumerated(EnumType.STRING)
    @Column(name = "member_role", nullable = false)
    var role: MemberRole,
    @Column(name = "invited_by", nullable = false)
    var invitedBy: UUID,
    @Column(name = "invited_at", nullable = false)
    var invitedAt: Instant,
) : AbstractEntity()

interface InvitationRepository : JpaRepository<Invitation, UUID> {
    fun findByProjectId(projectId: UUID): List<Invitation>

    fun findByEmailIgnoreCase(email: String): List<Invitation>

    fun findByProjectIdAndEmailIgnoreCase(projectId: UUID, email: String): Invitation?
}
