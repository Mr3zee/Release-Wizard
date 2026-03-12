package com.github.mr3zee.connections

import com.github.mr3zee.api.CreateConnectionRequest
import com.github.mr3zee.api.UpdateConnectionRequest
import com.github.mr3zee.model.Connection
import com.github.mr3zee.model.ConnectionConfig
import com.github.mr3zee.model.ConnectionId

interface ConnectionsService {
    suspend fun listConnections(): List<Connection>
    suspend fun getConnection(id: ConnectionId): Connection?
    suspend fun createConnection(request: CreateConnectionRequest): Connection
    suspend fun updateConnection(id: ConnectionId, request: UpdateConnectionRequest): Connection?
    suspend fun deleteConnection(id: ConnectionId): Boolean
}

class DefaultConnectionsService(
    private val repository: ConnectionsRepository,
) : ConnectionsService {

    override suspend fun listConnections(): List<Connection> {
        return repository.findAll().map { it.masked() }
    }

    override suspend fun getConnection(id: ConnectionId): Connection? {
        return repository.findById(id)?.masked()
    }

    override suspend fun createConnection(request: CreateConnectionRequest): Connection {
        return repository.create(
            name = request.name,
            type = request.type,
            config = request.config,
        ).masked()
    }

    override suspend fun updateConnection(id: ConnectionId, request: UpdateConnectionRequest): Connection? {
        return repository.update(
            id = id,
            name = request.name,
            config = request.config,
        )?.masked()
    }

    override suspend fun deleteConnection(id: ConnectionId): Boolean {
        return repository.delete(id)
    }
}

private fun Connection.masked(): Connection = copy(config = config.masked())

private fun ConnectionConfig.masked(): ConnectionConfig = when (this) {
    is ConnectionConfig.SlackConfig -> copy(webhookUrl = mask(webhookUrl))
    is ConnectionConfig.TeamCityConfig -> copy(token = mask(token))
    is ConnectionConfig.GitHubConfig -> copy(token = mask(token))
    is ConnectionConfig.MavenCentralConfig -> copy(password = mask(password))
}

private fun mask(value: String): String {
    if (value.length <= 4) return "****"
    return "****" + value.takeLast(4)
}
