package com.github.mr3zee.schedules

import com.github.mr3zee.model.Parameter
import com.github.mr3zee.model.ProjectId
import com.github.mr3zee.persistence.ScheduleTable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import java.util.UUID
import kotlin.time.Instant

class ExposedScheduleRepository(private val db: Database) : ScheduleRepository {

    private suspend fun <T> dbQuery(block: suspend () -> T): T =
        withContext(Dispatchers.IO) { suspendTransaction(db) { block() } }

    private fun ResultRow.toEntity(): ScheduleEntity {
        return ScheduleEntity(
            id = this[ScheduleTable.id].value.toString(),
            projectId = ProjectId(this[ScheduleTable.projectId].value.toString()),
            cronExpression = this[ScheduleTable.cronExpression],
            parameters = this[ScheduleTable.parameters],
            enabled = this[ScheduleTable.enabled],
            createdBy = this[ScheduleTable.createdBy]?.value?.toString(),
            nextRunAt = this[ScheduleTable.nextRunAt],
            lastRunAt = this[ScheduleTable.lastRunAt],
        )
    }

    override suspend fun findByProjectId(projectId: ProjectId): List<ScheduleEntity> = dbQuery {
        ScheduleTable.selectAll()
            .where { ScheduleTable.projectId eq UUID.fromString(projectId.value) }
            .map { it.toEntity() }
    }

    override suspend fun findById(id: String): ScheduleEntity? = dbQuery {
        ScheduleTable.selectAll()
            .where { ScheduleTable.id eq UUID.fromString(id) }
            .singleOrNull()
            ?.toEntity()
    }

    override suspend fun findDueSchedules(now: Instant): List<ScheduleEntity> = dbQuery {
        ScheduleTable.selectAll()
            .where {
                (ScheduleTable.enabled eq true) and
                    (ScheduleTable.nextRunAt lessEq now)
            }
            // SCHED-C2: FOR UPDATE provides single-instance serialization of poll reads.
            // Note: lock is released when this transaction ends (before claimSchedule runs in its own txn).
            // Cross-instance idempotency is guaranteed by the CAS in claimSchedule, not by this lock.
            .forUpdate()
            .map { it.toEntity() }
    }

    override suspend fun create(
        projectId: ProjectId,
        cronExpression: String,
        parameters: List<Parameter>,
        enabled: Boolean,
        createdBy: String,
        nextRunAt: Instant?,
    ): ScheduleEntity = dbQuery {
        val id = UUID.randomUUID()
        ScheduleTable.insert {
            it[ScheduleTable.id] = id
            it[ScheduleTable.projectId] = UUID.fromString(projectId.value)
            it[ScheduleTable.cronExpression] = cronExpression
            it[ScheduleTable.parameters] = parameters
            it[ScheduleTable.enabled] = enabled
            it[ScheduleTable.createdBy] = UUID.fromString(createdBy)
            it[ScheduleTable.nextRunAt] = nextRunAt
            it[ScheduleTable.lastRunAt] = null
        }
        ScheduleEntity(
            id = id.toString(),
            projectId = projectId,
            cronExpression = cronExpression,
            parameters = parameters,
            enabled = enabled,
            createdBy = createdBy,
            nextRunAt = nextRunAt,
            lastRunAt = null,
        )
    }

    override suspend fun update(
        id: String,
        enabled: Boolean?,
        nextRunAt: Instant?,
        lastRunAt: Instant?,
    ): ScheduleEntity? = dbQuery {
        val uuid = UUID.fromString(id)
        val updated = ScheduleTable.update({ ScheduleTable.id eq uuid }) { stmt ->
            enabled?.let { stmt[ScheduleTable.enabled] = it }
            nextRunAt?.let { stmt[ScheduleTable.nextRunAt] = it }
            lastRunAt?.let { stmt[ScheduleTable.lastRunAt] = it }
        }
        if (updated > 0) {
            ScheduleTable.selectAll()
                .where { ScheduleTable.id eq uuid }
                .single()
                .toEntity()
        } else {
            null
        }
    }

    override suspend fun claimSchedule(id: String, now: Instant, nextRun: Instant?): Boolean = dbQuery {
        val uuid = UUID.fromString(id)
        // SCHED-C1: CAS — only claim if lastRunAt hasn't already covered this scheduled run
        val updated = ScheduleTable.update({
            (ScheduleTable.id eq uuid) and
                ((ScheduleTable.lastRunAt.isNull()) or (ScheduleTable.lastRunAt less ScheduleTable.nextRunAt))
        }) { stmt ->
            stmt[ScheduleTable.lastRunAt] = now
            nextRun?.let { stmt[ScheduleTable.nextRunAt] = it }
        }
        updated > 0
    }

    override suspend fun delete(id: String): Boolean = dbQuery {
        val deleted = ScheduleTable.deleteWhere {
            ScheduleTable.id eq UUID.fromString(id)
        }
        deleted > 0
    }
}
