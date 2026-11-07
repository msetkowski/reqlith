package io.reqlith.repository

import io.reqlith.model.entity.Element
import java.util.UUID
import org.springframework.data.jpa.repository.JpaRepository

interface ElementRepository : JpaRepository<Element, UUID> {
    fun findByProjectId(projectId: UUID): List<Element>

    fun findByProjectIdAndKey(projectId: UUID, key: String): Element?
}
