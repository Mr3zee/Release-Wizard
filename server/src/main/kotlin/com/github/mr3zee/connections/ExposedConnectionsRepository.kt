package com.github.mr3zee.connections

import com.github.mr3zee.AppJson
import com.github.mr3zee.model.*
import com.github.mr3zee.persistence.ConnectionTable
import com.github.mr3zee.security.EncryptionService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import java.util.UUID
import kotlin.time.Clock

class ExposedConnectionsRepository(
    private val db: Database,
    private val encryption: EncryptionService,
) : ConnectionsRepository {

    private suspend fun <T> dbQuery(block: suspend () -> T): T =
        withContext(Dispatchers.IO) { suspendTransaction(db) { block() } }

    private fun ResultRow.toConnection(): Connection {
        val decryptedJson = encryption.decrypt(this[ConnectionTable.encryptedConfig])
        val config = AppJson.decodeFromString<ConnectionConfig>(decryptedJson)
        return Connection(
            id = ConnectionId(this[ConnectionTable.id].value.toString()),
            name = this[ConnectionTable.name],
            type = this[ConnectionTable.type],
            config = config,
            createdAt = this[ConnectionTable.createdAt],
            updatedAt = this[ConnectionTable.updatedAt],
        )
    }

    override suspend fun findAll(): List<Connection> = dbQuery {
        ConnectionTable.selectAll()
            .orderBy(ConnectionTable.updatedAt, SortOrder.DESC)
            .map { it.toConnection() }
    }

    override suspend fun findById(id: ConnectionId): Connection? = dbQuery {
        ConnectionTable.selectAll()
            .where { ConnectionTable.id eq UUID.fromString(id.value) }
            .singleOrNull()
            ?.toConnection()
    }

    override suspend fun create(
        name: String,
        type: ConnectionType,
        config: ConnectionConfig,
    ): Connection = dbQuery {
        val now = Clock.System.now()
        val id = UUID.randomUUID()
        val encryptedConfig = encryption.encrypt(AppJson.encodeToString(config))
        ConnectionTable.insert {
            it[ConnectionTable.id] = id
            it[ConnectionTable.name] = name
            it[ConnectionTable.type] = type
            it[ConnectionTable.encryptedConfig] = encryptedConfig
            it[ConnectionTable.createdAt] = now
            it[ConnectionTable.updatedAt] = now
        }
        Connection(
            id = ConnectionId(id.toString()),
            name = name,
            type = type,
            config = config,
            createdAt = now,
            updatedAt = now,
        )
    }

    override suspend fun update(
        id: ConnectionId,
        name: String?,
        config: ConnectionConfig?,
    ): Connection? = dbQuery {
        val uuid = UUID.fromString(id.value)
        val now = Clock.System.now()
        val updated = ConnectionTable.update({ ConnectionTable.id eq uuid }) { stmt ->
            name?.let { stmt[ConnectionTable.name] = it }
            config?.let { stmt[ConnectionTable.encryptedConfig] = encryption.encrypt(AppJson.encodeToString(it)) }
            stmt[ConnectionTable.updatedAt] = now
        }
        if (updated > 0) {
            ConnectionTable.selectAll()
                .where { ConnectionTable.id eq uuid }
                .single()
                .toConnection()
        } else {
            null
        }
    }

    override suspend fun delete(id: ConnectionId): Boolean = dbQuery {
        val deleted = ConnectionTable.deleteWhere {
            ConnectionTable.id eq UUID.fromString(id.value)
        }
        deleted > 0
    }
}
