package io.reqlith.repository

import io.reqlith.model.entity.Element
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface ElementRepository : JpaRepository<Element, UUID> {
    fun findByProjectId(projectId: UUID): List<Element>

    fun findByProjectIdAndKey(projectId: UUID, key: String): Element?
}
