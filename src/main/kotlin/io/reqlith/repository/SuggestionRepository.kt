package io.reqlith.repository

import io.reqlith.model.entity.Suggestion
import io.reqlith.model.entity.SuggestionStatus
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface SuggestionRepository : JpaRepository<Suggestion, UUID> {
    fun findByVersionIdOrderByCreatedAt(versionId: UUID): List<Suggestion>

    fun findByVersionIdInAndStatus(versionIds: Collection<UUID>, status: SuggestionStatus): List<Suggestion>
}
