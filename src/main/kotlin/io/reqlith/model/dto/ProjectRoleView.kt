package io.reqlith.model.dto

import io.reqlith.model.entity.Role

/** A role as used in one project: the global role plus its project-specific context. */
data class ProjectRoleView(val role: Role, val contextMd: String?, val sortOrder: Int)
