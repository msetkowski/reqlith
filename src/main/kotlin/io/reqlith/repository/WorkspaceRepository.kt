package io.reqlith.repository

import io.reqlith.model.entity.Workspace
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface WorkspaceRepository : JpaRepository<Workspace, UUID> {
    fun findAllByOrderByName(): List<Workspace>

    fun existsByKey(key: String): Boolean
}
