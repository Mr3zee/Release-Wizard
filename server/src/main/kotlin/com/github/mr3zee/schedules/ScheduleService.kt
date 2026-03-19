package com.github.mr3zee.schedules

import com.github.mr3zee.NotFoundException
import com.github.mr3zee.auth.UserSession
import com.github.mr3zee.model.ProjectId
import com.github.mr3zee.model.Schedule
import com.github.mr3zee.model.TeamId
import com.github.mr3zee.model.UserRole
import com.github.mr3zee.projects.ProjectsRepository
import com.github.mr3zee.teams.TeamAccessService
import org.slf4j.LoggerFactory

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
    private val teamAccessService: TeamAccessService,
) : ScheduleService {
    private val log = LoggerFactory.getLogger(DefaultScheduleService::class.java)

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

        // Reject expressions that fire more than once per 5 minutes
        CronUtils.validateMinimumInterval(request.cronExpression)

        val nextRunAt = CronUtils.computeNextRun(request.cronExpression)

        val entity = repository.create(
            projectId = projectId,
            cronExpression = request.cronExpression,
            parameters = request.parameters,
            enabled = request.enabled,
            createdBy = session.userId,
            nextRunAt = nextRunAt,
        )
        log.info("Schedule created: {} for project {} (cron='{}')", entity.id, projectId.value, request.cronExpression)
        return entity.toModel()
    }

    override suspend fun toggle(id: String, enabled: Boolean, session: UserSession): Schedule? {
        val entity = repository.findById(id) ?: return null
        checkAccess(entity, session)

        val nextRunAt = if (enabled) CronUtils.computeNextRun(entity.cronExpression) else entity.nextRunAt
        val updated = repository.update(id, enabled = enabled, nextRunAt = nextRunAt, lastRunAt = null)
        if (updated != null) {
            log.info("Schedule {} toggled to enabled={}", id, enabled)
        }
        return updated?.toModel()
    }

    override suspend fun delete(id: String, session: UserSession): Boolean {
        val entity = repository.findById(id) ?: return false
        checkAccess(entity, session)
        val deleted = repository.delete(id)
        if (deleted) {
            log.info("Schedule deleted: {}", id)
        }
        return deleted
    }

    private suspend fun checkAccess(entity: ScheduleEntity, session: UserSession) {
        checkProjectAccess(entity.projectId, session)
    }

    private suspend fun checkProjectAccess(projectId: ProjectId, session: UserSession) {
        if (session.role == UserRole.ADMIN) return
        val projectTeamId = projectsRepository.findTeamId(projectId)
            ?: throw NotFoundException("Project not found")
        teamAccessService.checkMembership(TeamId(projectTeamId), session)
    }

}

private fun ScheduleEntity.toModel() = Schedule(
    id = id,
    projectId = projectId,
    cronExpression = cronExpression,
    parameters = parameters,
    enabled = enabled,
)
