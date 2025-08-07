package io.github.mr3zee.rwizard.database

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction

object DatabaseConfig {
    fun init() {
        Database.connect(
            url = "jdbc:h2:./data/rwizard;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE;AUTO_SERVER=TRUE",
            driver = "org.h2.Driver"
        )
        
        createTables()
    }
    
    private fun createTables() {
        transaction {
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
