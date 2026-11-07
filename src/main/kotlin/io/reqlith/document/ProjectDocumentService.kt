package io.reqlith.document

import io.reqlith.element.ElementService
import io.reqlith.project.Project
import io.reqlith.role.RoleService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ProjectDocumentService(
    private val elementService: ElementService,
    private val roleService: RoleService,
) {
    /** The working copy as a document, with stable ordering so equal content gives equal documents. */
    @Transactional(readOnly = true)
    fun fromWorkingCopy(project: Project): ProjectDocument =
        ProjectDocument(
            project = DocProject(
                key = project.key,
                title = project.title,
                summary = project.summary,
                workspace = project.workspace.name,
                tags = project.tags.sorted(),
                domains = project.domains.sorted(),
            ),
            basicInfo = project.basicInfoMd.orEmpty(),
            elements = elementService.all(project).map { e ->
                DocElement(
                    key = e.key,
                    type = e.type,
                    title = e.title,
                    body = e.bodyMd,
                    priority = e.priority,
                    tags = e.tags.sorted(),
                    links = e.links.sortedWith(compareBy({ it.kind.ordinal }, { it.toKey })).map { DocLink(it.kind, it.toKey) },
                    order = e.sortOrder,
                )
            },
            roles = roleService.forProject(project).map { pr ->
                DocRole(pr.role.id, pr.role.key, pr.role.name, pr.role.descriptionMd, pr.contextMd)
            },
        )
}
