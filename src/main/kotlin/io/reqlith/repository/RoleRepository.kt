package io.reqlith.repository

import io.reqlith.model.entity.Role
import java.util.UUID
import org.springframework.data.jpa.repository.JpaRepository

interface RoleRepository : JpaRepository<Role, UUID> {
    fun findAllByOrderByName(): List<Role>

    fun findByNameIgnoreCase(name: String): Role?

    fun findByKey(key: String): Role?
}
