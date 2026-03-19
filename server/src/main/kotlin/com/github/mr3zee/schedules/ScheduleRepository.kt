package com.github.mr3zee.schedules

import com.github.mr3zee.model.Parameter
import com.github.mr3zee.model.ProjectId
import kotlin.time.Instant

data class ScheduleEntity(
    val id: String,
    val projectId: ProjectId,
    val cronExpression: String,
    val parameters: List<Parameter>,
    val enabled: Boolean,
    val createdBy: String?,
    val nextRunAt: Instant?,
    val lastRunAt: Instant?,
)

interface ScheduleRepository {
    suspend fun findByProjectId(projectId: ProjectId): List<ScheduleEntity>
    suspend fun findById(id: String): ScheduleEntity?
    suspend fun findDueSchedules(now: Instant): List<ScheduleEntity>
    suspend fun create(
        projectId: ProjectId,
        cronExpression: String,
        parameters: List<Parameter>,
        enabled: Boolean,
        createdBy: String,
        nextRunAt: Instant?,
    ): ScheduleEntity
    suspend fun update(id: String, enabled: Boolean?, nextRunAt: Instant?, lastRunAt: Instant?): ScheduleEntity?
    /**
     * SCHED-C1: Atomically claim a due schedule by setting lastRunAt and advancing nextRunAt.
     * Uses CAS condition (lastRunAt IS NULL OR lastRunAt < nextRunAt) to prevent double-fires.
     * Returns true if the schedule was claimed, false if another instance already claimed it.
     */
    suspend fun claimSchedule(id: String, now: Instant, nextRun: Instant?): Boolean
    suspend fun delete(id: String): Boolean
}
