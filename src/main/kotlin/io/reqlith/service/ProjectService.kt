package io.reqlith.service

import io.github.oshai.kotlinlogging.KotlinLogging
import io.reqlith.exception.ConcurrentEditException
import io.reqlith.exception.NotFoundException
import io.reqlith.exception.ValidationException
import io.reqlith.model.dto.CurrentUser
import io.reqlith.model.dto.ProjectDetails
import io.reqlith.model.entity.MemberId
import io.reqlith.model.entity.MemberRole
import io.reqlith.model.entity.Project
import io.reqlith.model.entity.ProjectMember
import io.reqlith.repository.KeyCounter
import io.reqlith.repository.ProjectMemberRepository
import io.reqlith.repository.ProjectRepository
import io.reqlith.repository.WorkspaceRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant
import java.util.UUID

private val logger = KotlinLogging.logger {}

@Service
class ProjectService(
    private val projects: ProjectRepository,
    private val workspaces: WorkspaceRepository,
    private val members: ProjectMemberRepository,
    private val memberships: MembershipService,
    private val access: ProjectAccess,
    private val keyCounter: KeyCounter,
    private val clock: Clock,
) {
    fun get(key: String): Project = projects.findByKey(key) ?: throw NotFoundException("Project $key not found")

    fun all(): List<Project> = projects.findAllByOrderByUpdatedAtDesc()

    /** Creates a draft project; the creator becomes its first owner and everyone else is invited. */
    @Transactional
    fun create(user: CurrentUser, workspaceId: UUID, details: ProjectDetails, invitations: List<Pair<String, MemberRole>> = emptyList()): Project {
        validate(details)
        val workspace = workspaces.findById(workspaceId).orElseThrow { ValidationException("workspaceId", "Choose a workspace") }
        val number = keyCounter.next("PROJECT_KEY:${workspace.id}", "PRJ")
        val now = Instant.now(clock)
        val project = Project(
            key = "PRJ-${workspace.key}-%03d".format(number),
            workspace = workspace,
            title = details.title.trim(),
            summary = details.summary?.trim()?.ifEmpty { null },
            basicInfoMd = BASIC_INFO_TEMPLATE,
            createdAt = now,
            createdBy = user.userId,
            updatedAt = now,
            updatedBy = user.userId,
        )
        project.tags = details.tags.toSortedSet()
        project.domains = details.domains.toSortedSet()
        val saved = projects.save(project)
        members.save(ProjectMember(MemberId(saved.id, user.userId), MemberRole.OWNER, now))
        invitations.forEach { (email, role) -> memberships.invite(user, saved, email, role) }
        logger.info { "Project created project=${saved.key} workspace=${workspace.key} invitations=${invitations.size}" }
        return saved
    }

    @Transactional
    fun updateDetails(user: CurrentUser, project: Project, details: ProjectDetails, rowVersion: Int) {
        access.requireEditor(project, user)
        project.requireEditable()
        checkVersion(project, rowVersion)
        validate(details)
        project.title = details.title.trim()
        project.summary = details.summary?.trim()?.ifEmpty { null }
        project.tags.apply {
            clear()
            addAll(details.tags)
        }
        project.domains.apply {
            clear()
            addAll(details.domains)
        }
        project.touch(Instant.now(clock), user.userId)
        projects.save(project)
        logger.info { "Project details updated project=${project.key}" }
    }

    @Transactional
    fun updateBasicInfo(user: CurrentUser, project: Project, markdown: String, rowVersion: Int) {
        access.requireEditor(project, user)
        project.requireEditable()
        checkVersion(project, rowVersion)
        project.basicInfoMd = markdown.replace("\r\n", "\n")
        project.touch(Instant.now(clock), user.userId)
        projects.save(project)
        logger.info { "Basic information updated project=${project.key}" }
    }

    /** Records that something inside the project changed (elements, roles). */
    @Transactional
    fun touch(project: Project, user: CurrentUser) {
        projects.touch(project.id, Instant.now(clock), user.userId)
    }

    private fun checkVersion(project: Project, rowVersion: Int) {
        if (project.rowVersion != rowVersion) {
            logger.debug { "Stale form rejected project=${project.key} formVersion=$rowVersion currentVersion=${project.rowVersion}" }
            throw ConcurrentEditException(project.key)
        }
    }

    private fun validate(details: ProjectDetails) {
        if (details.title.isBlank()) throw ValidationException("title", "Project name is required")
        if (details.title.length > 200) throw ValidationException("title", "Keep the name under 200 characters")
        if ((details.summary?.length ?: 0) > 1000) throw ValidationException("summary", "Keep the summary under 1000 characters")
    }

    companion object {
        /** BASIC-INFORMATION starts with fixed headings for problem statement, scope and success metrics. */
        val BASIC_INFO_TEMPLATE = """
            |## Problem statement
            |
            |
            |## Scope
            |
            |### In scope
            |
            |-
            |
            |### Out of scope
            |
            |-
            |
            |## Success metrics
            |
            || Metric | Baseline | Target |
            ||---|---|---|
            || | | |
            |
        """.trimMargin()

        /** Parses "a, b  c" style input into a clean set of tags. */
        fun parseList(raw: String?): Set<String> = raw.orEmpty().split(',', ';', '\n').map { it.trim().removePrefix("#").trim() }
            .filter { it.isNotEmpty() }.map { it.take(64) }.toSortedSet()
    }
}
