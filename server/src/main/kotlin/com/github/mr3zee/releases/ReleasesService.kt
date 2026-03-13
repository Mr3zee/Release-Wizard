package com.github.mr3zee.releases

import com.github.mr3zee.ForbiddenException
import com.github.mr3zee.api.ApproveBlockRequest
import com.github.mr3zee.api.CreateReleaseRequest
import com.github.mr3zee.auth.UserSession
import com.github.mr3zee.execution.ExecutionEngine
import com.github.mr3zee.model.*
import com.github.mr3zee.model.isTerminal
import com.github.mr3zee.projects.ProjectsRepository

interface ReleasesService {
    suspend fun listReleases(session: UserSession): List<Release>
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
) : ReleasesService {

    override suspend fun listReleases(session: UserSession): List<Release> {
        val ownerId = if (session.role == UserRole.ADMIN) null else session.userId
        return repository.findAll(ownerId = ownerId)
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

        if (project.dagGraph.blocks.isEmpty()) {
            throw IllegalArgumentException("Project has no blocks to execute")
        }

        val mergedParams = mergeParameters(project.parameters, request.parameters)

        val release = repository.create(
            projectTemplateId = project.id,
            dagSnapshot = project.dagGraph,
            parameters = mergedParams,
            ownerId = session.userId,
        )

        // Initialize block executions as WAITING
        for (block in project.dagGraph.blocks) {
            repository.upsertBlockExecution(
                BlockExecution(
                    blockId = block.id,
                    releaseId = release.id,
                    status = BlockStatus.WAITING,
                )
            )
        }

        executionEngine.startExecution(release)

        return release
    }

    override suspend fun startScheduledRelease(projectId: ProjectId, parameters: List<Parameter>): Release {
        val project = projectsRepository.findById(projectId)
            ?: throw IllegalArgumentException("Project not found: ${projectId.value}")

        if (project.dagGraph.blocks.isEmpty()) {
            throw IllegalArgumentException("Project has no blocks to execute")
        }

        val mergedParams = mergeParameters(project.parameters, parameters)

        val release = repository.create(
            projectTemplateId = project.id,
            dagSnapshot = project.dagGraph,
            parameters = mergedParams,
            ownerId = "",
        )

        for (block in project.dagGraph.blocks) {
            repository.upsertBlockExecution(
                BlockExecution(
                    blockId = block.id,
                    releaseId = release.id,
                    status = BlockStatus.WAITING,
                )
            )
        }

        executionEngine.startExecution(release)

        return release
    }

    override suspend fun rerunRelease(id: ReleaseId, session: UserSession): Release {
        val original = repository.findById(id)
            ?: throw IllegalArgumentException("Release not found: ${id.value}")

        checkAccess(id, session)

        if (!original.status.isTerminal) {
            throw IllegalArgumentException("Cannot re-run release in status ${original.status}")
        }

        val release = repository.create(
            projectTemplateId = original.projectTemplateId,
            dagSnapshot = original.dagSnapshot,
            parameters = original.parameters,
            ownerId = session.userId,
        )

        for (block in original.dagSnapshot.blocks) {
            repository.upsertBlockExecution(
                BlockExecution(
                    blockId = block.id,
                    releaseId = release.id,
                    status = BlockStatus.WAITING,
                )
            )
        }

        executionEngine.startExecution(release)
        return release
    }

    override suspend fun cancelRelease(id: ReleaseId, session: UserSession): Boolean {
        val release = repository.findById(id) ?: return false
        checkAccess(id, session)
        if (release.status != ReleaseStatus.RUNNING && release.status != ReleaseStatus.PENDING) {
            return false
        }
        // cancelExecution cancels the job and joins, so DB is updated before return
        executionEngine.cancelExecution(id)
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

    override suspend fun approveBlock(releaseId: ReleaseId, blockId: BlockId, request: ApproveBlockRequest, session: UserSession): Boolean {
        val release = repository.findById(releaseId) ?: return false
        checkAccess(releaseId, session)
        if (release.status != ReleaseStatus.RUNNING) return false

        val execution = repository.findBlockExecution(releaseId, blockId) ?: return false
        if (execution.status != BlockStatus.WAITING_FOR_INPUT) return false

        return executionEngine.approveBlock(releaseId, blockId, request.input)
    }

    override suspend fun checkAccess(id: ReleaseId, session: UserSession) {
        if (session.role == UserRole.ADMIN) return
        val ownerId = repository.findOwner(id)
        if (ownerId != null && ownerId != session.userId) {
            throw ForbiddenException("Access denied")
        }
    }

    private fun mergeParameters(projectParams: List<Parameter>, requestParams: List<Parameter>): List<Parameter> {
        val merged = projectParams.associateBy { it.key }.toMutableMap()
        for (param in requestParams) {
            merged[param.key] = param
        }
        return merged.values.toList()
    }

}
