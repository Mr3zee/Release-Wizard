package com.github.mr3zee.schedules

import com.github.mr3zee.model.Parameter
import com.github.mr3zee.model.ProjectId
import com.github.mr3zee.model.Schedule
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
    suspend fun delete(id: String): Boolean
}
