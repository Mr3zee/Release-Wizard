package com.github.mr3zee.projects

import com.github.mr3zee.api.CreateProjectRequest
import com.github.mr3zee.api.UpdateProjectRequest
import com.github.mr3zee.audit.AuditService
import com.github.mr3zee.NotFoundException
import com.github.mr3zee.auth.UserSession
import com.github.mr3zee.connections.ConnectionsRepository
import com.github.mr3zee.model.*
import com.github.mr3zee.model.collectConnectionIds
import com.github.mr3zee.teams.TeamAccessService

interface ProjectsService {
    suspend fun listProjects(session: UserSession, teamId: TeamId? = null, offset: Int = 0, limit: Int = 20, search: String? = null): Pair<List<ProjectTemplate>, Long>
    suspend fun getProject(id: ProjectId, session: UserSession): ProjectTemplate?
    suspend fun createProject(request: CreateProjectRequest, session: UserSession): ProjectTemplate
    suspend fun updateProject(id: ProjectId, request: UpdateProjectRequest, session: UserSession): ProjectTemplate?
    suspend fun deleteProject(id: ProjectId, session: UserSession): Boolean
    suspend fun findTeamId(id: ProjectId): String?
}

class DefaultProjectsService(
    private val repository: ProjectsRepository,
    private val teamAccessService: TeamAccessService,
    private val auditService: AuditService,
    private val connectionsRepository: ConnectionsRepository,
) : ProjectsService {

    override suspend fun listProjects(session: UserSession, teamId: TeamId?, offset: Int, limit: Int, search: String?): Pair<List<ProjectTemplate>, Long> {
        return when {
            teamId != null -> {
                teamAccessService.checkMembership(teamId, session)
                repository.findAllWithCount(teamId = teamId.value, offset = offset, limit = limit, search = search)
            }
            session.role == UserRole.ADMIN -> {
                repository.findAllWithCount(offset = offset, limit = limit, search = search)
            }
            else -> {
                val teamIds = teamAccessService.getUserTeamIds(session.userId).map { it.value }
                repository.findAllWithCount(teamIds = teamIds, offset = offset, limit = limit, search = search)
            }
        }
    }

    override suspend fun getProject(id: ProjectId, session: UserSession): ProjectTemplate? {
        checkAccess(id, session)
        return repository.findById(id)
    }

    override suspend fun createProject(request: CreateProjectRequest, session: UserSession): ProjectTemplate {
        teamAccessService.checkMembership(request.teamId, session)
        validateConnectionTeamConsistency(request.dagGraph, request.teamId.value)
        val project = repository.create(
            name = request.name,
            description = request.description,
            dagGraph = request.dagGraph,
            parameters = request.parameters,
            teamId = request.teamId.value,
            defaultTags = request.defaultTags,
        )
        auditService.log(request.teamId, session, AuditAction.PROJECT_CREATED, AuditTargetType.PROJECT, project.id.value, "Created project '${project.name}'")
        return project
    }

    override suspend fun updateProject(id: ProjectId, request: UpdateProjectRequest, session: UserSession): ProjectTemplate? {
        checkAccess(id, session)
        return repository.update(
            id = id,
            name = request.name,
            description = request.description,
            dagGraph = request.dagGraph,
            parameters = request.parameters,
            defaultTags = request.defaultTags,
        )
    }

    override suspend fun deleteProject(id: ProjectId, session: UserSession): Boolean {
        checkAccessTeamLead(id, session)
        val teamId = repository.findTeamId(id)
        val deleted = repository.delete(id)
        if (deleted && teamId != null) {
            auditService.log(TeamId(teamId), session, AuditAction.PROJECT_DELETED, AuditTargetType.PROJECT, id.value)
        }
        return deleted
    }

    override suspend fun findTeamId(id: ProjectId): String? {
        return repository.findTeamId(id)
    }

    private suspend fun checkAccess(id: ProjectId, session: UserSession) {
        if (session.role == UserRole.ADMIN) return
        val teamId = repository.findTeamId(id) ?: throw NotFoundException("Resource not found")
        teamAccessService.checkMembership(TeamId(teamId), session)
    }

    private suspend fun checkAccessTeamLead(id: ProjectId, session: UserSession) {
        if (session.role == UserRole.ADMIN) return
        val teamId = repository.findTeamId(id) ?: throw NotFoundException("Resource not found")
        teamAccessService.checkTeamLead(TeamId(teamId), session)
    }

    /**
     * Validates that all connections referenced in the DAG belong to the expected team.
     * Uses batch fetching to avoid N+1 queries.
     */
    private suspend fun validateConnectionTeamConsistency(dagGraph: DagGraph, expectedTeamId: String) {
        val connectionIds = dagGraph.collectConnectionIds()
        if (connectionIds.isEmpty()) return
        val teamIdMap = connectionsRepository.findTeamIds(connectionIds)
        for ((connId, connTeamId) in teamIdMap) {
            if (connTeamId != expectedTeamId) {
                throw IllegalArgumentException("Connection ${connId.value} belongs to a different team")
            }
        }
    }
}
