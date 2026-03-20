package com.github.mr3zee.persistence

import com.github.mr3zee.DatabaseConfig
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.slf4j.LoggerFactory
import javax.sql.DataSource

private val log = LoggerFactory.getLogger("com.github.mr3zee.persistence.DatabaseFactory")

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

fun initDatabase(ds: DataSource): Database {
    val database = Database.connect(ds)
    transaction(database) {
        // Create tables that don't exist. No ALTER of existing columns — avoids FK conflicts
        // when column types change between versions. Safe because there is no production data.
        SchemaUtils.create(
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
        )
    }
    // TEAM-H2, TEAM-H3, HOOK-M4: Create partial unique indexes (PostgreSQL only, graceful skip on H2)
    createPartialIndexes(ds)
    return database
}

/**
 * Creates partial unique indexes for defense-in-depth.
 * These ensure only one PENDING invite/request per (team, user) and only one active webhook token per (release, block).
 * PostgreSQL supports partial indexes; H2 does not — failures are logged and ignored.
 */
private fun createPartialIndexes(ds: DataSource) {
    // TAG-L3: Create composite index on (team_id, tag) for efficient team-scoped tag queries
    val regularIndexes = listOf(
        """CREATE INDEX IF NOT EXISTS idx_release_tags_team_tag
           ON release_tags (team_id, tag)""",
    )
    ds.connection.use { conn ->
        conn.autoCommit = true
        for (sql in regularIndexes) {
            try {
                conn.createStatement().use { stmt ->
                    stmt.execute(sql.trimIndent())
                }
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                log.warn("Index creation skipped: {}", e.message)
            }
        }
    }

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
