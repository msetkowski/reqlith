package io.reqlith.model.entity

import jakarta.persistence.Id
import jakarta.persistence.MappedSuperclass
import java.util.UUID

/** Entities get their UUID when constructed (time-ordered for SQL Server, see [SequentialUuid]). */
@MappedSuperclass
abstract class AbstractEntity(
    @field:Id
    val id: UUID = SequentialUuid.next(),
) {
    override fun equals(other: Any?): Boolean =
        this === other || (other is AbstractEntity && other.javaClass == javaClass && other.id == id)

    override fun hashCode(): Int = id.hashCode()
}
