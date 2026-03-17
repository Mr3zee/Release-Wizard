package com.github.mr3zee.releases

import com.github.mr3zee.model.*
import kotlin.time.Instant

interface ReleasesRepository {
    suspend fun batchUpsertBlockExecutions(releaseId: ReleaseId, blocks: List<Block>) {
        for (block in blocks) {
            upsertBlockExecution(
                BlockExecution(
                    blockId = block.id,
                    releaseId = releaseId,
                    status = BlockStatus.WAITING,
                )
            )
        }
    }
    suspend fun findAll(
        includeArchived: Boolean = false,
        teamId: String? = null,
        teamIds: List<String>? = null,
        offset: Int = 0,
        limit: Int = 20,
        search: String? = null,
        status: ReleaseStatus? = null,
        projectTemplateId: ProjectId? = null,
        releaseIds: Set<String>? = null,
    ): List<Release>
    suspend fun countAll(
        includeArchived: Boolean = false,
        teamId: String? = null,
        teamIds: List<String>? = null,
        search: String? = null,
        status: ReleaseStatus? = null,
        projectTemplateId: ProjectId? = null,
        releaseIds: Set<String>? = null,
    ): Long
    suspend fun findAllWithCount(
        includeArchived: Boolean = false,
        teamId: String? = null,
        teamIds: List<String>? = null,
        offset: Int = 0,
        limit: Int = 20,
        search: String? = null,
        status: ReleaseStatus? = null,
        projectTemplateId: ProjectId? = null,
        releaseIds: Set<String>? = null,
    ): Pair<List<Release>, Long>
    suspend fun findById(id: ReleaseId): Release?
    suspend fun findTeamId(id: ReleaseId): String?
    suspend fun findByProjectId(projectId: ProjectId): List<Release>
    suspend fun findByStatuses(statuses: Set<ReleaseStatus>): List<Release>
    suspend fun create(
        projectTemplateId: ProjectId,
        dagSnapshot: DagGraph,
        parameters: List<Parameter>,
        teamId: String,
    ): Release
    suspend fun updateStatus(id: ReleaseId, status: ReleaseStatus): Boolean
    suspend fun setStarted(id: ReleaseId): Boolean
    suspend fun setFinished(id: ReleaseId, status: ReleaseStatus): Boolean
    suspend fun delete(id: ReleaseId): Boolean

    // Block execution tracking
    suspend fun findBlockExecutions(releaseId: ReleaseId): List<BlockExecution>
    suspend fun findBlockExecution(releaseId: ReleaseId, blockId: BlockId): BlockExecution?
    suspend fun upsertBlockExecution(execution: BlockExecution)

    /**
     * Update the sub-builds list for a specific block execution.
     * Targeted column UPDATE to avoid overwriting other fields.
     */
    suspend fun updateSubBuilds(
        releaseId: ReleaseId,
        blockId: BlockId,
        subBuilds: List<SubBuild>,
    ): Boolean {
        // Default no-op for in-memory implementations
        return false
    }

    /**
     * Atomically updates only the webhook status columns on a block execution.
     * Only succeeds if the block is currently RUNNING (prevents overwriting terminal states).
     * Returns the updated [BlockExecution] or null if the block was not RUNNING.
     */
    suspend fun updateWebhookStatus(
        releaseId: ReleaseId,
        blockId: BlockId,
        status: String,
        description: String?,
        receivedAt: Instant,
    ): BlockExecution?
}
