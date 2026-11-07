package io.reqlith.repository

import io.reqlith.model.entity.Role
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface RoleRepository : JpaRepository<Role, UUID> {
    fun findAllByOrderByName(): List<Role>

    /** Case-insensitive through the database collation (SQL Server default), so the unique index on name is used. */
    fun findByName(name: String): Role?

    fun findByKey(key: String): Role?
}
