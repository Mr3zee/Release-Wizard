package com.github.mr3zee.connections

import com.github.mr3zee.model.Connection
import com.github.mr3zee.model.ConnectionConfig
import com.github.mr3zee.model.ConnectionId
import com.github.mr3zee.model.ConnectionType

interface ConnectionsRepository {
    suspend fun findAll(
        ownerId: String? = null,
        offset: Int = 0,
        limit: Int = 20,
        search: String? = null,
        type: ConnectionType? = null,
    ): List<Connection>
    suspend fun countAll(
        ownerId: String? = null,
        search: String? = null,
        type: ConnectionType? = null,
    ): Long
    suspend fun findAllWithCount(
        ownerId: String? = null,
        offset: Int = 0,
        limit: Int = 20,
        search: String? = null,
        type: ConnectionType? = null,
    ): Pair<List<Connection>, Long>
    suspend fun findById(id: ConnectionId): Connection?
    suspend fun findOwner(id: ConnectionId): String?
    suspend fun create(name: String, type: ConnectionType, config: ConnectionConfig, ownerId: String): Connection
    suspend fun update(id: ConnectionId, name: String?, config: ConnectionConfig?): Connection?
    suspend fun delete(id: ConnectionId): Boolean
}
