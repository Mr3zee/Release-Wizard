package com.github.mr3zee.api

import com.github.mr3zee.model.ProjectId
import com.github.mr3zee.model.Schedule
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*

class ScheduleApiClient(private val client: HttpClient) {

    suspend fun listSchedules(projectId: ProjectId): List<Schedule> {
        return client.get(serverUrl(ApiRoutes.Schedules.byProject(projectId.value))).body<ScheduleListResponse>().schedules
    }

    suspend fun createSchedule(projectId: ProjectId, request: CreateScheduleRequest): Schedule {
        return client.post(serverUrl(ApiRoutes.Schedules.byProject(projectId.value))) {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body<ScheduleResponse>().schedule
    }

    suspend fun toggleSchedule(projectId: ProjectId, scheduleId: String, enabled: Boolean): Schedule {
        return client.put(serverUrl(ApiRoutes.Schedules.byId(projectId.value, scheduleId))) {
            contentType(ContentType.Application.Json)
            setBody(ToggleScheduleRequest(enabled = enabled))
        }.body<ScheduleResponse>().schedule
    }

    suspend fun deleteSchedule(projectId: ProjectId, scheduleId: String) {
        client.delete(serverUrl(ApiRoutes.Schedules.byId(projectId.value, scheduleId)))
    }
}
