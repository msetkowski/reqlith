package io.reqlith.model.entity

import java.security.SecureRandom
import java.time.Clock
import java.util.UUID

/**
 * UUIDs that sort by creation time in SQL Server, so primary key (clustered index) inserts append instead of
 * landing on random pages and splitting them.
 *
 * SQL Server orders `uniqueidentifier` by its last six bytes first. Those carry the creation time in
 * milliseconds; the other ten bytes are random (with the RFC 4122 variant bits), so ids stay unique and
 * unguessable.
 */
object SequentialUuid {
    private val random = SecureRandom()

    fun next(clock: Clock = Clock.systemUTC()): UUID {
        val bytes = ByteArray(16).also(random::nextBytes)
        val millis = clock.millis()
        for (i in 0 until 6) bytes[10 + i] = (millis ushr (8 * (5 - i))).toByte()
        bytes[6] = ((bytes[6].toInt() and 0x0f) or 0x40).toByte() // version 4 (random-based)
        bytes[8] = ((bytes[8].toInt() and 0x3f) or 0x80).toByte() // RFC 4122 variant
        var msb = 0L
        var lsb = 0L
        for (i in 0 until 8) msb = (msb shl 8) or (bytes[i].toLong() and 0xff)
        for (i in 8 until 16) lsb = (lsb shl 8) or (bytes[i].toLong() and 0xff)
        return UUID(msb, lsb)
    }
}
