package io.reqlith.model.dto

import io.reqlith.model.entity.MemberRole
import java.util.UUID

/** Filters of the "All projects" table, bound from the query string. */
data class ProjectFilter(val ws: UUID? = null, val state: String? = null, val role: MemberRole? = null, val q: String? = null)
