package com.github.mr3zee.releases

import com.github.mr3zee.ForbiddenException
import com.github.mr3zee.api.ApproveBlockRequest
import com.github.mr3zee.api.CreateReleaseRequest
import com.github.mr3zee.audit.AuditService
import com.github.mr3zee.NotFoundException
import com.github.mr3zee.auth.UserSession
import com.github.mr3zee.connections.ConnectionsRepository
import com.github.mr3zee.execution.ExecutionEngine
import com.github.mr3zee.model.*
import com.github.mr3zee.model.collectConnectionIds
import com.github.mr3zee.model.findActionBlock
import com.github.mr3zee.projects.ProjectsRepository
import com.github.mr3zee.tags.TagRepository
import com.github.mr3zee.teams.TeamAccessService
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Clock

interface ReleasesService {
    suspend fun listReleases(
        session: UserSession,
        teamId: TeamId? = null,
        offset: Int = 0,
        limit: Int = 20,
        search: String? = null,
        status: ReleaseStatus? = null,
        projectTemplateId: ProjectId? = null,
        tag: String? = null,
    ): Pair<List<Release>, Long>
    suspend fun getRelease(id: ReleaseId): Release?
    suspend fun getBlockExecutions(releaseId: ReleaseId): List<BlockExecution>
    suspend fun startRelease(request: CreateReleaseRequest, session: UserSession): Release
    suspend fun startScheduledRelease(projectId: ProjectId, parameters: List<Parameter>): Release
    suspend fun rerunRelease(id: ReleaseId, session: UserSession): Release
    suspend fun cancelRelease(id: ReleaseId, session: UserSession): Boolean
    suspend fun archiveRelease(id: ReleaseId, session: UserSession): Boolean
    suspend fun deleteRelease(id: ReleaseId, session: UserSession): Boolean
    suspend fun awaitRelease(id: ReleaseId)
    suspend fun restartBlock(releaseId: ReleaseId, blockId: BlockId, session: UserSession): Boolean
    suspend fun approveBlock(releaseId: ReleaseId, blockId: BlockId, request: ApproveBlockRequest, session: UserSession): Boolean
    suspend fun checkAccess(id: ReleaseId, session: UserSession)
}

