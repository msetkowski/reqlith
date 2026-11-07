package io.reqlith.model.entity

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import jakarta.persistence.EmbeddedId
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.io.Serializable
import java.util.UUID

@Embeddable
data class ProjectRoleId(
    @Column(name = "project_id") val projectId: UUID,
    @Column(name = "role_id") val roleId: UUID,
) : Serializable


/** What a global role does in the context of one project. */
@Entity
@Table(name = "project_role")
class ProjectRole(
    @EmbeddedId
    val id: ProjectRoleId,
    @Column(name = "context_md")
    var contextMd: String?,
    @Column(name = "sort_order", nullable = false)
    var sortOrder: Int,
)
