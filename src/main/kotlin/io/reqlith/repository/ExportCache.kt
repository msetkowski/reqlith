package io.reqlith.repository

import java.time.Clock
import java.time.Instant
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Component

/** Exports of frozen versions never change, so they are generated once per content hash, format and options. */
@Component
class ExportCache(private val jdbc: JdbcClient, private val clock: Clock) {

    fun get(hash: String, format: String, options: String): ByteArray? =
        jdbc.sql("select bytes from export_cache where content_hash = ? and format = ? and options_key = ?")
            .params(hash, format, options).query(ByteArray::class.java).optional().orElse(null)

    fun put(hash: String, format: String, options: String, bytes: ByteArray) {
        jdbc.sql(
            """
            merge export_cache with (holdlock) as t
            using (select ? as content_hash, ? as format, ? as options_key) as s
               on t.content_hash = s.content_hash and t.format = s.format and t.options_key = s.options_key
            when not matched then insert (content_hash, format, options_key, bytes, created_at)
                 values (s.content_hash, s.format, s.options_key, ?, ?);
            """,
        ).params(hash, format, options, bytes, Instant.now(clock).atOffset(java.time.ZoneOffset.UTC)).update()
    }
}
