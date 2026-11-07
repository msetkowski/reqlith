package io.reqlith.repository

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

private val logger = KotlinLogging.logger {}

/**
 * Hands out monotonically increasing numbers per (scope, name). Numbers are never reused, so a
 * deleted BR-03 stays retired and old diffs and external references stay unambiguous.
 */
@Component
class KeyCounter(private val jdbc: JdbcClient) {

    @Transactional(propagation = Propagation.MANDATORY)
    fun next(scope: String, name: String): Int = jdbc.sql(
        """
            merge key_counter with (holdlock) as t
            using (select :scope as scope, :name as name) as s
               on t.scope = s.scope and t.name = s.name
            when matched then update set last_value = t.last_value + 1
            when not matched then insert (scope, name, last_value) values (s.scope, s.name, 1)
            output inserted.last_value;
            """,
    ).param("scope", scope).param("name", name).query(Int::class.java).single()
        .also { logger.debug { "Key number allocated scope=$scope name=$name value=$it" } }
}
