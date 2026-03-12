package com.github.mr3zee.connections

import com.github.mr3zee.model.Connection
import com.github.mr3zee.model.ConnectionConfig
import com.github.mr3zee.model.ConnectionId
import com.github.mr3zee.model.ConnectionType

interface ConnectionsRepository {
    suspend fun findAll(): List<Connection>
    suspend fun findById(id: ConnectionId): Connection?
    suspend fun create(name: String, type: ConnectionType, config: ConnectionConfig): Connection
    suspend fun update(id: ConnectionId, name: String?, config: ConnectionConfig?): Connection?
    suspend fun delete(id: ConnectionId): Boolean
}
