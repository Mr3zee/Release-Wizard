package com.github.mr3zee.releases

import com.github.mr3zee.model.*
import com.github.mr3zee.persistence.BlockExecutionTable
import com.github.mr3zee.persistence.ProjectTemplateTable
import com.github.mr3zee.persistence.ReleaseTable
import com.github.mr3zee.persistence.ReleaseTagTable
import com.github.mr3zee.persistence.StatusWebhookTokenTable
import com.github.mr3zee.persistence.likeContains
import com.github.mr3zee.persistence.safeOffset
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
            projectTemplateId = ProjectId(this[ReleaseTable.projectTemplateId].value.toString()),
            status = this[ReleaseTable.status],
            dagSnapshot = this[ReleaseTable.dagSnapshot],
            parameters = this[ReleaseTable.parameters],
            startedAt = this[ReleaseTable.startedAt],
            finishedAt = this[ReleaseTable.finishedAt],
        )
    }

    private fun ResultRow.toBlockExecution(): BlockExecution {
        val wsStatus = this[BlockExecutionTable.webhookStatus]
        val wsAt = this[BlockExecutionTable.webhookStatusAt]
        return BlockExecution(
            blockId = BlockId(this[BlockExecutionTable.blockId]),
            releaseId = ReleaseId(this[BlockExecutionTable.releaseId].value.toString()),
            status = this[BlockExecutionTable.status],
            outputs = this[BlockExecutionTable.outputs],
            error = this[BlockExecutionTable.error],
            startedAt = this[BlockExecutionTable.startedAt],
            finishedAt = this[BlockExecutionTable.finishedAt],
            approvals = this[BlockExecutionTable.approvals],
            gatePhase = this[BlockExecutionTable.gatePhase],
            gateMessage = this[BlockExecutionTable.gateMessage],
            webhookStatus = if (wsStatus != null && wsAt != null) {
                WebhookStatusUpdate(
                    status = wsStatus,
                    description = this[BlockExecutionTable.webhookStatusDescription],
                    receivedAt = wsAt,
                )
            } else null,
        )
    }

    private fun loadTagsForRelease(releaseId: String): List<String> {
        return ReleaseTagTable.select(ReleaseTagTable.tag)
            .where { ReleaseTagTable.releaseId eq UUID.fromString(releaseId) }
            .orderBy(ReleaseTagTable.tag, SortOrder.ASC)
            .map { it[ReleaseTagTable.tag] }
    }

    private fun loadTagsForReleases(releaseIds: List<String>): Map<String, List<String>> {
        if (releaseIds.isEmpty()) return emptyMap()
        val uuids = releaseIds.mapNotNull { runCatching { UUID.fromString(it) }.getOrNull() }
        return ReleaseTagTable.selectAll()
            .where { ReleaseTagTable.releaseId inList uuids }
            .groupBy({ it[ReleaseTagTable.releaseId].value.toString() }, { it[ReleaseTagTable.tag] })
    }

    private fun buildReleaseConditions(
        includeArchived: Boolean,
        teamId: String?,
        teamIds: List<String>?,
        search: String?,
        status: ReleaseStatus?,
        projectTemplateId: ProjectId?,
        releaseIds: Set<String>? = null,
    ): List<Op<Boolean>> {
        val conditions = mutableListOf<Op<Boolean>>()
        if (!includeArchived) {
            conditions.add(ReleaseTable.status neq ReleaseStatus.ARCHIVED)
        }
        if (teamId != null) {
            conditions.add(ReleaseTable.teamId eq UUID.fromString(teamId))
        } else if (teamIds != null) {
            val uuids = teamIds.mapNotNull { runCatching { UUID.fromString(it) }.getOrNull() }
            conditions.add(ReleaseTable.teamId inList uuids)
        }
        if (status != null) {
            conditions.add(ReleaseTable.status eq status)
        }
        if (projectTemplateId != null) {
            conditions.add(ReleaseTable.projectTemplateId eq UUID.fromString(projectTemplateId.value))
        }
        if (!search.isNullOrBlank()) {
            val pattern = likeContains(search)
            val projectNameMatch = exists(
                ProjectTemplateTable.selectAll().where {
                    (ProjectTemplateTable.id eq ReleaseTable.projectTemplateId) and
                        (ProjectTemplateTable.name.lowerCase() like pattern)
                }
            )
            val tagMatch = exists(
                ReleaseTagTable.selectAll().where {
                    (ReleaseTagTable.releaseId eq ReleaseTable.id) and
                        (ReleaseTagTable.tag.lowerCase() like pattern)
                }
            )
            conditions.add(projectNameMatch or tagMatch)
        }
        if (releaseIds != null) {
            val uuids = releaseIds.mapNotNull { runCatching { UUID.fromString(it) }.getOrNull() }
            conditions.add(ReleaseTable.id inList uuids)
        }
        return conditions
    }

    private fun queryReleasesWithTags(
        conditions: List<Op<Boolean>>,
        offset: Int,
        limit: Int,
    ): List<Release> {
        val query = ReleaseTable.selectAll()
        if (conditions.isNotEmpty()) {
            query.where { conditions.reduce { acc, op -> acc and op } }
        }
        val releases = query.orderBy(ReleaseTable.startedAt to SortOrder.DESC)
            .limit(limit)
            .offset(safeOffset(offset))
            .map { it.toRelease() }
        val tagsMap = loadTagsForReleases(releases.map { it.id.value })
        return releases.map { release -> release.copy(tags = tagsMap[release.id.value] ?: emptyList()) }
    }

    override suspend fun findAll(
        includeArchived: Boolean,
        teamId: String?,
        teamIds: List<String>?,
        offset: Int,
        limit: Int,
        search: String?,
        status: ReleaseStatus?,
        projectTemplateId: ProjectId?,
        releaseIds: Set<String>?,
    ): List<Release> = dbQuery {
        val conditions = buildReleaseConditions(includeArchived, teamId, teamIds, search, status, projectTemplateId, releaseIds)
        queryReleasesWithTags(conditions, offset, limit)
    }

    override suspend fun countAll(
        includeArchived: Boolean,
        teamId: String?,
        teamIds: List<String>?,
        search: String?,
        status: ReleaseStatus?,
        projectTemplateId: ProjectId?,
        releaseIds: Set<String>?,
    ): Long = dbQuery {
        val conditions = buildReleaseConditions(includeArchived, teamId, teamIds, search, status, projectTemplateId, releaseIds)
        countWithConditions(conditions)
    }

    override suspend fun findAllWithCount(
        includeArchived: Boolean,
        teamId: String?,
        teamIds: List<String>?,
        offset: Int,
        limit: Int,
        search: String?,
        status: ReleaseStatus?,
        projectTemplateId: ProjectId?,
        releaseIds: Set<String>?,
    ): Pair<List<Release>, Long> = dbQuery {
        val conditions = buildReleaseConditions(includeArchived, teamId, teamIds, search, status, projectTemplateId, releaseIds)
        val totalCount = countWithConditions(conditions)
        queryReleasesWithTags(conditions, offset, limit) to totalCount
    }

    private fun countWithConditions(conditions: List<Op<Boolean>>): Long {
        val query = ReleaseTable.selectAll()
        if (conditions.isNotEmpty()) {
            query.where { conditions.reduce { acc, op -> acc and op } }
        }
        return query.count()
    }

    override suspend fun findTeamId(id: ReleaseId): String? = dbQuery {
        ReleaseTable.select(ReleaseTable.teamId)
            .where { ReleaseTable.id eq UUID.fromString(id.value) }
            .singleOrNull()
            ?.get(ReleaseTable.teamId)?.value?.toString()
    }

    override suspend fun findById(id: ReleaseId): Release? = dbQuery {
        ReleaseTable.selectAll()
            .where { ReleaseTable.id eq UUID.fromString(id.value) }
            .singleOrNull()
            ?.toRelease()
            ?.let { release -> release.copy(tags = loadTagsForRelease(release.id.value)) }
    }

    override suspend fun findByStatuses(statuses: Set<ReleaseStatus>): List<Release> = dbQuery {
        val releases = ReleaseTable.selectAll()
            .where { ReleaseTable.status inList statuses }
            .limit(1000)
            .map { it.toRelease() }
        val tagMap = loadTagsForReleases(releases.map { it.id.value })
        releases.map { it.copy(tags = tagMap[it.id.value] ?: emptyList()) }
    }

    override suspend fun findByProjectId(projectId: ProjectId): List<Release> = dbQuery {
        val releases = ReleaseTable.selectAll()
            .where { ReleaseTable.projectTemplateId eq UUID.fromString(projectId.value) }
            .orderBy(ReleaseTable.startedAt to SortOrder.DESC)
            .limit(1000)
            .map { it.toRelease() }
        val tagMap = loadTagsForReleases(releases.map { it.id.value })
        releases.map { it.copy(tags = tagMap[it.id.value] ?: emptyList()) }
    }

    override suspend fun create(
        projectTemplateId: ProjectId,
        dagSnapshot: DagGraph,
        parameters: List<Parameter>,
        teamId: String,
    ): Release = dbQuery {
        val id = UUID.randomUUID()
        val now = Clock.System.now()
        ReleaseTable.insert {
            it[ReleaseTable.id] = id
            it[ReleaseTable.projectTemplateId] = UUID.fromString(projectTemplateId.value)
            it[ReleaseTable.status] = ReleaseStatus.PENDING
            it[ReleaseTable.dagSnapshot] = dagSnapshot
            it[ReleaseTable.parameters] = parameters
            it[ReleaseTable.teamId] = UUID.fromString(teamId)
            it[ReleaseTable.createdAt] = now
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
            it[ReleaseTable.status] = status
        }
        updated > 0
    }

    override suspend fun setStarted(id: ReleaseId): Boolean = dbQuery {
        val now = Clock.System.now()
        val updated = ReleaseTable.update({ ReleaseTable.id eq UUID.fromString(id.value) }) {
            it[ReleaseTable.status] = ReleaseStatus.RUNNING
            it[ReleaseTable.startedAt] = now
        }
        updated > 0
    }

    override suspend fun setFinished(id: ReleaseId, status: ReleaseStatus): Boolean = dbQuery {
        val now = Clock.System.now()
        val updated = ReleaseTable.update({ ReleaseTable.id eq UUID.fromString(id.value) }) {
            it[ReleaseTable.status] = status
            it[ReleaseTable.finishedAt] = now
        }
        updated > 0
    }

    override suspend fun delete(id: ReleaseId): Boolean = dbQuery {
        val releaseUuid = UUID.fromString(id.value)
        StatusWebhookTokenTable.deleteWhere {
            StatusWebhookTokenTable.releaseId eq releaseUuid
        }
        BlockExecutionTable.deleteWhere {
            BlockExecutionTable.releaseId eq releaseUuid
        }
        ReleaseTagTable.deleteWhere {
            ReleaseTagTable.releaseId eq releaseUuid
        }
        val deleted = ReleaseTable.deleteWhere {
            ReleaseTable.id eq releaseUuid
        }
        deleted > 0
    }

    override suspend fun findBlockExecutions(releaseId: ReleaseId): List<BlockExecution> = dbQuery {
        BlockExecutionTable.selectAll()
            .where { BlockExecutionTable.releaseId eq UUID.fromString(releaseId.value) }
            .map { it.toBlockExecution() }
    }

    override suspend fun findBlockExecution(releaseId: ReleaseId, blockId: BlockId): BlockExecution? = dbQuery {
        BlockExecutionTable.selectAll()
            .where {
                (BlockExecutionTable.releaseId eq UUID.fromString(releaseId.value)) and
                    (BlockExecutionTable.blockId eq blockId.value)
            }
            .singleOrNull()
            ?.toBlockExecution()
    }

    override suspend fun upsertBlockExecution(execution: BlockExecution) = dbQuery {
        BlockExecutionTable.upsert(
            keys = arrayOf(BlockExecutionTable.releaseId, BlockExecutionTable.blockId),
            onUpdate = {
                it[BlockExecutionTable.status] = execution.status
                it[BlockExecutionTable.outputs] = execution.outputs
                it[BlockExecutionTable.error] = execution.error
                it[BlockExecutionTable.startedAt] = execution.startedAt
                it[BlockExecutionTable.finishedAt] = execution.finishedAt
                it[BlockExecutionTable.approvals] = execution.approvals
                it[BlockExecutionTable.gatePhase] = execution.gatePhase
                it[BlockExecutionTable.gateMessage] = execution.gateMessage
            },
        ) {
            it[BlockExecutionTable.id] = UUID.randomUUID()
            it[BlockExecutionTable.releaseId] = UUID.fromString(execution.releaseId.value)
            it[BlockExecutionTable.blockId] = execution.blockId.value
            it[BlockExecutionTable.status] = execution.status
            it[BlockExecutionTable.outputs] = execution.outputs
            it[BlockExecutionTable.error] = execution.error
            it[BlockExecutionTable.startedAt] = execution.startedAt
            it[BlockExecutionTable.finishedAt] = execution.finishedAt
            it[BlockExecutionTable.approvals] = execution.approvals
            it[BlockExecutionTable.gatePhase] = execution.gatePhase
            it[BlockExecutionTable.gateMessage] = execution.gateMessage
        }
        Unit
    }

    override suspend fun batchUpsertBlockExecutions(releaseId: ReleaseId, blocks: List<Block>) = dbQuery {
        val releaseUuid = UUID.fromString(releaseId.value)
        BlockExecutionTable.batchUpsert(
            data = blocks,
            keys = arrayOf(BlockExecutionTable.releaseId, BlockExecutionTable.blockId),
            onUpdate = {
                it[BlockExecutionTable.status] = BlockStatus.WAITING
                it[BlockExecutionTable.outputs] = emptyMap()
                it[BlockExecutionTable.error] = null
                it[BlockExecutionTable.startedAt] = null
                it[BlockExecutionTable.finishedAt] = null
                it[BlockExecutionTable.approvals] = emptyList()
                it[BlockExecutionTable.gatePhase] = null
                it[BlockExecutionTable.gateMessage] = null
                it[BlockExecutionTable.webhookStatus] = null
                it[BlockExecutionTable.webhookStatusDescription] = null
                it[BlockExecutionTable.webhookStatusAt] = null
            },
        ) { block ->
            this[BlockExecutionTable.id] = UUID.randomUUID()
            this[BlockExecutionTable.releaseId] = releaseUuid
            this[BlockExecutionTable.blockId] = block.id.value
            this[BlockExecutionTable.status] = BlockStatus.WAITING
            this[BlockExecutionTable.outputs] = emptyMap()
            this[BlockExecutionTable.error] = null
            this[BlockExecutionTable.startedAt] = null
            this[BlockExecutionTable.finishedAt] = null
            this[BlockExecutionTable.approvals] = emptyList()
            this[BlockExecutionTable.gatePhase] = null
            this[BlockExecutionTable.gateMessage] = null
            this[BlockExecutionTable.webhookStatus] = null
            this[BlockExecutionTable.webhookStatusDescription] = null
            this[BlockExecutionTable.webhookStatusAt] = null
        }
        Unit
    }

    override suspend fun updateWebhookStatus(
        releaseId: ReleaseId,
        blockId: BlockId,
        status: String,
        description: String?,
        receivedAt: kotlin.time.Instant,
    ): BlockExecution? = dbQuery {
        val releaseUuid = UUID.fromString(releaseId.value)
        val updated = BlockExecutionTable.update({
            (BlockExecutionTable.releaseId eq releaseUuid) and
                (BlockExecutionTable.blockId eq blockId.value) and
                (BlockExecutionTable.status eq BlockStatus.RUNNING)
        }) {
            it[BlockExecutionTable.webhookStatus] = status
            it[BlockExecutionTable.webhookStatusDescription] = description
            it[BlockExecutionTable.webhookStatusAt] = receivedAt
        }
        if (updated > 0) {
            BlockExecutionTable.selectAll()
                .where {
                    (BlockExecutionTable.releaseId eq releaseUuid) and
                        (BlockExecutionTable.blockId eq blockId.value)
                }
                .singleOrNull()
                ?.toBlockExecution()
        } else null
    }
}
