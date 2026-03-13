package com.github.mr3zee.releases

import com.github.mr3zee.model.*
import com.github.mr3zee.persistence.BlockExecutionTable
import com.github.mr3zee.persistence.ReleaseTable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import java.util.UUID
import kotlin.time.Clock

class ExposedReleasesRepository(private val db: Database) : ReleasesRepository {

    private suspend fun <T> dbQuery(block: suspend () -> T): T =
        withContext(Dispatchers.IO) { suspendTransaction(db) { block() } }

    private fun ResultRow.toRelease(): Release {
        return Release(
            id = ReleaseId(this[ReleaseTable.id].value.toString()),
            projectTemplateId = ProjectId(this[ReleaseTable.projectTemplateId]),
            status = ReleaseStatus.valueOf(this[ReleaseTable.status]),
            dagSnapshot = this[ReleaseTable.dagSnapshot],
            parameters = this[ReleaseTable.parameters],
            startedAt = this[ReleaseTable.startedAt],
            finishedAt = this[ReleaseTable.finishedAt],
        )
    }

    private fun ResultRow.toBlockExecution(): BlockExecution {
        return BlockExecution(
            blockId = BlockId(this[BlockExecutionTable.blockId]),
            releaseId = ReleaseId(this[BlockExecutionTable.releaseId]),
            status = BlockStatus.valueOf(this[BlockExecutionTable.status]),
            outputs = this[BlockExecutionTable.outputs],
            error = this[BlockExecutionTable.error],
            startedAt = this[BlockExecutionTable.startedAt],
            finishedAt = this[BlockExecutionTable.finishedAt],
        )
    }

    override suspend fun findAll(): List<Release> = dbQuery {
        ReleaseTable.selectAll()
            .orderBy(ReleaseTable.id, SortOrder.DESC)
            .map { it.toRelease() }
    }

    override suspend fun findById(id: ReleaseId): Release? = dbQuery {
        ReleaseTable.selectAll()
            .where { ReleaseTable.id eq UUID.fromString(id.value) }
            .singleOrNull()
            ?.toRelease()
    }

    override suspend fun findByStatuses(statuses: Set<ReleaseStatus>): List<Release> = dbQuery {
        ReleaseTable.selectAll()
            .where { ReleaseTable.status inList statuses.map { it.name } }
            .map { it.toRelease() }
    }

    override suspend fun findByProjectId(projectId: ProjectId): List<Release> = dbQuery {
        ReleaseTable.selectAll()
            .where { ReleaseTable.projectTemplateId eq projectId.value }
            .orderBy(ReleaseTable.id, SortOrder.DESC)
            .map { it.toRelease() }
    }

    override suspend fun create(
        projectTemplateId: ProjectId,
        dagSnapshot: DagGraph,
        parameters: List<Parameter>,
    ): Release = dbQuery {
        val id = UUID.randomUUID()
        ReleaseTable.insert {
            it[ReleaseTable.id] = id
            it[ReleaseTable.projectTemplateId] = projectTemplateId.value
            it[ReleaseTable.status] = ReleaseStatus.PENDING.name
            it[ReleaseTable.dagSnapshot] = dagSnapshot
            it[ReleaseTable.parameters] = parameters
            it[ReleaseTable.startedAt] = null
            it[ReleaseTable.finishedAt] = null
        }
        Release(
            id = ReleaseId(id.toString()),
            projectTemplateId = projectTemplateId,
            status = ReleaseStatus.PENDING,
            dagSnapshot = dagSnapshot,
            parameters = parameters,
        )
    }

    override suspend fun updateStatus(id: ReleaseId, status: ReleaseStatus): Boolean = dbQuery {
        val updated = ReleaseTable.update({ ReleaseTable.id eq UUID.fromString(id.value) }) {
            it[ReleaseTable.status] = status.name
        }
        updated > 0
    }

    override suspend fun setStarted(id: ReleaseId): Boolean = dbQuery {
        val now = Clock.System.now()
        val updated = ReleaseTable.update({ ReleaseTable.id eq UUID.fromString(id.value) }) {
            it[ReleaseTable.status] = ReleaseStatus.RUNNING.name
            it[ReleaseTable.startedAt] = now
        }
        updated > 0
    }

    override suspend fun setFinished(id: ReleaseId, status: ReleaseStatus): Boolean = dbQuery {
        val now = Clock.System.now()
        val updated = ReleaseTable.update({ ReleaseTable.id eq UUID.fromString(id.value) }) {
            it[ReleaseTable.status] = status.name
            it[ReleaseTable.finishedAt] = now
        }
        updated > 0
    }

    override suspend fun delete(id: ReleaseId): Boolean = dbQuery {
        // Delete block executions first
        BlockExecutionTable.deleteWhere {
            BlockExecutionTable.releaseId eq id.value
        }
        val deleted = ReleaseTable.deleteWhere {
            ReleaseTable.id eq UUID.fromString(id.value)
        }
        deleted > 0
    }

    override suspend fun findBlockExecutions(releaseId: ReleaseId): List<BlockExecution> = dbQuery {
        BlockExecutionTable.selectAll()
            .where { BlockExecutionTable.releaseId eq releaseId.value }
            .map { it.toBlockExecution() }
    }

    override suspend fun findBlockExecution(releaseId: ReleaseId, blockId: BlockId): BlockExecution? = dbQuery {
        BlockExecutionTable.selectAll()
            .where {
                (BlockExecutionTable.releaseId eq releaseId.value) and
                    (BlockExecutionTable.blockId eq blockId.value)
            }
            .singleOrNull()
            ?.toBlockExecution()
    }

    override suspend fun upsertBlockExecution(execution: BlockExecution) = dbQuery {
        val existing = BlockExecutionTable.selectAll()
            .where {
                (BlockExecutionTable.releaseId eq execution.releaseId.value) and
                    (BlockExecutionTable.blockId eq execution.blockId.value)
            }
            .singleOrNull()

        if (existing != null) {
            BlockExecutionTable.update({
                (BlockExecutionTable.releaseId eq execution.releaseId.value) and
                    (BlockExecutionTable.blockId eq execution.blockId.value)
            }) {
                it[BlockExecutionTable.status] = execution.status.name
                it[BlockExecutionTable.outputs] = execution.outputs
                it[BlockExecutionTable.error] = execution.error
                it[BlockExecutionTable.startedAt] = execution.startedAt
                it[BlockExecutionTable.finishedAt] = execution.finishedAt
            }
        } else {
            BlockExecutionTable.insert {
                it[BlockExecutionTable.id] = UUID.randomUUID()
                it[BlockExecutionTable.releaseId] = execution.releaseId.value
                it[BlockExecutionTable.blockId] = execution.blockId.value
                it[BlockExecutionTable.status] = execution.status.name
                it[BlockExecutionTable.outputs] = execution.outputs
                it[BlockExecutionTable.error] = execution.error
                it[BlockExecutionTable.startedAt] = execution.startedAt
                it[BlockExecutionTable.finishedAt] = execution.finishedAt
            }
        }
        Unit
    }
}
