package com.github.mr3zee.api

import com.github.mr3zee.model.Parameter
import com.github.mr3zee.model.Schedule
import kotlinx.serialization.Serializable

@Serializable
data class CreateScheduleRequest(
    val cronExpression: String,
    val parameters: List<Parameter> = emptyList(),
    val enabled: Boolean = true,
)

@Serializable
data class ScheduleResponse(
    val schedule: Schedule,
)

@Serializable
data class ScheduleListResponse(
    val schedules: List<Schedule>,
)
