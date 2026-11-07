package io.reqlith.repository

import io.reqlith.model.entity.Suggestion
import io.reqlith.model.entity.SuggestionStatus
import java.util.UUID
import org.springframework.data.jpa.repository.JpaRepository

interface SuggestionRepository : JpaRepository<Suggestion, UUID> {
    fun findByVersionIdOrderByCreatedAt(versionId: UUID): List<Suggestion>

    fun findByVersionIdInAndStatus(versionIds: Collection<UUID>, status: SuggestionStatus): List<Suggestion>
}
