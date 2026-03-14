package com.github.mr3zee.schedules

import com.github.mr3zee.ForbiddenException
import com.github.mr3zee.auth.UserSession
import com.github.mr3zee.model.ProjectId
import com.github.mr3zee.model.Schedule
import com.github.mr3zee.model.UserRole
import com.github.mr3zee.projects.ProjectsRepository

interface ScheduleService {
    suspend fun listByProject(projectId: ProjectId, session: UserSession): List<Schedule>
    suspend fun getById(id: String, session: UserSession): Schedule?
    suspend fun create(projectId: ProjectId, request: com.github.mr3zee.api.CreateScheduleRequest, session: UserSession): Schedule
    suspend fun toggle(id: String, enabled: Boolean, session: UserSession): Schedule?
    suspend fun delete(id: String, session: UserSession): Boolean
}

class DefaultScheduleService(
    private val repository: ScheduleRepository,
    private val projectsRepository: ProjectsRepository,
) : ScheduleService {

    override suspend fun listByProject(projectId: ProjectId, session: UserSession): List<Schedule> {
        checkProjectAccess(projectId, session)
        return repository.findByProjectId(projectId).map { it.toModel() }
    }

    override suspend fun getById(id: String, session: UserSession): Schedule? {
        val entity = repository.findById(id) ?: return null
        checkProjectAccess(entity.projectId, session)
        return entity.toModel()
    }

    override suspend fun create(
        projectId: ProjectId,
        request: com.github.mr3zee.api.CreateScheduleRequest,
        session: UserSession,
    ): Schedule {
        checkProjectAccess(projectId, session)
        // Validate cron expression
        val cron = CronUtils.parser.parse(request.cronExpression)
        cron.validate()

        val nextRunAt = CronUtils.computeNextRun(request.cronExpression)

        val entity = repository.create(
            projectId = projectId,
            cronExpression = request.cronExpression,
            parameters = request.parameters,
            enabled = request.enabled,
            createdBy = session.userId,
            nextRunAt = nextRunAt,
        )
        return entity.toModel()
    }

    override suspend fun toggle(id: String, enabled: Boolean, session: UserSession): Schedule? {
        val entity = repository.findById(id) ?: return null
        checkAccess(entity, session)

        val nextRunAt = if (enabled) CronUtils.computeNextRun(entity.cronExpression) else entity.nextRunAt
        val updated = repository.update(id, enabled = enabled, nextRunAt = nextRunAt, lastRunAt = null)
        return updated?.toModel()
    }

    override suspend fun delete(id: String, session: UserSession): Boolean {
        val entity = repository.findById(id) ?: return false
        checkAccess(entity, session)
        return repository.delete(id)
    }

    private suspend fun checkAccess(entity: ScheduleEntity, session: UserSession) {
        checkProjectAccess(entity.projectId, session)
    }

    private suspend fun checkProjectAccess(projectId: ProjectId, session: UserSession) {
        if (session.role == UserRole.ADMIN) return
        val projectOwner = projectsRepository.findOwner(projectId)
        if (projectOwner != null && projectOwner != session.userId) {
            throw ForbiddenException("Access denied")
        }
    }

}

private fun ScheduleEntity.toModel() = Schedule(
    id = id,
    projectId = projectId,
    cronExpression = cronExpression,
    parameters = parameters,
    enabled = enabled,
)
