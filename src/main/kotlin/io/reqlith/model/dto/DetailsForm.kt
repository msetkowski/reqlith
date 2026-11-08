package io.reqlith.model.dto

import io.reqlith.model.entity.Project
import io.reqlith.service.ProjectService

class DetailsForm(var title: String = "", var summary: String? = null, var tags: String? = null, var domains: String? = null, var rowVersion: Int = 0) {
    fun details() = ProjectDetails(title, summary, ProjectService.parseList(tags), ProjectService.parseList(domains))

    companion object {
        fun of(project: Project) = DetailsForm(
            title = project.title,
            summary = project.summary,
            tags = project.tags.joinToString(", "),
            domains = project.domains.joinToString(", "),
            rowVersion = project.rowVersion,
        )
    }
}
