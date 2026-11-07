package io.reqlith.service

import io.github.oshai.kotlinlogging.KotlinLogging
import io.reqlith.exception.NotFoundException
import io.reqlith.exception.ValidationException
import io.reqlith.model.dto.CurrentUser
import io.reqlith.model.dto.ProjectRoleView
import io.reqlith.model.entity.MemberRole
import io.reqlith.model.entity.Project
import io.reqlith.model.entity.ProjectRole
import io.reqlith.model.entity.ProjectRoleId
import io.reqlith.model.entity.Role
import io.reqlith.repository.KeyCounter
import io.reqlith.repository.ProjectRoleRepository
import io.reqlith.repository.RoleRepository
import java.time.Clock
import java.time.Instant
import java.util.UUID
import org.springframework.security.access.AccessDeniedException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

private val logger = KotlinLogging.logger {}

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

    /** Editors of a project using the role, and, while no project uses it yet, anyone who may create roles. */
    fun canEditGlobal(user: CurrentUser, role: Role): Boolean {
        val usedBy = projectIdsUsing(role)
        return if (usedBy.isEmpty()) canCreate(user) else usedBy.any { access.roleOf(it, user.userId) in EDITOR_ROLES }
    }

    /** Editors of at least one project can define new roles. */
    fun canCreate(user: CurrentUser): Boolean = access.isEditorAnywhere(user)

    /** A new role that projects can then select, created from the roles overview. */
    @Transactional
    fun create(user: CurrentUser, name: String, descriptionMd: String?): Role {
        if (!canCreate(user)) throw AccessDeniedException("Only editors of a project can create roles")
        return newRole(user, name, descriptionMd).also { logger.info { "Role created role=${it.key}" } }
    }

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
        logger.info { "Role added to project project=${project.key} role=${role.key}" }
    }

    @Transactional
    fun createAndAdd(user: CurrentUser, project: Project, name: String, descriptionMd: String?, contextMd: String?): Role {
        access.requireEditor(project, user)
        project.requireEditable()
        val role = newRole(user, name, descriptionMd)
        projectRoles.save(ProjectRole(ProjectRoleId(project.id, role.id), normalize(contextMd), projectRoles.maxSortOrder(project.id) + 1))
        projectService.touch(project, user)
        logger.info { "Role created and added project=${project.key} role=${role.key}" }
        return role
    }

    @Transactional
    fun updateContext(user: CurrentUser, project: Project, roleId: UUID, contextMd: String?) {
        access.requireEditor(project, user)
        project.requireEditable()
        val link = projectRoles.findById(ProjectRoleId(project.id, roleId)).orElseThrow { NotFoundException("Role is not part of ${project.key}") }
        link.contextMd = normalize(contextMd)
        projectService.touch(project, user)
        logger.info { "Role context updated project=${project.key} roleId=$roleId" }
    }

    @Transactional
    fun removeFromProject(user: CurrentUser, project: Project, roleId: UUID) {
        access.requireEditor(project, user)
        project.requireEditable()
        projectRoles.deleteById(ProjectRoleId(project.id, roleId))
        projectService.touch(project, user)
        logger.info { "Role removed from project project=${project.key} roleId=$roleId" }
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
        roles.findByName(trimmed)?.let { if (it.id != role.id) throw ValidationException("name", "A role called $trimmed already exists") }
        role.name = trimmed
        role.descriptionMd = normalize(descriptionMd)
        role.updatedAt = Instant.now(clock)
        role.updatedBy = user.userId
        roles.save(role)
        logger.info { "Global role changed role=${role.key} usedInProjects=${projectIdsUsing(role).size}" }
    }

    private fun newRole(user: CurrentUser, name: String, descriptionMd: String?): Role {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) throw ValidationException("name", "Role name is required")
        if (trimmed.length > 200) throw ValidationException("name", "Keep the name under 200 characters")
        if (roles.findByName(trimmed) != null) {
            throw ValidationException("name", "A role called $trimmed already exists. Select it from the list instead.")
        }
        return roles.save(
            Role(
                key = "RO-%02d".format(keyCounter.next("ROLE_KEY", "RO")),
                name = trimmed,
                descriptionMd = normalize(descriptionMd),
                updatedAt = Instant.now(clock),
                updatedBy = user.userId,
            ),
        )
    }

    private fun normalize(markdown: String?): String? = markdown?.replace("\r\n", "\n")?.ifBlank { null }

    companion object {
        private val EDITOR_ROLES = setOf(MemberRole.OWNER, MemberRole.CONTRIBUTOR)
    }
}
