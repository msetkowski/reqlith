package io.reqlith.repository

import io.reqlith.model.entity.Workspace
import java.util.UUID
import org.springframework.data.jpa.repository.JpaRepository

interface WorkspaceRepository : JpaRepository<Workspace, UUID> {
    fun findAllByOrderByName(): List<Workspace>

    fun existsByKey(key: String): Boolean
}
