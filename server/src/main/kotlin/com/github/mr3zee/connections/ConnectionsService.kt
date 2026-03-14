package com.github.mr3zee.connections

import com.github.mr3zee.ForbiddenException
import com.github.mr3zee.api.ConnectionTestResult
import com.github.mr3zee.api.CreateConnectionRequest
import com.github.mr3zee.api.UpdateConnectionRequest
import com.github.mr3zee.auth.UserSession
import com.github.mr3zee.model.Connection
import com.github.mr3zee.model.ConnectionConfig
import com.github.mr3zee.model.ConnectionId
import com.github.mr3zee.model.ConnectionType
import com.github.mr3zee.model.UserRole

interface ConnectionsService {
    suspend fun listConnections(
        session: UserSession,
        offset: Int = 0,
        limit: Int = 20,
        search: String? = null,
        type: ConnectionType? = null,
    ): Pair<List<Connection>, Long>
    suspend fun getConnection(id: ConnectionId, session: UserSession): Connection?
    suspend fun createConnection(request: CreateConnectionRequest, session: UserSession): Connection
    suspend fun updateConnection(id: ConnectionId, request: UpdateConnectionRequest, session: UserSession): Connection?
    suspend fun deleteConnection(id: ConnectionId, session: UserSession): Boolean
    suspend fun testConnection(id: ConnectionId, session: UserSession): ConnectionTestResult?
}

class DefaultConnectionsService(
    private val repository: ConnectionsRepository,
    private val connectionTester: ConnectionTester,
) : ConnectionsService {

    override suspend fun listConnections(
        session: UserSession,
        offset: Int,
        limit: Int,
        search: String?,
        type: ConnectionType?,
    ): Pair<List<Connection>, Long> {
        val ownerId = if (session.role == UserRole.ADMIN) null else session.userId
        val (connections, totalCount) = repository.findAllWithCount(ownerId = ownerId, offset = offset, limit = limit, search = search, type = type)
        return connections.map { it.masked() } to totalCount
    }

    override suspend fun getConnection(id: ConnectionId, session: UserSession): Connection? {
        checkAccess(id, session)
        return repository.findById(id)?.masked()
    }

    override suspend fun createConnection(request: CreateConnectionRequest, session: UserSession): Connection {
        return repository.create(
            name = request.name,
            type = request.type,
            config = request.config,
            ownerId = session.userId,
        ).masked()
    }

    override suspend fun updateConnection(id: ConnectionId, request: UpdateConnectionRequest, session: UserSession): Connection? {
        checkAccess(id, session)
        return repository.update(
            id = id,
            name = request.name,
            config = request.config,
        )?.masked()
    }

    override suspend fun deleteConnection(id: ConnectionId, session: UserSession): Boolean {
        checkAccess(id, session)
        return repository.delete(id)
    }

    override suspend fun testConnection(id: ConnectionId, session: UserSession): ConnectionTestResult? {
        checkAccess(id, session)
        val connection = repository.findById(id) ?: return null
        return connectionTester.test(connection.config)
    }

    private suspend fun checkAccess(id: ConnectionId, session: UserSession) {
        if (session.role == UserRole.ADMIN) return
        val ownerId = repository.findOwner(id)
        if (ownerId != null && ownerId != session.userId) {
            throw ForbiddenException("Access denied")
        }
    }
}

private fun Connection.masked(): Connection = copy(config = config.masked())

private fun ConnectionConfig.masked(): ConnectionConfig = when (this) {
    is ConnectionConfig.SlackConfig -> copy(webhookUrl = mask(webhookUrl))
    is ConnectionConfig.TeamCityConfig -> copy(token = mask(token), webhookSecret = maskOptional(webhookSecret))
    is ConnectionConfig.GitHubConfig -> copy(token = mask(token), webhookSecret = maskOptional(webhookSecret))
    is ConnectionConfig.MavenCentralConfig -> copy(password = mask(password))
}

private fun mask(value: String): String {
    if (value.length <= 4) return "****"
    return "****" + value.takeLast(4)
}

private fun maskOptional(value: String): String {
    if (value.isEmpty()) return ""
    return mask(value)
}
