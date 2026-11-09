package io.reqlith.service

import io.github.oshai.kotlinlogging.KotlinLogging
import io.reqlith.exception.NotFoundException
import io.reqlith.exception.ValidationException
import io.reqlith.model.dto.CurrentUser
import io.reqlith.model.entity.BaselineCategory
import io.reqlith.model.entity.BaselineRequirement
import io.reqlith.repository.BaselineRequirementRepository
import io.reqlith.repository.KeyCounter
import org.springframework.security.access.AccessDeniedException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest
import java.time.Clock
import java.time.Instant

private val logger = KotlinLogging.logger {}

/**
 * The non-functional requirements baseline: global requirements every project inherits as read-only items.
 * Only architects maintain it. It is deliberately not part of project documents, so changes never alter a
 * project's state, content hash or approved versions.
 */
@Service
class BaselineService(private val baseline: BaselineRequirementRepository, private val keyCounter: KeyCounter, private val clock: Clock) {
    /** By category, then in the order they were added. */
    fun all(): List<BaselineRequirement> = baseline.findAll().sortedWith(compareBy({ it.category.ordinal }, { it.createdAt }))

    fun get(key: String): BaselineRequirement = baseline.findByKey(key) ?: throw NotFoundException("Baseline requirement $key not found")

    fun keys(): Set<String> = baseline.findAll().map { it.key }.toSet()

    fun canEdit(user: CurrentUser): Boolean = user.isArchitect

    /** Changes whenever any baseline requirement is added, changed or removed (part of export cache keys). */
    fun fingerprint(): String {
        val text = all().joinToString("\u0000") { "${it.key}|${it.category}|${it.title}|${it.bodyMd.orEmpty()}" }
        return MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }.take(16)
    }

    @Transactional
    fun create(user: CurrentUser, category: BaselineCategory, title: String, bodyMd: String?): BaselineRequirement {
        requireArchitect(user)
        val now = Instant.now(clock)
        return baseline.save(
            BaselineRequirement(
                key = "BL-%02d".format(keyCounter.next("BASELINE_KEY", "BL")),
                category = category,
                title = validTitle(title),
                bodyMd = normalize(bodyMd),
                createdAt = now,
                updatedAt = now,
                updatedBy = user.userId,
            ),
        ).also { logger.info { "Baseline requirement created key=${it.key} category=${it.category}" } }
    }

    @Transactional
    fun update(user: CurrentUser, requirement: BaselineRequirement, category: BaselineCategory, title: String, bodyMd: String?) {
        requireArchitect(user)
        requirement.category = category
        requirement.title = validTitle(title)
        requirement.bodyMd = normalize(bodyMd)
        requirement.updatedAt = Instant.now(clock)
        requirement.updatedBy = user.userId
        baseline.save(requirement)
        logger.info { "Baseline requirement changed key=${requirement.key}" }
    }

    /** Its key stays retired, like element keys. */
    @Transactional
    fun delete(user: CurrentUser, requirement: BaselineRequirement) {
        requireArchitect(user)
        baseline.delete(requirement)
        logger.info { "Baseline requirement removed key=${requirement.key}" }
    }

    private fun requireArchitect(user: CurrentUser) {
        if (!canEdit(user)) throw AccessDeniedException("Only architects can change the baseline")
    }

    private fun validTitle(title: String): String {
        val trimmed = title.trim()
        if (trimmed.isEmpty()) throw ValidationException("title", "Title is required")
        if (trimmed.length > 200) throw ValidationException("title", "Keep the title under 200 characters")
        return trimmed
    }

    private fun normalize(markdown: String?): String? = markdown?.replace("\r\n", "\n")?.ifBlank { null }
}
