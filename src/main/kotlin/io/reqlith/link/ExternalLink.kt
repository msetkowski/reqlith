package io.reqlith.link

import io.reqlith.common.AbstractEntity
import io.reqlith.common.NotFoundException
import io.reqlith.common.ValidationException
import io.reqlith.project.Project
import io.reqlith.project.ProjectAccess
import io.reqlith.user.CurrentUser
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.net.URI
import java.time.Clock
import java.time.Instant
import java.util.UUID

enum class LinkSystem(val label: String) {
    JIRA("JIRA"),
    ADO("ADO"),
    URL("URL"),
}

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

interface ExternalLinkRepository : JpaRepository<ExternalLink, UUID> {
    fun findByProjectIdOrderByCreatedAt(projectId: UUID): List<ExternalLink>
}

@Service
class ExternalLinkService(
    private val links: ExternalLinkRepository,
    private val access: ProjectAccess,
    private val clock: Clock,
) {
    fun all(project: Project): List<ExternalLink> = links.findByProjectIdOrderByCreatedAt(project.id)

    fun forElement(project: Project, key: String): List<ExternalLink> = all(project).filter { it.elementKey == key }

    /** Links are not part of the snapshot, so they can be added in any state (also during review). */
    @Transactional
    fun add(user: CurrentUser, project: Project, system: LinkSystem, url: String, label: String?, versionNo: Int?, elementKey: String?): ExternalLink {
        access.requireEditor(project, user)
        val target = url.trim()
        val parsed = runCatching { URI(target) }.getOrNull()
        if (parsed == null || parsed.scheme?.lowercase() !in setOf("http", "https") || parsed.host.isNullOrBlank()) {
            throw ValidationException("url", "Enter a full http(s) link, e.g. https://jira.example.com/browse/PAY-2291")
        }
        if (versionNo != null && (project.latestApprovedVersion == null || versionNo > project.latestApprovedVersion!!)) {
            throw ValidationException("versionNo", "Version v$versionNo is not approved")
        }
        val text = label?.trim()?.takeIf { it.isNotEmpty() } ?: target.substringAfterLast('/').ifEmpty { target }
        return links.save(
            ExternalLink(project.id, versionNo, elementKey?.trim()?.uppercase()?.ifEmpty { null }, system, target, text.take(300), Instant.now(clock), user.userId),
        )
    }

    @Transactional
    fun remove(user: CurrentUser, project: Project, linkId: UUID) {
        access.requireEditor(project, user)
        val link = links.findById(linkId).orElseThrow { NotFoundException("Link not found") }
        require(link.projectId == project.id)
        links.delete(link)
    }
}
