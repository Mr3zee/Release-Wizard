package com.github.mr3zee.releases

import com.github.mr3zee.model.*

interface ReleasesRepository {
    suspend fun findAll(): List<Release>
    suspend fun findById(id: ReleaseId): Release?
    suspend fun findByProjectId(projectId: ProjectId): List<Release>
    suspend fun create(
        projectTemplateId: ProjectId,
        dagSnapshot: DagGraph,
        parameters: List<Parameter>,
    ): Release
    suspend fun updateStatus(id: ReleaseId, status: ReleaseStatus): Boolean
    suspend fun setStarted(id: ReleaseId): Boolean
    suspend fun setFinished(id: ReleaseId, status: ReleaseStatus): Boolean
    suspend fun delete(id: ReleaseId): Boolean

    // Block execution tracking
    suspend fun findBlockExecutions(releaseId: ReleaseId): List<BlockExecution>
    suspend fun findBlockExecution(releaseId: ReleaseId, blockId: BlockId): BlockExecution?
    suspend fun upsertBlockExecution(execution: BlockExecution)
}
