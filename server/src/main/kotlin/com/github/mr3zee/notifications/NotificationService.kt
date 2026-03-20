package com.github.mr3zee.notifications

import com.github.mr3zee.ForbiddenException
import com.github.mr3zee.NotFoundException
import com.github.mr3zee.api.CreateNotificationConfigRequest
import com.github.mr3zee.api.NotificationConfigResponse
import com.github.mr3zee.auth.UserSession
import com.github.mr3zee.connections.ConnectionTester
import com.github.mr3zee.model.NotificationConfig
import com.github.mr3zee.model.ProjectId
import com.github.mr3zee.model.TeamId
import com.github.mr3zee.model.UserRole
import com.github.mr3zee.projects.ProjectsRepository
import com.github.mr3zee.teams.TeamAccessService
import org.slf4j.LoggerFactory

interface NotificationService {
    suspend fun listByProject(projectId: ProjectId, session: UserSession): List<NotificationConfigResponse>
    suspend fun create(request: CreateNotificationConfigRequest, session: UserSession): NotificationConfigResponse
    suspend fun delete(id: String, session: UserSession): Boolean
}

class DefaultNotificationService(
    private val repository: NotificationRepository,
    private val projectsRepository: ProjectsRepository,
    private val teamAccessService: TeamAccessService,
) : NotificationService {
    private val log = LoggerFactory.getLogger(DefaultNotificationService::class.java)

    override suspend fun listByProject(projectId: ProjectId, session: UserSession): List<NotificationConfigResponse> {
        // Verify the caller has access to the project's team (or is admin)
        if (session.role != UserRole.ADMIN) {
            val projectTeamId = projectsRepository.findTeamId(projectId)
                ?: throw NotFoundException("Project not found")
            teamAccessService.checkMembership(TeamId(projectTeamId), session)
        }
        return repository.findByProjectId(projectId).map { it.toResponse() }
    }

    override suspend fun create(request: CreateNotificationConfigRequest, session: UserSession): NotificationConfigResponse {
        // NOTIF-H1: Validate webhook URL for SSRF before storing
        validateNotificationConfig(request.config)

        // Verify the caller has access to the project's team (or is admin)
        if (session.role != UserRole.ADMIN) {
            val projectTeamId = projectsRepository.findTeamId(request.projectId)
                ?: throw NotFoundException("Project not found")
            teamAccessService.checkMembership(TeamId(projectTeamId), session)
        }

        val entity = repository.create(
            projectId = request.projectId,
            userId = session.userId,
            type = request.type,
            config = request.config,
            enabled = request.enabled,
        )
        log.info("Notification config created: {} for project {} (type={})", entity.id, request.projectId.value, request.type)
        return entity.toResponse()
    }

    override suspend fun delete(id: String, session: UserSession): Boolean {
        val entity = repository.findById(id) ?: return false

        // Verify team membership before allowing delete
        if (session.role != UserRole.ADMIN) {
            val projectTeamId = projectsRepository.findTeamId(entity.projectId)
                ?: throw NotFoundException("Project not found")
            teamAccessService.checkMembership(TeamId(projectTeamId), session)
        }

        checkOwnership(entity, session)
        val deleted = repository.delete(id)
        if (deleted) {
            log.info("Notification config deleted: {}", id)
        }
        return deleted
    }

    private fun checkOwnership(entity: NotificationConfigEntity, session: UserSession) {
        if (session.role == UserRole.ADMIN) return
        if (entity.userId.isNotEmpty() && entity.userId != session.userId) {
            throw ForbiddenException("Access denied")
        }
    }
}

/** NOTIF-H1: Validates notification config URLs for SSRF. */
private fun validateNotificationConfig(config: NotificationConfig) {
    when (config) {
        is NotificationConfig.SlackNotification -> {
            require(config.webhookUrl.startsWith("https://")) { "Webhook URL must use HTTPS" }
            ConnectionTester.validateUrlNotPrivate(config.webhookUrl)
        }
    }
}

// NOTIF-H4: Mask webhook URLs in API responses to avoid credential exposure
private fun NotificationConfigEntity.toResponse() = NotificationConfigResponse(
    id = id,
    projectId = projectId,
    type = type,
    config = config.masked(),
    enabled = enabled,
)

private fun NotificationConfig.masked(): NotificationConfig = when (this) {
    is NotificationConfig.SlackNotification -> {
        val masked = if (webhookUrl.length > 20) webhookUrl.take(20) + "****" else "****"
        copy(webhookUrl = masked)
    }
}
