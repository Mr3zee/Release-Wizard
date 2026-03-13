package com.github.mr3zee.releases

import com.github.mr3zee.api.ApproveBlockRequest
import com.github.mr3zee.api.CreateReleaseRequest
import com.github.mr3zee.execution.ExecutionEngine
import com.github.mr3zee.model.*
import com.github.mr3zee.projects.ProjectsRepository

interface ReleasesService {
    suspend fun listReleases(): List<Release>
    suspend fun getRelease(id: ReleaseId): Release?
    suspend fun getBlockExecutions(releaseId: ReleaseId): List<BlockExecution>
    suspend fun startRelease(request: CreateReleaseRequest): Release
    suspend fun cancelRelease(id: ReleaseId): Boolean
    suspend fun awaitRelease(id: ReleaseId)
    suspend fun restartBlock(releaseId: ReleaseId, blockId: BlockId): Boolean
    suspend fun approveBlock(releaseId: ReleaseId, blockId: BlockId, request: ApproveBlockRequest): Boolean
}

class DefaultReleasesService(
    private val repository: ReleasesRepository,
    private val projectsRepository: ProjectsRepository,
    private val executionEngine: ExecutionEngine,
) : ReleasesService {

    override suspend fun listReleases(): List<Release> {
        return repository.findAll()
    }

    override suspend fun getRelease(id: ReleaseId): Release? {
        return repository.findById(id)
    }

    override suspend fun getBlockExecutions(releaseId: ReleaseId): List<BlockExecution> {
        return repository.findBlockExecutions(releaseId)
    }

    override suspend fun startRelease(request: CreateReleaseRequest): Release {
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

    override suspend fun cancelRelease(id: ReleaseId): Boolean {
        val release = repository.findById(id) ?: return false
        if (release.status != ReleaseStatus.RUNNING && release.status != ReleaseStatus.PENDING) {
            return false
        }
        // cancelExecution cancels the job and joins, so DB is updated before return
        executionEngine.cancelExecution(id)
        return true
    }

    override suspend fun awaitRelease(id: ReleaseId) {
        executionEngine.awaitExecution(id)
    }

    override suspend fun restartBlock(releaseId: ReleaseId, blockId: BlockId): Boolean {
        val release = repository.findById(releaseId) ?: return false
        if (release.status != ReleaseStatus.RUNNING && release.status != ReleaseStatus.FAILED) return false

        val execution = repository.findBlockExecution(releaseId, blockId) ?: return false
        if (execution.status != BlockStatus.FAILED) return false

        return executionEngine.restartBlock(releaseId, blockId)
    }

    override suspend fun approveBlock(releaseId: ReleaseId, blockId: BlockId, request: ApproveBlockRequest): Boolean {
        val release = repository.findById(releaseId) ?: return false
        if (release.status != ReleaseStatus.RUNNING) return false

        val execution = repository.findBlockExecution(releaseId, blockId) ?: return false
        if (execution.status != BlockStatus.WAITING_FOR_INPUT) return false

        return executionEngine.approveBlock(releaseId, blockId, request.input)
    }

    private fun mergeParameters(projectParams: List<Parameter>, requestParams: List<Parameter>): List<Parameter> {
        val merged = projectParams.associateBy { it.key }.toMutableMap()
        for (param in requestParams) {
            merged[param.key] = param
        }
        return merged.values.toList()
    }
}
