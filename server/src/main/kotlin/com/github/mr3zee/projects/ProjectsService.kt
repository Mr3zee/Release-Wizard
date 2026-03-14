package com.github.mr3zee.projects

import com.github.mr3zee.ForbiddenException
import com.github.mr3zee.api.CreateProjectRequest
import com.github.mr3zee.api.UpdateProjectRequest
import com.github.mr3zee.auth.UserSession
import com.github.mr3zee.model.ProjectId
import com.github.mr3zee.model.ProjectTemplate
import com.github.mr3zee.model.UserRole

interface ProjectsService {
    suspend fun listProjects(session: UserSession, offset: Int = 0, limit: Int = 20, search: String? = null): Pair<List<ProjectTemplate>, Long>
    suspend fun getProject(id: ProjectId, session: UserSession): ProjectTemplate?
    suspend fun createProject(request: CreateProjectRequest, session: UserSession): ProjectTemplate
    suspend fun updateProject(id: ProjectId, request: UpdateProjectRequest, session: UserSession): ProjectTemplate?
    suspend fun deleteProject(id: ProjectId, session: UserSession): Boolean
}

class DefaultProjectsService(
    private val repository: ProjectsRepository,
) : ProjectsService {

    override suspend fun listProjects(session: UserSession, offset: Int, limit: Int, search: String?): Pair<List<ProjectTemplate>, Long> {
        val ownerId = if (session.role == UserRole.ADMIN) null else session.userId
        return repository.findAllWithCount(ownerId = ownerId, offset = offset, limit = limit, search = search)
    }

    override suspend fun getProject(id: ProjectId, session: UserSession): ProjectTemplate? {
        checkAccess(id, session)
        return repository.findById(id)
    }

    override suspend fun createProject(request: CreateProjectRequest, session: UserSession): ProjectTemplate {
        return repository.create(
            name = request.name,
            description = request.description,
            dagGraph = request.dagGraph,
            parameters = request.parameters,
            ownerId = session.userId,
            defaultTags = request.defaultTags,
        )
    }

    override suspend fun updateProject(id: ProjectId, request: UpdateProjectRequest, session: UserSession): ProjectTemplate? {
        checkAccess(id, session)
        return repository.update(
            id = id,
            name = request.name,
            description = request.description,
            dagGraph = request.dagGraph,
            parameters = request.parameters,
            defaultTags = request.defaultTags,
        )
    }

    override suspend fun deleteProject(id: ProjectId, session: UserSession): Boolean {
        checkAccess(id, session)
        return repository.delete(id)
    }

    private suspend fun checkAccess(id: ProjectId, session: UserSession) {
        if (session.role == UserRole.ADMIN) return
        val ownerId = repository.findOwner(id)
        if (ownerId != null && ownerId != session.userId) {
            throw ForbiddenException("Access denied")
        }
    }
}
