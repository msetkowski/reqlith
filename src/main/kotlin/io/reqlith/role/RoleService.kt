package io.reqlith.role

import io.reqlith.common.KeyCounter
import io.reqlith.common.NotFoundException
import io.reqlith.common.ValidationException
import io.reqlith.project.MemberRole
import io.reqlith.project.Project
import io.reqlith.project.ProjectAccess
import io.reqlith.project.ProjectService
import io.reqlith.user.CurrentUser
import org.springframework.security.access.AccessDeniedException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant
import java.util.UUID

/** A role as used in one project: the global role plus its project-specific context. */
data class ProjectRoleView(val role: Role, val contextMd: String?, val sortOrder: Int)

/**
 * Roles are global so the same group of users can be recognised across projects.
 * A project picks an existing role (or creates a new one) and describes what it does in that project.
 */
@Service
class RoleService(
    private val roles: RoleRepository,
    private val projectRoles: ProjectRoleRepository,
    private val projectService: ProjectService,
    private val access: ProjectAccess,
    private val keyCounter: KeyCounter,
    private val clock: Clock,
) {
    fun all(): List<Role> = roles.findAllByOrderByName()

    fun get(key: String): Role = roles.findByKey(key) ?: throw NotFoundException("Role $key not found")

    fun forProject(project: Project): List<ProjectRoleView> {
        val links = projectRoles.findByIdProjectIdOrderBySortOrder(project.id)
        val byId = roles.findAllById(links.map { it.id.roleId }).associateBy { it.id }
        return links.mapNotNull { link -> byId[link.id.roleId]?.let { ProjectRoleView(it, link.contextMd, link.sortOrder) } }
    }

    /** Number of projects using each role. */
    fun usage(): Map<UUID, Int> = projectRoles.findAll().groupingBy { it.id.roleId }.eachCount()

    fun canEditGlobal(user: CurrentUser, role: Role): Boolean =
        user.isAdmin || projectIdsUsing(role).any { access.roleOf(it, user.userId) in EDITOR_ROLES }

    fun projectIdsUsing(role: Role): List<UUID> = projectRoles.findByIdRoleId(role.id).map { it.id.projectId }

    @Transactional
    fun addExisting(user: CurrentUser, project: Project, roleId: UUID, contextMd: String?) {
        access.requireEditor(project, user)
        project.requireEditable()
        val role = roles.findById(roleId).orElseThrow { NotFoundException("Role not found") }
        val id = ProjectRoleId(project.id, role.id)
        if (projectRoles.existsById(id)) throw ValidationException("roleId", "${role.name} is already part of this project")
        projectRoles.save(ProjectRole(id, normalize(contextMd), projectRoles.maxSortOrder(project.id) + 1))
        projectService.touch(project, user)
    }

    @Transactional
    fun createAndAdd(user: CurrentUser, project: Project, name: String, descriptionMd: String?, contextMd: String?): Role {
        access.requireEditor(project, user)
        project.requireEditable()
        val trimmed = name.trim()
        if (trimmed.isEmpty()) throw ValidationException("name", "Role name is required")
        if (roles.findByNameIgnoreCase(trimmed) != null) {
            throw ValidationException("name", "A role called $trimmed already exists. Select it from the list instead.")
        }
        val role = roles.save(
            Role(
                key = "RO-%02d".format(keyCounter.next("ROLE_KEY", "RO")),
                name = trimmed,
                descriptionMd = normalize(descriptionMd),
                updatedAt = Instant.now(clock),
                updatedBy = user.userId,
            ),
        )
        projectRoles.save(ProjectRole(ProjectRoleId(project.id, role.id), normalize(contextMd), projectRoles.maxSortOrder(project.id) + 1))
        projectService.touch(project, user)
        return role
    }

    @Transactional
    fun updateContext(user: CurrentUser, project: Project, roleId: UUID, contextMd: String?) {
        access.requireEditor(project, user)
        project.requireEditable()
        val link = projectRoles.findById(ProjectRoleId(project.id, roleId)).orElseThrow { NotFoundException("Role is not part of ${project.key}") }
        link.contextMd = normalize(contextMd)
        projectService.touch(project, user)
    }

    @Transactional
    fun removeFromProject(user: CurrentUser, project: Project, roleId: UUID) {
        access.requireEditor(project, user)
        project.requireEditable()
        projectRoles.deleteById(ProjectRoleId(project.id, roleId))
        projectService.touch(project, user)
    }

    /**
     * Changes the global description shared by every project. Allowed for editors of any project using the role.
     * Approved versions are not affected: they carry a copy of the description in their snapshot.
     */
    @Transactional
    fun updateGlobal(user: CurrentUser, role: Role, name: String, descriptionMd: String?) {
        if (!canEditGlobal(user, role)) {
            throw AccessDeniedException("Only editors of a project using ${role.name} can change it")
        }
        val trimmed = name.trim()
        if (trimmed.isEmpty()) throw ValidationException("name", "Role name is required")
        roles.findByNameIgnoreCase(trimmed)?.let { if (it.id != role.id) throw ValidationException("name", "A role called $trimmed already exists") }
        role.name = trimmed
        role.descriptionMd = normalize(descriptionMd)
        role.updatedAt = Instant.now(clock)
        role.updatedBy = user.userId
        roles.save(role)
    }

    private fun normalize(markdown: String?): String? = markdown?.replace("\r\n", "\n")?.ifBlank { null }

    companion object {
        private val EDITOR_ROLES = setOf(MemberRole.OWNER, MemberRole.CONTRIBUTOR)
    }
}
