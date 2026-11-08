package io.reqlith.model.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

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
