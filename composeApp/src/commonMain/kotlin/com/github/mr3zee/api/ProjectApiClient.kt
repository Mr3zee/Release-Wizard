package com.github.mr3zee.api

import com.github.mr3zee.model.ProjectId
import com.github.mr3zee.model.ProjectTemplate
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.request.*
import io.ktor.client.request.parameter
import io.ktor.http.*

class ProjectApiClient(private val client: HttpClient) {

    suspend fun listProjects(
        offset: Int = 0,
        limit: Int = 20,
        search: String? = null,
    ): ProjectListResponse {
        val response = client.get(serverUrl(ApiRoutes.Projects.BASE)) {
            parameter("offset", offset)
            parameter("limit", limit)
            search?.let { parameter("q", it) }
        }
        return response.body()
    }

    suspend fun getProject(id: ProjectId): ProjectTemplate {
        val response = client.get(serverUrl(ApiRoutes.Projects.byId(id.value)))
        return response.body<ProjectResponse>().project
    }

    suspend fun createProject(request: CreateProjectRequest): ProjectTemplate {
        val response = client.post(serverUrl(ApiRoutes.Projects.BASE)) {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
        return response.body<ProjectResponse>().project
    }

    suspend fun updateProject(id: ProjectId, request: UpdateProjectRequest): ProjectTemplate {
        val response = client.put(serverUrl(ApiRoutes.Projects.byId(id.value))) {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
        return response.body<ProjectResponse>().project
    }

    suspend fun deleteProject(id: ProjectId) {
        client.delete(serverUrl(ApiRoutes.Projects.byId(id.value)))
    }

    suspend fun acquireLock(projectId: ProjectId): ProjectLockInfo {
        val response = client.post(serverUrl(ApiRoutes.Projects.lock(projectId.value)))
        return response.body()
    }

    suspend fun releaseLock(projectId: ProjectId) {
        try {
            client.delete(serverUrl(ApiRoutes.Projects.lock(projectId.value)))
        } catch (_: ClientRequestException) {
            // Fire-and-forget — TTL handles cleanup
            // todo claude: java in common main
        } catch (_: java.io.IOException) {
            // Network failure — TTL handles cleanup
        }
    }

    suspend fun heartbeatLock(projectId: ProjectId): ProjectLockInfo {
        val response = client.put(serverUrl(ApiRoutes.Projects.lockHeartbeat(projectId.value)))
        return response.body()
    }

    // todo claude: unused
    suspend fun getLockInfo(projectId: ProjectId): ProjectLockInfo? {
        return try {
            val response = client.get(serverUrl(ApiRoutes.Projects.lock(projectId.value)))
            response.body()
        } catch (e: ClientRequestException) {
            if (e.response.status.value == 404) null else throw e
        }
    }

    suspend fun forceReleaseLock(projectId: ProjectId) {
        client.delete(serverUrl(ApiRoutes.Projects.lock(projectId.value))) {
            parameter("force", true)
        }
    }
}
