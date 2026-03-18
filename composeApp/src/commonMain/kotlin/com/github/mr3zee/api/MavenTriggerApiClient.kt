package com.github.mr3zee.api

import com.github.mr3zee.model.MavenTrigger
import com.github.mr3zee.model.ProjectId
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*

class MavenTriggerApiClient(private val client: HttpClient) {

    suspend fun listMavenTriggers(projectId: ProjectId): List<MavenTrigger> {
        return client.get(serverUrl(ApiRoutes.MavenTriggers.byProject(projectId.value))).body<MavenTriggerListResponse>().triggers
    }

    suspend fun createMavenTrigger(projectId: ProjectId, request: CreateMavenTriggerRequest): MavenTrigger {
        return client.post(serverUrl(ApiRoutes.MavenTriggers.byProject(projectId.value))) {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body<MavenTriggerResponse>().trigger
    }

    suspend fun toggleMavenTrigger(projectId: ProjectId, triggerId: String, enabled: Boolean): MavenTrigger {
        return client.put(serverUrl(ApiRoutes.MavenTriggers.byId(projectId.value, triggerId))) {
            contentType(ContentType.Application.Json)
            setBody(ToggleMavenTriggerRequest(enabled = enabled))
        }.body<MavenTriggerResponse>().trigger
    }

    suspend fun deleteMavenTrigger(projectId: ProjectId, triggerId: String) {
        client.delete(serverUrl(ApiRoutes.MavenTriggers.byId(projectId.value, triggerId)))
    }
}
