package com.github.mr3zee.persistence

import com.github.mr3zee.DatabaseConfig
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import javax.sql.DataSource

fun dataSource(config: DatabaseConfig): DataSource {
    val hikariConfig = HikariConfig().apply {
        jdbcUrl = config.url
        username = config.user
        password = config.password
        driverClassName = config.driver
        maximumPoolSize = config.maxPoolSize
        validate()
    }
    return HikariDataSource(hikariConfig)
}

fun initDatabase(ds: DataSource): Database {
    val database = Database.connect(ds)
    transaction(database) {
        // todo claude:
        //   [DEPRECATION] 'fun createMissingTablesAndColumns(vararg tables: Table, inBatch: Boolean = ..., withLogs: Boolean = ...): Unit' is deprecated. Execution of this function might lead to unpredictable state in the database if a failure occurs at any point. To prevent this, please use `MigrationUtils.statementsRequiredForDatabaseMigration()` with a third-party migration tool (e.g., Flyway). `MigrationUtils` is accessible with a dependency on `exposed-migration-jdbc`.
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
            // PendingWebhookTable removed — orphaned table, drop manually if needed
            NotificationConfigTable,
            ScheduleTable,
            TriggerTable,
            MavenTriggerTable,
            ReleaseTagTable,
            ProjectLockTable,
            StatusWebhookTokenTable,
        )
    }
    return database
}
