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
        database = R2dbcDatabase.connect(
            "r2dbc:h2:file:///./data/rwizard",
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
