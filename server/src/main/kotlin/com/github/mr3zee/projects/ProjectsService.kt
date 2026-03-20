package com.github.mr3zee.projects

import com.github.mr3zee.api.CreateProjectRequest
import com.github.mr3zee.api.UpdateProjectRequest
import com.github.mr3zee.audit.AuditService
import com.github.mr3zee.dag.DagValidator
import com.github.mr3zee.NotFoundException
import com.github.mr3zee.auth.UserSession
import com.github.mr3zee.connections.ConnectionsRepository
import com.github.mr3zee.model.*
import com.github.mr3zee.model.collectConnectionIds
import com.github.mr3zee.teams.TeamAccessService
import org.slf4j.LoggerFactory

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
    private val lockRepository: ProjectLockRepository,
) : ProjectsService {
    private val log = LoggerFactory.getLogger(DefaultProjectsService::class.java)

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
        // PROJ-H4: Blank-name validation in service layer (not just route)
        require(request.name.isNotBlank()) { "Project name must not be blank" }
        teamAccessService.checkMembership(request.teamId, session)
        // PROJ-H2: Validate DAG structure on create
        validateDagGraph(request.dagGraph)
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
        log.info("Project created: {} (name='{}', team={})", project.id.value, project.name, request.teamId.value)
        return project
    }

    override suspend fun updateProject(id: ProjectId, request: UpdateProjectRequest, session: UserSession): ProjectTemplate? {
        // PROJ-H4: Blank-name validation in service layer
        request.name?.let { require(it.isNotBlank()) { "Project name must not be blank" } }
        checkAccess(id, session)
        // PROJ-H2: Validate DAG structure on update
        val dagGraph = request.dagGraph
        if (dagGraph != null) {
            validateDagGraph(dagGraph)
            // PROJ-H1: Validate connection team consistency on update (not just create)
            val teamId = repository.findTeamId(id) ?: throw NotFoundException("Project not found")
            validateConnectionTeamConsistency(dagGraph, teamId)
        }
        // PROJ-C1: Lock check + update in a single transaction to prevent TOCTOU race
        val updated = repository.updateWithLockCheck(
            id = id,
            callerUserId = session.userId,
            name = request.name,
            description = request.description,
            dagGraph = request.dagGraph,
            parameters = request.parameters,
            defaultTags = request.defaultTags,
        )
        if (updated != null) {
            // PROJ-M3: Audit project updates
            val teamId = repository.findTeamId(id)
            if (teamId != null) {
                auditService.log(TeamId(teamId), session, AuditAction.PROJECT_UPDATED, AuditTargetType.PROJECT, id.value, "Updated project '${updated.name}'")
            }
            log.info("Project updated: {}", id.value)
        }
        return updated
    }

    override suspend fun deleteProject(id: ProjectId, session: UserSession): Boolean {
        checkAccessTeamLead(id, session)
        val teamId = repository.findTeamId(id)
            ?: throw NotFoundException("Project not found")
        val deleted = repository.delete(id)
        if (deleted) {
            log.info("Project deleted: {} (team={})", id.value, teamId)
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
     * PROJ-H2: Validates DAG structure — duplicate IDs, self-loops, invalid edges, cycles.
     */
    private fun validateDagGraph(dagGraph: DagGraph) {
        val errors = DagValidator.validate(dagGraph)
        if (errors.isNotEmpty()) {
            val messages = errors.joinToString("; ") { error ->
                when (error) {
                    is com.github.mr3zee.dag.ValidationError.DuplicateBlockId -> "Duplicate block ID: ${error.blockId.value}"
                    is com.github.mr3zee.dag.ValidationError.SelfLoop -> "Self-loop on block: ${error.edge.fromBlockId.value}"
                    is com.github.mr3zee.dag.ValidationError.InvalidEdgeReference -> "Invalid edge reference: ${error.missingBlockId.value}"
                    is com.github.mr3zee.dag.ValidationError.CycleDetected -> "Cycle detected involving: ${error.involvedBlockIds.joinToString { it.value }}"
                }
            }
            throw IllegalArgumentException("Invalid DAG: $messages")
        }
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
