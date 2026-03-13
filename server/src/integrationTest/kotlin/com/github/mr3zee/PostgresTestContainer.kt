package com.github.mr3zee

import org.testcontainers.containers.PostgreSQLContainer

object PostgresTestContainer {
    private val container = PostgreSQLContainer("postgres:16-alpine").apply {
        withDatabaseName("release_wizard_test")
        withUsername("test")
        withPassword("test")
    }

    fun start(): DatabaseConfig {
        if (!container.isRunning) container.start()
        return DatabaseConfig(
            url = container.jdbcUrl,
            user = container.username,
            password = container.password,
            driver = "org.postgresql.Driver",
        )
    }

    fun stop() {
        container.stop()
    }
}
