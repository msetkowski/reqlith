package io.reqlith.repository

import io.reqlith.model.entity.BaselineRequirement
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface BaselineRequirementRepository : JpaRepository<BaselineRequirement, UUID> {
    fun findByKey(key: String): BaselineRequirement?
}
