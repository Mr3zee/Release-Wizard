package com.github.mr3zee.connections

import com.github.mr3zee.api.ConnectionTestResult
import com.github.mr3zee.api.CreateConnectionRequest
import com.github.mr3zee.api.ExternalConfigParametersResponse
import com.github.mr3zee.api.ExternalConfigsResponse
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
    suspend fun fetchExternalConfigs(id: ConnectionId, expectedType: ConnectionType, session: UserSession): ExternalConfigsResponse
    suspend fun fetchExternalConfigParameters(id: ConnectionId, configId: String, session: UserSession): ExternalConfigParametersResponse
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
        // CONN-M3: Blank-name validation in service layer (not just route)
        require(request.name.isNotBlank()) { "Connection name must not be blank" }
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
        // CONN-M3: Blank-name validation on update path
        request.name?.let { require(it.isNotBlank()) { "Connection name must not be blank" } }
        // CONN-M2: Reject no-op PUT — at least one field must be provided
        require(request.name != null || request.config != null) {
            "At least one field (name or config) must be provided for update"
        }
        checkAccess(id, session)
        // CONN-H4: Fetch teamId before update to avoid TOCTOU if connection is deleted concurrently
        val teamId = repository.findTeamId(id)
        val resolvedConfig = request.config?.let { newConfig ->
            mergeConfigWithExistingSecrets(id, newConfig)
        }
        val updated = repository.update(
            id = id,
            name = request.name,
            config = resolvedConfig,
        ) ?: return null
        if (teamId != null) {
            auditService.log(
                TeamId(teamId), session,
                AuditAction.CONNECTION_UPDATED, AuditTargetType.CONNECTION,
                id.value, "Updated connection '${updated.name}'"
            )
        }
        return updated.masked()
    }

    override suspend fun deleteConnection(id: ConnectionId, session: UserSession): Boolean {
        checkAccessTeamLead(id, session)
        // CONN-H6: Atomic findTeamId + delete in single transaction to prevent TOCTOU
        val teamId = repository.deleteReturningTeamId(id)
            ?: throw NotFoundException("Connection not found")
        auditService.log(
            TeamId(teamId), session,
            AuditAction.CONNECTION_DELETED, AuditTargetType.CONNECTION,
            id.value, "Deleted connection"
        )
        return true
    }

    override suspend fun testConnection(id: ConnectionId, session: UserSession): ConnectionTestResult? {
        checkAccess(id, session)
        val connection = repository.findById(id) ?: return null
        return connectionTester.test(connection.config)
    }

    override suspend fun fetchExternalConfigs(id: ConnectionId, expectedType: ConnectionType, session: UserSession): ExternalConfigsResponse {
        checkAccess(id, session)
        val connection = repository.findById(id) ?: throw NotFoundException("Connection not found")
        if (connection.type != expectedType) {
            throw UnsupportedOperationException("Connection is ${connection.type}, expected $expectedType")
        }
        return when (val config = connection.config) {
            is ConnectionConfig.TeamCityConfig -> connectionTester.fetchTeamCityBuildTypes(config)
            is ConnectionConfig.GitHubConfig -> connectionTester.fetchGitHubWorkflows(config)
            else -> throw UnsupportedOperationException("Config discovery not supported for ${connection.type}")
        }
    }

    override suspend fun fetchExternalConfigParameters(id: ConnectionId, configId: String, session: UserSession): ExternalConfigParametersResponse {
        checkAccess(id, session)
        val connection = repository.findById(id) ?: throw NotFoundException("Connection not found")
        return when (val config = connection.config) {
            is ConnectionConfig.TeamCityConfig -> connectionTester.fetchTeamCityBuildTypeParameters(config, configId)
            is ConnectionConfig.GitHubConfig -> connectionTester.fetchGitHubWorkflowInputs(config, configId)
            else -> throw UnsupportedOperationException("Config parameter discovery not supported for ${connection.type}")
        }
    }

    override suspend fun findTeamId(id: ConnectionId): String? {
        return repository.findTeamId(id)
    }

    private suspend fun mergeConfigWithExistingSecrets(id: ConnectionId, newConfig: ConnectionConfig): ConnectionConfig {
        val existing = repository.findById(id)?.config ?: return newConfig
        return when (newConfig) {
            is ConnectionConfig.SlackConfig -> {
                val existingSlack = existing as? ConnectionConfig.SlackConfig
                if (isMasked(newConfig.webhookUrl) && existingSlack != null) {
                    newConfig.copy(webhookUrl = existingSlack.webhookUrl)
                } else newConfig
            }
            is ConnectionConfig.TeamCityConfig -> {
                val existingTc = existing as? ConnectionConfig.TeamCityConfig
                if (isMasked(newConfig.token) && existingTc != null) {
                    newConfig.copy(token = existingTc.token)
                } else newConfig
            }
            is ConnectionConfig.GitHubConfig -> {
                val existingGh = existing as? ConnectionConfig.GitHubConfig
                if (isMasked(newConfig.token) && existingGh != null) {
                    newConfig.copy(token = existingGh.token)
                } else newConfig
            }
        }
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
    is ConnectionConfig.TeamCityConfig -> copy(token = mask(token))
    is ConnectionConfig.GitHubConfig -> copy(token = mask(token))
}

private const val MASK = "********"

private fun mask(@Suppress("UNUSED_PARAMETER") value: String): String = MASK

private fun isMasked(value: String): Boolean = value == MASK
