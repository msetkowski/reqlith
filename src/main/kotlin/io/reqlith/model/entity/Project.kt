package io.reqlith.model.entity

import io.reqlith.exception.ProjectLockedException
import jakarta.persistence.CollectionTable
import jakarta.persistence.Column
import jakarta.persistence.ElementCollection
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.Version
import java.time.Instant
import java.util.UUID

/** The mutable working copy of a project. Approved history lives in project versions, never here. */
@Entity
@Table(name = "project")
class Project(
    @Column(name = "project_key", nullable = false, updatable = false)
    val key: String,
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "workspace_id", nullable = false, updatable = false)
    val workspace: Workspace,
    @Column(nullable = false)
    var title: String,
    var summary: String?,
    @Column(name = "basic_info_md")
    var basicInfoMd: String?,
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant,
    @Column(name = "created_by", nullable = false, updatable = false)
    val createdBy: UUID,
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant,
    @Column(name = "updated_by", nullable = false)
    var updatedBy: UUID,
) : AbstractEntity() {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: ProjectStatus = ProjectStatus.DRAFT

    @Column(name = "latest_approved_version")
    var latestApprovedVersion: Int? = null

    @Column(name = "current_candidate_id")
    var currentCandidateId: UUID? = null

    @Column(name = "solution_design_md")
    var solutionDesignMd: String? = null

    @Version
    @Column(name = "row_version", nullable = false)
    var rowVersion: Int = 0

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "project_tag", joinColumns = [JoinColumn(name = "project_id")])
    @Column(name = "tag")
    var tags: MutableSet<String> = sortedSetOf()

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "project_domain", joinColumns = [JoinColumn(name = "project_id")])
    @Column(name = "domain")
    var domains: MutableSet<String> = sortedSetOf()

    /** Guards every change to the working copy: it is read-only while a review is open. */
    fun requireEditable() {
        if (status == ProjectStatus.IN_REVIEW) throw ProjectLockedException(key)
    }

    fun touch(at: Instant, by: UUID) {
        updatedAt = at
        updatedBy = by
    }
}
