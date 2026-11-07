package io.reqlith.project

import io.reqlith.common.ConcurrentEditException
import io.reqlith.common.KeyCounter
import io.reqlith.common.NotFoundException
import io.reqlith.common.ValidationException
import io.reqlith.user.CurrentUser
import io.reqlith.workspace.WorkspaceRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant
import java.util.UUID

data class ProjectDetails(
    val title: String,
    val summary: String?,
    val tags: Set<String>,
    val domains: Set<String>,
)

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
    fun create(
        user: CurrentUser,
        workspaceId: UUID,
        details: ProjectDetails,
        invitations: List<Pair<String, MemberRole>> = emptyList(),
    ): Project {
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
        project.tags.apply { clear(); addAll(details.tags) }
        project.domains.apply { clear(); addAll(details.domains) }
        project.touch(Instant.now(clock), user.userId)
        projects.save(project)
    }

    @Transactional
    fun updateBasicInfo(user: CurrentUser, project: Project, markdown: String, rowVersion: Int) {
        access.requireEditor(project, user)
        project.requireEditable()
        checkVersion(project, rowVersion)
        project.basicInfoMd = markdown.replace("\r\n", "\n")
        project.touch(Instant.now(clock), user.userId)
        projects.save(project)
    }

    /** Records that something inside the project changed (elements, roles). */
    @Transactional
    fun touch(project: Project, user: CurrentUser) {
        projects.touch(project.id, Instant.now(clock), user.userId)
    }

    private fun checkVersion(project: Project, rowVersion: Int) {
        if (project.rowVersion != rowVersion) throw ConcurrentEditException(project.key)
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
            |""".trimMargin()

        /** Parses "a, b  c" style input into a clean set of tags. */
        fun parseList(raw: String?): Set<String> =
            raw.orEmpty().split(',', ';', '\n').map { it.trim().removePrefix("#").trim() }
                .filter { it.isNotEmpty() }.map { it.take(64) }.toSortedSet()
    }
}
