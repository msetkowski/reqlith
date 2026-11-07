package io.reqlith.config

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import jakarta.annotation.PreDestroy
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Component

/**
 * Connection pool for the MCP server, logged in as a database user with SELECT rights only.
 * Even a bug in a tool cannot change data. Deliberately not a DataSource bean, so the
 * application's main DataSource stays auto-configured.
 */
@Component
class McpReadOnlyDatabase(properties: ReqlithProperties, main: DataSourceProperties) {
    private val dataSource = HikariDataSource(
        HikariConfig().apply {
            poolName = "mcp-read-only"
            jdbcUrl = properties.mcp.datasource.url ?: main.url
            username = properties.mcp.datasource.username
            password = properties.mcp.datasource.password
            isReadOnly = true
            maximumPoolSize = 4
            minimumIdle = 0
            initializationFailTimeout = -1 // connect lazily: the app also starts while the database is not up
        },
    )

    val jdbc: JdbcClient = JdbcClient.create(dataSource)

    @PreDestroy
    fun close() = dataSource.close()
}
