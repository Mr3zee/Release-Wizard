package io.github.mr3zee.rwizard.database

import io.github.mr3zee.rwizard.database.DatabaseConfig.database
import io.r2dbc.spi.IsolationLevel
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.core.Transaction
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabase
import org.jetbrains.exposed.v1.r2dbc.SchemaUtils
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction

object DatabaseConfig {
    lateinit var database: R2dbcDatabase

    fun init() {
        // Build PostgreSQL R2DBC URL from environment variables with sensible defaults
        fun env(name: String, default: String? = null): String? =
            System.getenv(name) ?: System.getProperty(name) ?: default

        val url = env("POSTGRES_URL")
            ?: env("DATABASE_URL")
            ?: run {
                val host = env("DB_HOST", "localhost")!!
                val port = env("DB_PORT", "5432")!!
                val dbName = env("DB_NAME", "rwizard")!!
                val user = env("DB_USER", "postgres")!!
                val password = env("DB_PASSWORD", "postgres")!!
                "r2dbc:postgresql://$host:$port/$dbName?user=$user&password=$password"
            }

        database = R2dbcDatabase.connect(
            url,
            databaseConfig = {
                defaultMaxAttempts = 3
                defaultR2dbcIsolationLevel = IsolationLevel.READ_COMMITTED
            }
        )
        // Create tables on startup using a suspended transaction
        runBlocking { createTables() }
    }

    private suspend fun createTables() {
        tr {
            SchemaUtils.create(
                Users,
                UserCredentials,
                UserSessions,
                Connections,
                ConnectionCredentials,
                Projects,
                ProjectConnections,
                MessageTemplates,
                Releases,
                BlockExecutions,
                ExecutionLogs,
                UserInputs
            )
        }
    }
}

suspend fun <T> tr(block: suspend Transaction.() -> T): T =
    suspendTransaction(db = database) { block() }
