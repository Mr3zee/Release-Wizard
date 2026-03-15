package com.github.mr3zee.connections

import com.github.mr3zee.AppJson
import com.github.mr3zee.model.*
import com.github.mr3zee.persistence.ConnectionTable
import com.github.mr3zee.persistence.likeContains
import com.github.mr3zee.persistence.safeOffset
import com.github.mr3zee.security.EncryptionService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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

    private fun buildConnectionConditions(
        teamId: String?,
        teamIds: List<String>?,
        search: String?,
        type: ConnectionType?,
    ): List<Op<Boolean>> {
        val conditions = mutableListOf<Op<Boolean>>()
        if (teamId != null) {
            conditions.add(ConnectionTable.teamId eq UUID.fromString(teamId))
        } else if (teamIds != null) {
            val uuids = teamIds.mapNotNull { runCatching { UUID.fromString(it) }.getOrNull() }
            conditions.add(ConnectionTable.teamId inList uuids)
        }
        if (!search.isNullOrBlank()) {
            conditions.add(ConnectionTable.name.lowerCase() like likeContains(search))
        }
        if (type != null) {
            conditions.add(ConnectionTable.type eq type)
        }
        return conditions
    }

    override suspend fun findAll(
        teamId: String?,
        teamIds: List<String>?,
        offset: Int,
        limit: Int,
        search: String?,
        type: ConnectionType?,
    ): List<Connection> = dbQuery {
        val conditions = buildConnectionConditions(teamId, teamIds, search, type)
        val query = ConnectionTable.selectAll()
        if (conditions.isNotEmpty()) {
            query.where { conditions.reduce { acc, op -> acc and op } }
        }
        query.orderBy(ConnectionTable.updatedAt, SortOrder.DESC)
            .limit(limit)
            .offset(safeOffset(offset))
            .map { it.toConnection() }
    }

    override suspend fun countAll(
        teamId: String?,
        teamIds: List<String>?,
        search: String?,
        type: ConnectionType?,
    ): Long = dbQuery {
        val conditions = buildConnectionConditions(teamId, teamIds, search, type)
        val query = ConnectionTable.selectAll()
        if (conditions.isNotEmpty()) {
            query.where { conditions.reduce { acc, op -> acc and op } }
        }
        query.count()
    }

    override suspend fun findAllWithCount(
        teamId: String?,
        teamIds: List<String>?,
        offset: Int,
        limit: Int,
        search: String?,
        type: ConnectionType?,
    ): Pair<List<Connection>, Long> = dbQuery {
        val conditions = buildConnectionConditions(teamId, teamIds, search, type)

        val countQuery = ConnectionTable.selectAll()
        if (conditions.isNotEmpty()) {
            countQuery.where { conditions.reduce { acc, op -> acc and op } }
        }
        val totalCount = countQuery.count()

        val dataQuery = ConnectionTable.selectAll()
        if (conditions.isNotEmpty()) {
            dataQuery.where { conditions.reduce { acc, op -> acc and op } }
        }
        val items = dataQuery.orderBy(ConnectionTable.updatedAt, SortOrder.DESC)
            .limit(limit)
            .offset(safeOffset(offset))
            .map { it.toConnection() }

        items to totalCount
    }

    override suspend fun findById(id: ConnectionId): Connection? = dbQuery {
        ConnectionTable.selectAll()
            .where { ConnectionTable.id eq UUID.fromString(id.value) }
            .singleOrNull()
            ?.toConnection()
    }

    override suspend fun findTeamId(id: ConnectionId): String? = dbQuery {
        ConnectionTable.select(ConnectionTable.teamId)
            .where { ConnectionTable.id eq UUID.fromString(id.value) }
            .singleOrNull()
            ?.get(ConnectionTable.teamId)?.value?.toString()
    }

    override suspend fun findTeamIds(ids: List<ConnectionId>): Map<ConnectionId, String> = dbQuery {
        if (ids.isEmpty()) return@dbQuery emptyMap()
        val uuids = ids.mapNotNull { runCatching { UUID.fromString(it.value) }.getOrNull() }
        ConnectionTable.select(ConnectionTable.id, ConnectionTable.teamId)
            .where { ConnectionTable.id inList uuids }
            .associate {
                ConnectionId(it[ConnectionTable.id].value.toString()) to it[ConnectionTable.teamId].value.toString()
            }
    }

    override suspend fun create(
        name: String,
        type: ConnectionType,
        config: ConnectionConfig,
        teamId: String,
    ): Connection = dbQuery {
        val now = Clock.System.now()
        val id = UUID.randomUUID()
        val encryptedConfig = encryption.encrypt(AppJson.encodeToString(config))
        ConnectionTable.insert {
            it[ConnectionTable.id] = id
            it[ConnectionTable.name] = name
            it[ConnectionTable.type] = type
            it[ConnectionTable.encryptedConfig] = encryptedConfig
            it[ConnectionTable.teamId] = UUID.fromString(teamId)
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
