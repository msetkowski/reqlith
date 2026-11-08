package io.reqlith.model.entity

import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SequentialUuidTest {
    /** The byte order SQL Server sorts uniqueidentifier by: bytes 10-15, then 8-9, 6-7, 4-5, 0-3. */
    private fun sqlServerKey(uuid: UUID): String {
        val hex = uuid.toString().replace("-", "")
        val bytes = hex.chunked(2)
        return (bytes.subList(10, 16) + bytes.subList(8, 10) + bytes.subList(6, 8) + bytes.subList(4, 6) + bytes.subList(0, 4)).joinToString("")
    }

    @Test
    fun `later ids sort after earlier ones in SQL Server`() {
        val ids = (0L until 50L).map { SequentialUuid.next(Clock.fixed(Instant.ofEpochMilli(1_800_000_000_000 + it), ZoneOffset.UTC)) }
        assertEquals(ids, ids.sortedBy(::sqlServerKey))
    }

    @Test
    fun `ids are unique and valid version 4 UUIDs`() {
        val ids = List(10_000) { SequentialUuid.next() }
        assertEquals(ids.size, ids.toSet().size)
        assertTrue(ids.all { it.version() == 4 && it.variant() == 2 })
    }
}
