package com.github.mr3zee.api

import com.github.mr3zee.model.ProjectId
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*

class WebhookTriggerApiClient(private val client: HttpClient) {

    suspend fun listTriggers(projectId: ProjectId): List<TriggerResponse> {
        return client.get(serverUrl(ApiRoutes.Triggers.byProject(projectId.value))).body<TriggerListResponse>().triggers
    }

    suspend fun createTrigger(projectId: ProjectId, request: CreateTriggerRequest): TriggerResponse {
        return client.post(serverUrl(ApiRoutes.Triggers.byProject(projectId.value))) {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body<TriggerResponse>()
    }

    suspend fun toggleTrigger(projectId: ProjectId, triggerId: String, enabled: Boolean): TriggerResponse {
        return client.put(serverUrl(ApiRoutes.Triggers.byId(projectId.value, triggerId))) {
            contentType(ContentType.Application.Json)
            setBody(ToggleTriggerRequest(enabled = enabled))
        }.body<TriggerResponse>()
    }

    suspend fun deleteTrigger(projectId: ProjectId, triggerId: String) {
        client.delete(serverUrl(ApiRoutes.Triggers.byId(projectId.value, triggerId)))
    }
}
