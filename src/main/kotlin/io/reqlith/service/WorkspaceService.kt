package io.reqlith.service

import io.github.oshai.kotlinlogging.KotlinLogging
import io.reqlith.exception.ValidationException
import io.reqlith.model.dto.CurrentUser
import io.reqlith.model.entity.Workspace
import io.reqlith.repository.WorkspaceRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant

private val logger = KotlinLogging.logger {}

@Service
class WorkspaceService(private val workspaces: WorkspaceRepository, private val clock: Clock) {
    fun all(): List<Workspace> = workspaces.findAllByOrderByName()

    @Transactional
    fun create(user: CurrentUser, key: String, name: String): Workspace {
        val normalizedKey = key.trim().uppercase()
        if (!KEY.matches(normalizedKey)) throw ValidationException("key", "Use 2–8 capital letters or digits, e.g. PAY")
        if (name.isBlank()) throw ValidationException("name", "Name is required")
        if (workspaces.existsByKey(normalizedKey)) throw ValidationException("key", "Workspace $normalizedKey already exists")
        return workspaces.save(Workspace(normalizedKey, name.trim(), Instant.now(clock)))
            .also { logger.info { "Workspace created workspace=${it.key}" } }
    }

    companion object {
        private val KEY = Regex("[A-Z0-9]{2,8}")
    }
}
