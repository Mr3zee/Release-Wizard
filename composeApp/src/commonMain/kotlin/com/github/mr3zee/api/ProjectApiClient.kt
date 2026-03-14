package com.github.mr3zee.api

import com.github.mr3zee.model.ProjectId
import com.github.mr3zee.model.ProjectTemplate
import io.ktor.client.*
import io.ktor.client.call.*
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
}
