package io.reqlith.service

import io.github.oshai.kotlinlogging.KotlinLogging
import io.reqlith.exception.ConcurrentEditException
import io.reqlith.exception.NotFoundException
import io.reqlith.exception.ValidationException
import io.reqlith.model.dto.CurrentUser
import io.reqlith.model.dto.ElementContent
import io.reqlith.model.entity.Element
import io.reqlith.model.entity.ElementType
import io.reqlith.model.entity.Project
import io.reqlith.repository.ElementRepository
import io.reqlith.repository.KeyCounter
import java.time.Clock
import java.time.Instant
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

private val logger = KotlinLogging.logger {}

@Service
class ElementService(
    private val elements: ElementRepository,
    private val projectService: ProjectService,
    private val access: ProjectAccess,
    private val keyCounter: KeyCounter,
    private val clock: Clock,
) {
    /** Elements in document order: by type, then by the order they were created. */
    fun all(project: Project): List<Element> =
        elements.findByProjectId(project.id).sortedWith(compareBy({ it.type.ordinal }, { it.sortOrder }))

    fun get(project: Project, key: String): Element =
        elements.findByProjectIdAndKey(project.id, key) ?: throw NotFoundException("$key not found in ${project.key}")

    @Transactional
    fun create(user: CurrentUser, project: Project, type: ElementType, content: ElementContent): Element {
        access.requireEditor(project, user)
        project.requireEditable()
        val number = keyCounter.next("ELEMENT_KEY:${project.id}", type.name)
        val key = "${type.name}-%02d".format(number)
        validate(key, content)
        val now = Instant.now(clock)
        val element = Element(project.id, type, key, content.title.trim(), normalize(content.bodyMd), number, now, now, user.userId)
        apply(element, content)
        projectService.touch(project, user)
        return elements.save(element).also { logger.info { "Element created project=${project.key} element=$key" } }
    }

    @Transactional
    fun update(user: CurrentUser, project: Project, key: String, content: ElementContent, rowVersion: Int): Element {
        access.requireEditor(project, user)
        project.requireEditable()
        val element = get(project, key)
        if (element.rowVersion != rowVersion) {
            logger.debug { "Stale form rejected project=${project.key} element=$key formVersion=$rowVersion currentVersion=${element.rowVersion}" }
            throw ConcurrentEditException(key)
        }
        validate(key, content)
        element.title = content.title.trim()
        element.bodyMd = normalize(content.bodyMd)
        apply(element, content)
        element.updatedAt = Instant.now(clock)
        element.updatedBy = user.userId
        projectService.touch(project, user)
        return elements.save(element).also { logger.info { "Element updated project=${project.key} element=$key links=${content.links.size}" } }
    }

    /** Removes the element. Its key stays retired; links pointing at it show up as broken references. */
    @Transactional
    fun delete(user: CurrentUser, project: Project, key: String) {
        access.requireEditor(project, user)
        project.requireEditable()
        elements.delete(get(project, key))
        projectService.touch(project, user)
        logger.info { "Element deleted project=${project.key} element=$key (key retired)" }
    }

    private fun apply(element: Element, content: ElementContent) {
        element.priority = content.priority
        element.tags.apply { clear(); addAll(content.tags) }
        element.links.apply { clear(); addAll(content.links) }
    }

    private fun validate(key: String, content: ElementContent) {
        if (content.title.isBlank()) throw ValidationException("title", "Title is required")
        if (content.title.length > 300) throw ValidationException("title", "Keep the title under 300 characters")
        content.links.forEach {
            if (!MarkdownRenderer.KEY.matches(it.toKey)) throw ValidationException("links", "${it.toKey} is not an element or role key")
            if (it.toKey == key) throw ValidationException("links", "An element cannot link to itself")
        }
    }

    private fun normalize(markdown: String?): String? = markdown?.replace("\r\n", "\n")?.ifBlank { null }
}
