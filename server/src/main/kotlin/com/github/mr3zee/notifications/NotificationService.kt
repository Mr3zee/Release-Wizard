package com.github.mr3zee.notifications

import com.github.mr3zee.ForbiddenException
import com.github.mr3zee.api.CreateNotificationConfigRequest
import com.github.mr3zee.api.NotificationConfigResponse
import com.github.mr3zee.auth.UserSession
import com.github.mr3zee.model.ProjectId
import com.github.mr3zee.model.UserRole
import com.github.mr3zee.projects.ProjectsRepository

interface NotificationService {
    suspend fun listByProject(projectId: ProjectId, session: UserSession): List<NotificationConfigResponse>
    suspend fun create(request: CreateNotificationConfigRequest, session: UserSession): NotificationConfigResponse
    suspend fun delete(id: String, session: UserSession): Boolean
}

class DefaultNotificationService(
    private val repository: NotificationRepository,
    private val projectsRepository: ProjectsRepository,
) : NotificationService {

    override suspend fun listByProject(projectId: ProjectId, session: UserSession): List<NotificationConfigResponse> {
        return repository.findByProjectId(projectId).map { it.toResponse() }
    }

    override suspend fun create(request: CreateNotificationConfigRequest, session: UserSession): NotificationConfigResponse {
        // Verify the caller owns the project (or is admin)
        val projectOwner = projectsRepository.findOwner(request.projectId)
        if (session.role != UserRole.ADMIN && projectOwner != null && projectOwner != session.userId) {
            throw ForbiddenException("Access denied")
        }

        val entity = repository.create(
            projectId = request.projectId,
            userId = session.userId,
            type = request.type,
            config = request.config,
            enabled = request.enabled,
        )
        return entity.toResponse()
    }

    override suspend fun delete(id: String, session: UserSession): Boolean {
        val entity = repository.findById(id) ?: return false
        checkAccess(entity, session)
        return repository.delete(id)
    }

    private fun checkAccess(entity: NotificationConfigEntity, session: UserSession) {
        if (session.role == UserRole.ADMIN) return
        if (entity.userId.isNotEmpty() && entity.userId != session.userId) {
            throw ForbiddenException("Access denied")
        }
    }
}

private fun NotificationConfigEntity.toResponse() = NotificationConfigResponse(
    id = id,
    projectId = projectId,
    type = type,
    config = config,
    enabled = enabled,
)
