package io.reqlith.workspace

import io.reqlith.common.ValidationException
import io.reqlith.user.CurrentUser
import org.springframework.security.access.AccessDeniedException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant

@Service
class WorkspaceService(
    private val workspaces: WorkspaceRepository,
    private val clock: Clock,
) {
    fun all(): List<Workspace> = workspaces.findAllByOrderByName()

    @Transactional
    fun create(user: CurrentUser, key: String, name: String): Workspace {
        if (!user.isAdmin) throw AccessDeniedException("Only administrators manage workspaces")
        val normalizedKey = key.trim().uppercase()
        if (!KEY.matches(normalizedKey)) throw ValidationException("key", "Use 2–8 capital letters or digits, e.g. PAY")
        if (name.isBlank()) throw ValidationException("name", "Name is required")
        if (workspaces.existsByKey(normalizedKey)) throw ValidationException("key", "Workspace $normalizedKey already exists")
        return workspaces.save(Workspace(normalizedKey, name.trim(), Instant.now(clock)))
    }

    @Transactional
    fun rename(user: CurrentUser, workspace: Workspace, name: String) {
        if (!user.isAdmin) throw AccessDeniedException("Only administrators manage workspaces")
        if (name.isBlank()) throw ValidationException("name", "Name is required")
        workspace.name = name.trim()
    }

    companion object {
        private val KEY = Regex("[A-Z0-9]{2,8}")
    }
}
