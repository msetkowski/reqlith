package io.reqlith.service

import io.github.oshai.kotlinlogging.KotlinLogging
import io.reqlith.model.dto.CurrentUser
import io.reqlith.model.dto.DirectoryUser
import io.reqlith.model.entity.Project
import io.reqlith.repository.AppUserRepository
import org.springframework.beans.factory.ObjectProvider
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service

private val logger = KotlinLogging.logger {}

/**
 * Suggests people to invite while an e-mail address is typed: users who signed in before, plus the identity
 * provider's directory when one is configured. Existing members, pending invitations and the inviter are left
 * out. If the directory is unavailable, suggestions continue with known users only.
 */
@Service
class UserSuggestionService(
    private val users: AppUserRepository,
    private val directory: ObjectProvider<UserDirectory>,
    private val memberships: MembershipService,
) {
    fun suggest(user: CurrentUser, text: String, project: Project?): List<DirectoryUser> {
        val query = text.trim()
        if (query.length < MIN_LENGTH) return emptyList()
        val excluded = buildSet {
            user.emailAddress?.let { add(it.lowercase()) }
            if (project != null) {
                val memberIds = memberships.members(project).map { it.userId }
                users.findAllById(memberIds).mapNotNullTo(this) { it.email?.lowercase() }
                memberships.invitations(project).mapTo(this) { it.email.lowercase() }
            }
        }
        val known = users.search(query, PageRequest.of(0, LIMIT)).map { DirectoryUser(it.displayName, it.email!!) }
        val fromDirectory = directory.ifAvailable?.let { source ->
            try {
                source.search(query, LIMIT)
            } catch (e: Exception) {
                logger.error(e) { "User directory unavailable, suggesting known users only: ${e.message}" }
                emptyList()
            }
        }.orEmpty()
        return (known + fromDirectory)
            .distinctBy { it.email.lowercase() }
            .filter { it.email.lowercase() !in excluded }
            .sortedBy { it.name.lowercase() }
            .take(LIMIT)
            .also { logger.debug { "Invitation suggestions for '$query': ${it.size} (directory ${fromDirectory.size})" } }
    }

    companion object {
        const val MIN_LENGTH = 2
        const val LIMIT = 10
    }
}
