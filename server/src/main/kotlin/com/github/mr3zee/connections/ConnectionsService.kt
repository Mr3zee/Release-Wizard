package com.github.mr3zee.connections

import com.github.mr3zee.api.ConnectionTestResult
import com.github.mr3zee.api.CreateConnectionRequest
import com.github.mr3zee.api.UpdateConnectionRequest
import com.github.mr3zee.audit.AuditService
import com.github.mr3zee.NotFoundException
import com.github.mr3zee.auth.UserSession
import com.github.mr3zee.model.*
import com.github.mr3zee.teams.TeamAccessService

interface ConnectionsService {
    suspend fun listConnections(
        session: UserSession,
        teamId: TeamId? = null,
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
    suspend fun findTeamId(id: ConnectionId): String?
}

class DefaultConnectionsService(
    private val repository: ConnectionsRepository,
    private val connectionTester: ConnectionTester,
    private val teamAccessService: TeamAccessService,
    private val auditService: AuditService,
) : ConnectionsService {

    override suspend fun listConnections(
        session: UserSession,
        teamId: TeamId?,
        offset: Int,
        limit: Int,
        search: String?,
        type: ConnectionType?,
    ): Pair<List<Connection>, Long> {
        val (connections, totalCount) = when {
            teamId != null -> {
                teamAccessService.checkMembership(teamId, session)
                repository.findAllWithCount(teamId = teamId.value, offset = offset, limit = limit, search = search, type = type)
            }
            session.role == UserRole.ADMIN -> {
                repository.findAllWithCount(offset = offset, limit = limit, search = search, type = type)
            }
            else -> {
                val teamIds = teamAccessService.getUserTeamIds(session.userId).map { it.value }
                repository.findAllWithCount(teamIds = teamIds, offset = offset, limit = limit, search = search, type = type)
            }
        }
        return connections.map { it.masked() } to totalCount
    }

    override suspend fun getConnection(id: ConnectionId, session: UserSession): Connection? {
        checkAccess(id, session)
        return repository.findById(id)?.masked()
    }

    override suspend fun createConnection(request: CreateConnectionRequest, session: UserSession): Connection {
        teamAccessService.checkMembership(request.teamId, session)
        val connection = repository.create(
            name = request.name,
            type = request.type,
            config = request.config,
            teamId = request.teamId.value,
        )
        auditService.log(
            request.teamId, session,
            AuditAction.CONNECTION_CREATED, AuditTargetType.CONNECTION,
            connection.id.value, "Created connection '${request.name}'"
        )
        return connection.masked()
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
        checkAccessTeamLead(id, session)
        val teamId = repository.findTeamId(id)
            ?: throw NotFoundException("Connection not found")
        val deleted = repository.delete(id)
        if (deleted) {
            auditService.log(
                TeamId(teamId), session,
                AuditAction.CONNECTION_DELETED, AuditTargetType.CONNECTION,
                id.value, "Deleted connection"
            )
        }
        return deleted
    }

    override suspend fun testConnection(id: ConnectionId, session: UserSession): ConnectionTestResult? {
        checkAccess(id, session)
        val connection = repository.findById(id) ?: return null
        return connectionTester.test(connection.config)
    }

    override suspend fun findTeamId(id: ConnectionId): String? {
        return repository.findTeamId(id)
    }

    private suspend fun checkAccess(id: ConnectionId, session: UserSession) {
        if (session.role == UserRole.ADMIN) return
        val teamId = repository.findTeamId(id) ?: throw NotFoundException("Resource not found")
        teamAccessService.checkMembership(TeamId(teamId), session)
    }

    private suspend fun checkAccessTeamLead(id: ConnectionId, session: UserSession) {
        if (session.role == UserRole.ADMIN) return
        val teamId = repository.findTeamId(id) ?: throw NotFoundException("Resource not found")
        teamAccessService.checkTeamLead(TeamId(teamId), session)
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
