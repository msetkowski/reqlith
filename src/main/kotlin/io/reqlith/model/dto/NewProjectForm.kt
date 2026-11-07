package io.reqlith.model.dto

import io.reqlith.model.entity.MemberRole
import io.reqlith.service.ProjectService
import java.util.UUID

class NewProjectForm(
    var workspaceId: UUID? = null,
    var title: String = "",
    var summary: String? = null,
    var tags: String? = null,
    var domains: String? = null,
    var inviteEmails: MutableList<String> = mutableListOf(),
    var inviteRoles: MutableList<MemberRole> = mutableListOf(),
) {
    fun details() = ProjectDetails(title, summary, ProjectService.parseList(tags), ProjectService.parseList(domains))

    fun invitations(): List<Pair<String, MemberRole>> =
        inviteEmails.zip(inviteRoles).filter { it.first.isNotBlank() }
}
