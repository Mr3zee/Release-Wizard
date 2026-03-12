package com.github.mr3zee.projects

import com.github.mr3zee.api.CreateProjectRequest
import com.github.mr3zee.api.UpdateProjectRequest
import com.github.mr3zee.model.ProjectId
import com.github.mr3zee.model.ProjectTemplate

interface ProjectsService {
    suspend fun listProjects(): List<ProjectTemplate>
    suspend fun getProject(id: ProjectId): ProjectTemplate?
    suspend fun createProject(request: CreateProjectRequest): ProjectTemplate
    suspend fun updateProject(id: ProjectId, request: UpdateProjectRequest): ProjectTemplate?
    suspend fun deleteProject(id: ProjectId): Boolean
}

class DefaultProjectsService(
    private val repository: ProjectsRepository,
) : ProjectsService {

    override suspend fun listProjects(): List<ProjectTemplate> {
        return repository.findAll()
    }

    override suspend fun getProject(id: ProjectId): ProjectTemplate? {
        return repository.findById(id)
    }

    override suspend fun createProject(request: CreateProjectRequest): ProjectTemplate {
        return repository.create(
            name = request.name,
            description = request.description,
            dagGraph = request.dagGraph,
            parameters = request.parameters,
        )
    }

    override suspend fun updateProject(id: ProjectId, request: UpdateProjectRequest): ProjectTemplate? {
        return repository.update(
            id = id,
            name = request.name,
            description = request.description,
            dagGraph = request.dagGraph,
            parameters = request.parameters,
        )
    }

    override suspend fun deleteProject(id: ProjectId): Boolean {
        return repository.delete(id)
    }
}
