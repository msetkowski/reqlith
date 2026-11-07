package io.reqlith.model.entity

import jakarta.persistence.CollectionTable
import jakarta.persistence.Column
import jakarta.persistence.ElementCollection
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.Table
import jakarta.persistence.Version
import java.time.Instant
import java.util.UUID

/** A requirement, scenario, assumption, risk or dependency in a project's working copy. */
@Entity
@Table(name = "element")
class Element(
    @Column(name = "project_id", nullable = false, updatable = false)
    val projectId: UUID,
    @Enumerated(EnumType.STRING)
    @Column(name = "element_type", nullable = false, updatable = false)
    val type: ElementType,
    @Column(name = "element_key", nullable = false, updatable = false)
    val key: String,
    @Column(nullable = false)
    var title: String,
    @Column(name = "body_md")
    var bodyMd: String?,
    @Column(name = "sort_order", nullable = false)
    var sortOrder: Int,
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant,
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant,
    @Column(name = "updated_by", nullable = false)
    var updatedBy: UUID,
) : AbstractEntity() {

    @Enumerated(EnumType.STRING)
    var priority: Priority? = null

    @Version
    @Column(name = "row_version", nullable = false)
    var rowVersion: Int = 0

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "element_tag", joinColumns = [JoinColumn(name = "element_id")])
    @Column(name = "tag")
    var tags: MutableSet<String> = sortedSetOf()

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "trace_link", joinColumns = [JoinColumn(name = "from_element_id")])
    var links: MutableSet<TraceLink> = mutableSetOf()
}
