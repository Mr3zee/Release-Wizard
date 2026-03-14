package com.github.mr3zee.releases

import com.github.mr3zee.ForbiddenException
import com.github.mr3zee.api.ApproveBlockRequest
import com.github.mr3zee.api.CreateReleaseRequest
import com.github.mr3zee.auth.UserSession
import com.github.mr3zee.execution.ExecutionEngine
import com.github.mr3zee.model.*
import com.github.mr3zee.model.isTerminal
import com.github.mr3zee.projects.ProjectsRepository
import com.github.mr3zee.tags.TagRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Clock

interface ReleasesService {
    suspend fun listReleases(
        session: UserSession,
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
) : ReleasesService {

    private val approvalMutexes = ConcurrentHashMap<String, Mutex>()

    override suspend fun listReleases(
        session: UserSession,
        offset: Int,
        limit: Int,
        search: String?,
        status: ReleaseStatus?,
        projectTemplateId: ProjectId?,
        tag: String?,
    ): Pair<List<Release>, Long> {
        val ownerId = if (session.role == UserRole.ADMIN) null else session.userId
        // If tag is provided, resolve matching release IDs first
        val releaseIds = if (tag != null) {
            val ids = tagRepository.findReleaseIdsByTag(tag).toSet()
            if (ids.isEmpty()) return emptyList<Release>() to 0L
            ids
        } else null
        return repository.findAllWithCount(
            ownerId = ownerId, offset = offset, limit = limit,
            search = search, status = status, projectTemplateId = projectTemplateId,
            releaseIds = releaseIds,
        )
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

        // Set tags (merge default tags from project with request tags)
        val tags = (project.defaultTags + request.tags).map { it.trim().lowercase() }.distinct()
        // todo claude: duplicate 3 lines
        if (tags.isNotEmpty()) {
            tagRepository.setTagsForRelease(release.id, tags)
        }

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

        return release.copy(tags = tags)
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

        // Apply project's default tags to scheduled releases
        val tags = project.defaultTags.map { it.trim().lowercase() }.distinct()
        // todo claude: duplicate 3 lines
        if (tags.isNotEmpty()) {
            tagRepository.setTagsForRelease(release.id, tags)
        }

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

        return release.copy(tags = tags)
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

        // Copy tags from the original release
        val originalTags = original.tags
        // todo claude: duplicate 3 lines
        if (originalTags.isNotEmpty()) {
            tagRepository.setTagsForRelease(release.id, originalTags)
        }

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
        return release.copy(tags = originalTags)
    }

    override suspend fun cancelRelease(id: ReleaseId, session: UserSession): Boolean {
        val release = repository.findById(id) ?: return false
        checkAccess(id, session)
        if (release.status != ReleaseStatus.RUNNING && release.status != ReleaseStatus.PENDING) {
            return false
        }
        // cancelExecution cancels the job and joins, so DB is updated before return
        executionEngine.cancelExecution(id)
        // Clean up approval mutexes for this release
        approvalMutexes.keys.removeIf { it.startsWith("${id.value}:") }
        return true
    }

    override suspend fun archiveRelease(id: ReleaseId, session: UserSession): Boolean {
        val release = repository.findById(id) ?: return false
        checkAccess(id, session)
        if (!release.status.isTerminal) return false
        val archived = repository.updateStatus(id, ReleaseStatus.ARCHIVED)
        if (archived) {
            // Clean up approval mutexes for this release
            approvalMutexes.keys.removeIf { it.startsWith("${id.value}:") }
        }
        return archived
    }

    override suspend fun deleteRelease(id: ReleaseId, session: UserSession): Boolean {
        val release = repository.findById(id) ?: return false
        checkAccess(id, session)
        if (!release.status.isTerminal) return false
        val deleted = repository.delete(id)
        if (deleted) {
            // Clean up approval mutexes for this release
            approvalMutexes.keys.removeIf { it.startsWith("${id.value}:") }
        }
        return deleted
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

        // Find the block's approval rule (search nested containers too)
        val block = findActionBlock(release.dagSnapshot, blockId)
        val rule = block?.approvalRule

        if (rule != null) {
            // Invalid rule — treat as no rule, approve immediately
            if (rule.requiredCount < 1) {
                return executionEngine.approveBlock(releaseId, blockId, request.input)
            }

            // Acquire mutex AFTER validations to prevent DoS via fabricated IDs
            val mutexKey = "${releaseId.value}:${blockId.value}"
            val mutex = approvalMutexes.getOrPut(mutexKey) { Mutex() }

            mutex.withLock {
                // Re-read execution within the lock to get latest state
                val execution = repository.findBlockExecution(releaseId, blockId) ?: return false
                if (execution.status != BlockStatus.WAITING_FOR_INPUT) return false

                // Check role requirement
                if (rule.requiredRole != null && session.role != rule.requiredRole) {
                    throw ForbiddenException("Approval requires ${rule.requiredRole} role")
                }
                // Check user requirement
                if (rule.requiredUserIds.isNotEmpty() && session.userId !in rule.requiredUserIds) {
                    throw ForbiddenException("You are not authorized to approve this block")
                }
                // Check for duplicate approval
                if (execution.approvals.any { it.userId == session.userId }) {
                    throw IllegalArgumentException("You have already approved this block")
                }

                // Add approval
                val newApproval = BlockApproval(
                    userId = session.userId,
                    username = session.username,
                    approvedAt = Clock.System.now().toEpochMilliseconds(),
                )
                val updatedApprovals = execution.approvals + newApproval
                val updatedExecution = execution.copy(approvals = updatedApprovals)
                repository.upsertBlockExecution(updatedExecution)

                // Check if we have enough approvals
                if (updatedApprovals.size < rule.requiredCount) {
                    // Partial approval -- emit update event so subscribers see the new approval
                    executionEngine.emitBlockUpdate(releaseId, updatedExecution)
                    return true
                }
            }

            // Approval threshold met — clean up mutex since this block won't need approval again
            approvalMutexes.remove(mutexKey)
        } else {
            val execution = repository.findBlockExecution(releaseId, blockId) ?: return false
            if (execution.status != BlockStatus.WAITING_FOR_INPUT) return false
        }

        return executionEngine.approveBlock(releaseId, blockId, request.input)
    }

    private fun findActionBlock(graph: DagGraph, blockId: BlockId): Block.ActionBlock? {
        // todo claude: duplicate 9 lines
        for (block in graph.blocks) {
            when (block) {
                is Block.ActionBlock -> if (block.id == blockId) return block
                is Block.ContainerBlock -> {
                    findActionBlock(block.children, blockId)?.let { return it }
                }
            }
        }
        return null
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
