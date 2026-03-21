package com.github.mr3zee.persistence

import com.github.mr3zee.DatabaseConfig
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.migration.jdbc.MigrationUtils
import org.slf4j.LoggerFactory
import javax.sql.DataSource

private val log = LoggerFactory.getLogger("com.github.mr3zee.persistence.DatabaseFactory")

/** All table definitions — used by SchemaUtils (tests) and drift detection. */
val ALL_TABLES = arrayOf(
    UserTable,
    TeamTable,
    TeamMembershipTable,
    TeamInviteTable,
    JoinRequestTable,
    AuditEventTable,
    ProjectTemplateTable,
    ConnectionTable,
    ReleaseTable,
    BlockExecutionTable,
    NotificationConfigTable,
    ScheduleTable,
    TriggerTable,
    MavenTriggerTable,
    ReleaseTagTable,
    ProjectLockTable,
    StatusWebhookTokenTable,
    AccountLockoutTable,
    PasswordResetTokenTable,
    OAuthAccountTable,
)

fun dataSource(config: DatabaseConfig): DataSource {
    val hikariConfig = HikariConfig().apply {
        jdbcUrl = config.url
        username = config.user
        password = config.password
        driverClassName = config.driver
        maximumPoolSize = config.maxPoolSize
        // INFRA-M1: Prevent stale connections and improve pool health
        idleTimeout = 600_000          // 10 minutes — close idle connections after this
        keepaliveTime = 300_000        // 5 minutes — send keepalive to prevent firewall/proxy timeouts
        connectionTestQuery = "SELECT 1" // lightweight validation query
        validate()
    }
    return HikariDataSource(hikariConfig)
}

/**
 * Initializes the database schema.
 *
 * @param useFlyway true (default) for production — runs Flyway SQL migrations against PostgreSQL.
 *                  false for tests — uses Exposed SchemaUtils.create() against H2 for speed.
 */
fun initDatabase(ds: DataSource, useFlyway: Boolean = true): Database {
    if (useFlyway) {
        Flyway.configure()
            .dataSource(ds)
            .locations("classpath:db/migration")
            .cleanDisabled(true)
            .validateOnMigrate(true)
            .load()
            .migrate()
    }

    val database = Database.connect(ds)

    // Create partial indexes before drift detection so they exist on every run.
    // Must happen after Flyway (tables must exist) but before MigrationUtils check.
    createPartialIndexes(ds)

    if (useFlyway) {
        // After Flyway applies migrations, verify the schema matches Exposed Table definitions.
        // Fails fast on startup if a migration was forgotten or is out of sync.
        // Partial indexes (WITH WHERE clause) are intentionally outside Exposed's model,
        // so MigrationUtils sees them as "extra" and emits DROP INDEX statements — filter those out.
        val drift = transaction(database) {
            MigrationUtils.statementsRequiredForDatabaseMigration(*ALL_TABLES)
        }
        val realDrift = drift.filter { stmt ->
            PARTIAL_INDEX_NAMES.none { name -> stmt.contains(name, ignoreCase = true) }
        }
        if (realDrift.isNotEmpty()) {
            log.error("Schema drift detected after Flyway migration. Missing statements:\n{}", realDrift.joinToString("\n"))
            error("Database schema does not match Exposed Table definitions. ${realDrift.size} statements required.")
        }
    } else {
        transaction(database) {
            SchemaUtils.create(*ALL_TABLES)
        }
    }

    return database
}

/** Names of partial indexes managed by [createPartialIndexes] — used by drift detection to ignore expected extras. */
private val PARTIAL_INDEX_NAMES = setOf(
    "uq_invite_pending_team_user",
    "uq_join_request_pending_team_user",
    "uq_swt_active_release_block",
    "idx_users_pending_approval",
)

/**
 * Creates partial unique indexes for defense-in-depth.
 * These ensure only one PENDING invite/request per (team, user) and only one active webhook token per (release, block).
 * PostgreSQL supports partial indexes; H2 does not — failures are logged and ignored.
 *
 * Note: Regular indexes (including idx_release_tags_team_tag) are managed by Flyway migrations.
 * Only partial indexes with WHERE clauses remain here because H2 does not support them.
 * When adding a new partial index, also add its name to [PARTIAL_INDEX_NAMES].
 */
private fun createPartialIndexes(ds: DataSource) {
    val partialIndexes = listOf(
        // TEAM-H2: Only one PENDING invite per (team, user)
        """CREATE UNIQUE INDEX IF NOT EXISTS uq_invite_pending_team_user
           ON team_invites (team_id, invited_user_id)
           WHERE status = 'PENDING'""",
        // TEAM-H3: Only one PENDING join request per (team, user)
        """CREATE UNIQUE INDEX IF NOT EXISTS uq_join_request_pending_team_user
           ON join_requests (team_id, user_id)
           WHERE status = 'PENDING'""",
        // HOOK-M4: Only one active webhook token per (release, block)
        """CREATE UNIQUE INDEX IF NOT EXISTS uq_swt_active_release_block
           ON status_webhook_tokens (release_id, block_id)
           WHERE active = true""",
        // APPROVAL-M1: Efficient lookup of pending-approval users for admin screen
        """CREATE INDEX IF NOT EXISTS idx_users_pending_approval
           ON users (created_at)
           WHERE approved = false""",
    )
    ds.connection.use { conn ->
        // Ensure DDL commits immediately regardless of pool autoCommit setting
        conn.autoCommit = true
        for (sql in partialIndexes) {
            try {
                conn.createStatement().use { stmt ->
                    stmt.execute(sql.trimIndent())
                }
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                // H2 and some other DBs don't support partial indexes — this is fine,
                // application-level checks already prevent duplicates
                log.warn("Partial index creation skipped (unsupported by DB): {}", e.message)
            }
        }
    }
}
