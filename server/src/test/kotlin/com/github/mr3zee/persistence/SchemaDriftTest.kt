package com.github.mr3zee.persistence

import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.v1.migration.jdbc.MigrationUtils
import org.testcontainers.postgresql.PostgreSQLContainer
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Verifies that Flyway migrations produce the same schema that Exposed Table definitions expect.
 * Catches drift when a developer changes a Kotlin Table object but forgets to write a migration.
 *
 * Limitation: only detects missing objects (Kotlin expects but DB lacks). Extra DB objects
 * (columns, indexes added in migration but not in Kotlin) are NOT caught — Exposed's
 * statementsRequiredToActualizeScheme is uni-directional.
 *
 * Requires Docker (Testcontainers spins up a real PostgreSQL instance).
 */
class SchemaDriftTest {

    @Test
    fun `Flyway migrations match Exposed table definitions`() {
        PostgreSQLContainer("postgres:18-alpine").use { pg ->
            pg.start()

            // Run Flyway migrations against real PostgreSQL
            Flyway.configure()
                .dataSource(pg.jdbcUrl, pg.username, pg.password)
                .locations("classpath:db/migration")
                .cleanDisabled(true)
                .validateOnMigrate(true)
                .load()
                .migrate()

            // Connect Exposed to the same database
            val database = Database.connect(
                pg.jdbcUrl,
                driver = "org.postgresql.Driver",
                user = pg.username,
                password = pg.password,
            )

            // Ask Exposed what SQL it would need to bring the schema in line with Kotlin definitions.
            // statementsRequiredToActualizeScheme is deprecated in Exposed but there is no replacement
            // available at version 1.1.1 — exposed-migration-jdbc was never released for this version.
            val drift = transaction(database) {
                MigrationUtils.statementsRequiredForDatabaseMigration(*ALL_TABLES, withLogs = true)
            }

            assertEquals(
                emptyList(),
                drift,
                "Flyway-migrated schema drifted from Exposed Table definitions. " +
                    "Missing statements:\n${drift.joinToString("\n")}",
            )
        }
    }
}
