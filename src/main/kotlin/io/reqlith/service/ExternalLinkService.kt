package io.reqlith.service

import io.github.oshai.kotlinlogging.KotlinLogging
import io.reqlith.exception.NotFoundException
import io.reqlith.exception.ValidationException
import io.reqlith.model.dto.CurrentUser
import io.reqlith.model.entity.ExternalLink
import io.reqlith.model.entity.LinkSystem
import io.reqlith.model.entity.Project
import io.reqlith.repository.ExternalLinkRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.net.URI
import java.time.Clock
import java.time.Instant
import java.util.UUID

private val logger = KotlinLogging.logger {}

@Service
class ExternalLinkService(private val links: ExternalLinkRepository, private val access: ProjectAccess, private val clock: Clock) {
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
            ExternalLink(
                project.id,
                versionNo,
                elementKey?.trim()?.uppercase()?.ifEmpty {
                    null
                },
                system,
                target,
                text.take(300),
                Instant.now(clock),
                user.userId,
            ),
        ).also { logger.info { "External link added project=${project.key} system=$system scope=${it.scope}" } }
    }

    @Transactional
    fun remove(user: CurrentUser, project: Project, linkId: UUID) {
        access.requireEditor(project, user)
        val link = links.findById(linkId).orElseThrow { NotFoundException("Link not found") }
        require(link.projectId == project.id)
        links.delete(link)
        logger.info { "External link removed project=${project.key} system=${link.system} scope=${link.scope}" }
    }
}