class DefaultReleasesService(
    private val repository: ReleasesRepository,
    private val projectsRepository: ProjectsRepository,
    private val executionEngine: ExecutionEngine,
    private val tagRepository: TagRepository,
    private val teamAccessService: TeamAccessService,
    private val auditService: AuditService,
    private val connectionsRepository: ConnectionsRepository,
) : ReleasesService {

    /** Per-(releaseId, blockId) mutex to serialize concurrent approvals and prevent read-modify-write races. */
    private val approvalMutexes = ConcurrentHashMap<Pair<String, String>, Mutex>()

    override suspend fun listReleases(
        session: UserSession,
        teamId: TeamId?,
        offset: Int,
        limit: Int,
        search: String?,
        status: ReleaseStatus?,
        projectTemplateId: ProjectId?,
        tag: String?,
    ): Pair<List<Release>, Long> {
        // If tag is provided, resolve matching release IDs first
        val releaseIds = if (tag != null) {
            val ids = tagRepository.findReleaseIdsByTag(tag).toSet()
            if (ids.isEmpty()) return emptyList<Release>() to 0L
            ids
        } else null

        return when {
            teamId != null -> {
                teamAccessService.checkMembership(teamId, session)
                repository.findAllWithCount(
                    teamId = teamId.value, offset = offset, limit = limit,
                    search = search, status = status, projectTemplateId = projectTemplateId,
                    releaseIds = releaseIds,
                )
            }
            session.role == UserRole.ADMIN -> {
                repository.findAllWithCount(
                    offset = offset, limit = limit,
                    search = search, status = status, projectTemplateId = projectTemplateId,
                    releaseIds = releaseIds,
                )
            }
            else -> {
                val teamIds = teamAccessService.getUserTeamIds(session.userId).map { it.value }
                repository.findAllWithCount(
                    teamIds = teamIds, offset = offset, limit = limit,
                    search = search, status = status, projectTemplateId = projectTemplateId,
                    releaseIds = releaseIds,
                )
            }
        }
    }

    override suspend fun getRelease(id: ReleaseId): Release? {
        return repository.findById(id)
    }

    override suspend fun getBlockExecutions(releaseId: ReleaseId): List<BlockExecution> {
        return repository.findBlockExecutions(releaseId)
    }

    override suspend fun startRelease(request: CreateReleaseRequest, session: UserSession): Release {
        val project = projectsRepository.findById(request.projectTemplateId)
            ?: throw IllegalArgumentException("Project not found: ${request.projectTemplateId.value}")

        // Get teamId from project -- releases inherit team from their project
        val projectTeamId = projectsRepository.findTeamId(request.projectTemplateId)
            ?: throw NotFoundException("Project not found")
        teamAccessService.checkMembership(TeamId(projectTeamId), session)
        validateConnectionTeamConsistency(project.dagGraph, projectTeamId)

        if (project.dagGraph.blocks.isEmpty()) {
            throw IllegalArgumentException("Project has no blocks to execute")
        }

        val mergedParams = mergeParameters(project.parameters, request.parameters)

        val release = repository.create(
            projectTemplateId = project.id,
            dagSnapshot = project.dagGraph,
            parameters = mergedParams,
            teamId = projectTeamId,
        )

        // Set tags (merge default tags from project with request tags)
        val tags = (project.defaultTags + request.tags).map { it.trim().lowercase() }.distinct()
        applyTags(release.id, tags, projectTeamId)

        // Initialize block executions as WAITING (batch)
        repository.batchUpsertBlockExecutions(release.id, project.dagGraph.blocks)

        executionEngine.startExecution(release)

        auditService.log(TeamId(projectTeamId), session, AuditAction.RELEASE_STARTED, AuditTargetType.RELEASE, release.id.value, "Started release for project '${project.name}'")

        return release.copy(tags = tags)
    }

    override suspend fun startScheduledRelease(projectId: ProjectId, parameters: List<Parameter>): Release {
        val project = projectsRepository.findById(projectId)
            ?: throw IllegalArgumentException("Project not found: ${projectId.value}")

        val projectTeamId = projectsRepository.findTeamId(projectId)
            ?: throw NotFoundException("Project not found")

        if (project.dagGraph.blocks.isEmpty()) {
            throw IllegalArgumentException("Project has no blocks to execute")
        }

        val mergedParams = mergeParameters(project.parameters, parameters)

        val release = repository.create(
            projectTemplateId = project.id,
            dagSnapshot = project.dagGraph,
            parameters = mergedParams,
            teamId = projectTeamId,
        )

        // Apply project's default tags to scheduled releases
        val tags = project.defaultTags.map { it.trim().lowercase() }.distinct()
        applyTags(release.id, tags, projectTeamId)

        // Initialize block executions as WAITING (batch)
        repository.batchUpsertBlockExecutions(release.id, project.dagGraph.blocks)

        executionEngine.startExecution(release)

        return release.copy(tags = tags)
    }

    override suspend fun rerunRelease(id: ReleaseId, session: UserSession): Release {
        val original = repository.findById(id)
            ?: throw IllegalArgumentException("Release not found: ${id.value}")

        checkAccess(id, session)

        if (!original.status.isTerminal) {
            throw IllegalArgumentException("Cannot re-run release in status ${original.status}")
        }

        val releaseTeamId = repository.findTeamId(id)
            ?: throw NotFoundException("Release not found")

        val release = repository.create(
            projectTemplateId = original.projectTemplateId,
            dagSnapshot = original.dagSnapshot,
            parameters = original.parameters,
            teamId = releaseTeamId,
        )

        // Copy tags from the original release
        val originalTags = original.tags
        applyTags(release.id, originalTags, releaseTeamId)

        // Initialize block executions as WAITING (batch)
        repository.batchUpsertBlockExecutions(release.id, original.dagSnapshot.blocks)

        executionEngine.startExecution(release)
        return release.copy(tags = originalTags)
    }

    override suspend fun cancelRelease(id: ReleaseId, session: UserSession): Boolean {
        val release = repository.findById(id) ?: return false
        checkAccess(id, session)
        if (release.status != ReleaseStatus.RUNNING && release.status != ReleaseStatus.PENDING) {
            return false
        }
        executionEngine.cancelExecution(id)
        val teamId = repository.findTeamId(id)
            ?: throw NotFoundException("Release not found")
        auditService.log(TeamId(teamId), session, AuditAction.RELEASE_CANCELLED, AuditTargetType.RELEASE, id.value)
        return true
    }

    override suspend fun archiveRelease(id: ReleaseId, session: UserSession): Boolean {
        val release = repository.findById(id) ?: return false
        checkAccess(id, session)
        if (!release.status.isTerminal) return false
        return repository.updateStatus(id, ReleaseStatus.ARCHIVED)
    }

    override suspend fun deleteRelease(id: ReleaseId, session: UserSession): Boolean {
        val release = repository.findById(id) ?: return false
        checkAccess(id, session)
        if (!release.status.isTerminal) return false
        return repository.delete(id)
    }

    override suspend fun awaitRelease(id: ReleaseId) {
        executionEngine.awaitExecution(id)
    }

    override suspend fun restartBlock(releaseId: ReleaseId, blockId: BlockId, session: UserSession): Boolean {
        val release = repository.findById(releaseId) ?: return false
        checkAccess(releaseId, session)
        if (release.status != ReleaseStatus.RUNNING && release.status != ReleaseStatus.FAILED) return false

        val execution = repository.findBlockExecution(releaseId, blockId) ?: return false
        if (execution.status != BlockStatus.FAILED) return false

        return executionEngine.restartBlock(releaseId, blockId)
    }

    /**
     * Approval result from the mutex-protected section.
     * - [Recorded]: approval saved but threshold not yet met — return true to caller without completing the deferred.
     * - [ThresholdMet]: threshold met — proceed to complete the deferred via the engine.
     * - [Rejected]: block not in expected state — return false to caller.
     */
    private enum class ApprovalResult { Recorded, ThresholdMet, Rejected }

    override suspend fun approveBlock(releaseId: ReleaseId, blockId: BlockId, request: ApproveBlockRequest, session: UserSession): Boolean {
        val release = repository.findById(releaseId) ?: return false
        checkAccess(releaseId, session)
        if (release.status != ReleaseStatus.RUNNING) return false

        // Resolve gate from execution state
        val execution = repository.findBlockExecution(releaseId, blockId) ?: return false
        if (execution.status != BlockStatus.WAITING_FOR_INPUT) return false

        val block = release.dagSnapshot.findActionBlock(blockId) ?: return false

        // Serialize concurrent approvals for the same (release, block) to prevent read-modify-write races.
        // Gate and rule are resolved inside the mutex using the freshest execution read.
        val mutexKey = releaseId.value to blockId.value
        val mutex = approvalMutexes.getOrPut(mutexKey) { Mutex() }
        val result = mutex.withLock {
            val currentExecution = repository.findBlockExecution(releaseId, blockId)
                ?: return@withLock ApprovalResult.Rejected
            if (currentExecution.status != BlockStatus.WAITING_FOR_INPUT) {
                return@withLock ApprovalResult.Rejected
            }

            val currentGatePhase = currentExecution.gatePhase
                ?: return@withLock ApprovalResult.Rejected
            val gate = when (currentGatePhase) {
                GatePhase.PRE -> block.preGate
                GatePhase.POST -> block.postGate
            }
            val rule = gate?.approvalRule

            if (rule == null || rule.requiredCount < 1) {
                return@withLock ApprovalResult.ThresholdMet
            }

            if (rule.requiredRole != null && session.role != rule.requiredRole) {
                throw ForbiddenException("Approval requires ${rule.requiredRole} role")
            }
            if (rule.requiredUserIds.isNotEmpty() && session.userId !in rule.requiredUserIds) {
                throw ForbiddenException("You are not authorized to approve this block")
            }
            if (currentExecution.approvals.any { it.userId == session.userId }) {
                throw IllegalArgumentException("You have already approved this block")
            }

            val newApproval = BlockApproval(
                userId = session.userId,
                username = session.username,
                approvedAt = Clock.System.now().toEpochMilliseconds(),
            )
            val updatedApprovals = currentExecution.approvals + newApproval
            val updatedExecution = currentExecution.copy(approvals = updatedApprovals)
            repository.upsertBlockExecution(updatedExecution)

            if (updatedApprovals.size < rule.requiredCount) {
                executionEngine.emitBlockUpdate(releaseId, updatedExecution)
                return@withLock ApprovalResult.Recorded
            }
            ApprovalResult.ThresholdMet
        }

        return when (result) {
            ApprovalResult.Recorded -> true
            ApprovalResult.Rejected -> false
            ApprovalResult.ThresholdMet -> {
                approvalMutexes.remove(mutexKey)
                executionEngine.approveBlock(releaseId, blockId, request.input)
            }
        }
    }

    private suspend fun applyTags(releaseId: ReleaseId, tags: List<String>, teamId: String) {
        if (tags.isNotEmpty()) {
            tagRepository.setTagsForRelease(releaseId, tags, teamId)
        }
    }

    override suspend fun checkAccess(id: ReleaseId, session: UserSession) {
        if (session.role == UserRole.ADMIN) return
        val teamId = repository.findTeamId(id) ?: throw NotFoundException("Resource not found")
        teamAccessService.checkMembership(TeamId(teamId), session)
    }

    private fun mergeParameters(projectParams: List<Parameter>, requestParams: List<Parameter>): List<Parameter> {
        val merged = projectParams.associateBy { it.key }.toMutableMap()
        for (param in requestParams) {
            merged[param.key] = param
        }
        return merged.values.toList()
    }

    /**
     * Validates that all connections referenced in the DAG belong to the expected team.
     * Uses batch fetching to avoid N+1 queries.
     */
    private suspend fun validateConnectionTeamConsistency(dagGraph: DagGraph, expectedTeamId: String) {
        // todo claude: duplicate 8 lines
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
