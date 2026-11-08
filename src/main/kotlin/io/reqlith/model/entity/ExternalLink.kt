package io.reqlith.model.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/**
 * A reference to another system (a JIRA epic, an ADO feature, a document). It lives outside version
 * snapshots, so links can be added after approval, for the project, a specific version or one element.
 */
@Entity
@Table(name = "external_link")
class ExternalLink(
    @Column(name = "project_id", nullable = false, updatable = false)
    val projectId: UUID,
    @Column(name = "version_no", updatable = false)
    val versionNo: Int?,
    @Column(name = "element_key", updatable = false)
    val elementKey: String?,
    @Enumerated(EnumType.STRING)
    @Column(name = "link_system", nullable = false, updatable = false)
    val system: LinkSystem,
    @Column(nullable = false, updatable = false)
    val url: String,
    @Column(nullable = false, updatable = false)
    val label: String,
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant,
    @Column(name = "created_by", nullable = false, updatable = false)
    val createdBy: UUID,
) : AbstractEntity() {
    /** "Project", "v2" or "FR-12". */
    val scope: String get() = elementKey ?: versionNo?.let { "v$it" } ?: "Project"
}
