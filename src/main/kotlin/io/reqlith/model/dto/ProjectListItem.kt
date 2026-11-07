package io.reqlith.model.dto

import io.reqlith.model.entity.MemberRole
import io.reqlith.model.entity.Project

/** A row of the "All projects" table. */
data class ProjectListItem(val project: Project, val owners: String, val myRole: MemberRole?, val state: String, val stateClass: String)
