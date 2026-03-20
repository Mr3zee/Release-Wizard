package com.github.mr3zee.schedules

import com.github.mr3zee.NotFoundException
import com.github.mr3zee.audit.AuditService
import com.github.mr3zee.auth.UserSession
import com.github.mr3zee.model.*
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
    private val auditService: AuditService,
) : ScheduleService {
    private val log = LoggerFactory.getLogger(DefaultScheduleService::class.java)

    companion object {
        /** SCHED-M8: Parameter validation constants */
        const val MAX_SCHEDULE_PARAMETERS = 50
        const val MAX_PARAM_KEY_LENGTH = 255
        const val MAX_PARAM_VALUE_LENGTH = 1000
        /** SCHED-H4: Per-project schedule count cap to prevent execution engine saturation */
        const val MAX_SCHEDULES_PER_PROJECT = 20
    }

    override suspend fun listByProject(projectId: ProjectId, session: UserSession): List<Schedule> {
        resolveProjectTeamId(projectId, session)
        return repository.findByProjectId(projectId).map { it.toModel() }
    }

    override suspend fun getById(id: String, session: UserSession): Schedule? {
        val entity = repository.findById(id) ?: return null
        resolveProjectTeamId(entity.projectId, session)
        return entity.toModel()
    }

    override suspend fun create(
        projectId: ProjectId,
        request: com.github.mr3zee.api.CreateScheduleRequest,
        session: UserSession,
    ): Schedule {
        val teamId = resolveProjectTeamId(projectId, session)
        // SCHED-H4: Enforce per-project schedule count cap
        val currentCount = repository.countByProjectId(projectId)
        require(currentCount < MAX_SCHEDULES_PER_PROJECT) {
            "Maximum $MAX_SCHEDULES_PER_PROJECT schedules per project reached"
        }
        // SCHED-M8: Cap schedule parameter list size and value lengths
        require(request.parameters.size <= MAX_SCHEDULE_PARAMETERS) { "Maximum $MAX_SCHEDULE_PARAMETERS parameters allowed" }
        request.parameters.forEach { p ->
            require(p.key.length <= MAX_PARAM_KEY_LENGTH) { "Parameter key must not exceed $MAX_PARAM_KEY_LENGTH characters" }
            require(p.value.length <= MAX_PARAM_VALUE_LENGTH) { "Parameter value must not exceed $MAX_PARAM_VALUE_LENGTH characters" }
        }
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
        // SCHED-M5: Audit schedule creation
        auditService.log(TeamId(teamId), session, AuditAction.SCHEDULE_CREATED, AuditTargetType.SCHEDULE, entity.id, "Created schedule with cron '${request.cronExpression}' for project ${projectId.value}")
        return entity.toModel()
    }

    override suspend fun toggle(id: String, enabled: Boolean, session: UserSession): Schedule? {
        val entity = repository.findById(id) ?: return null
        val teamId = resolveProjectTeamId(entity.projectId, session)

        val nextRunAt = if (enabled) CronUtils.computeNextRun(entity.cronExpression) else entity.nextRunAt
        val updated = repository.update(id, enabled = enabled, nextRunAt = nextRunAt, lastRunAt = null)
        if (updated != null) {
            log.info("Schedule {} toggled to enabled={}", id, enabled)
            // SCHED-M5: Audit schedule toggle
            auditService.log(TeamId(teamId), session, AuditAction.SCHEDULE_UPDATED, AuditTargetType.SCHEDULE, id, "Toggled schedule to enabled=$enabled")
        }
        return updated?.toModel()
    }

    override suspend fun delete(id: String, session: UserSession): Boolean {
        val entity = repository.findById(id) ?: return false
        val teamId = resolveProjectTeamId(entity.projectId, session)
        val deleted = repository.delete(id)
        if (deleted) {
            log.info("Schedule deleted: {}", id)
            // SCHED-M5: Audit schedule deletion
            auditService.log(TeamId(teamId), session, AuditAction.SCHEDULE_DELETED, AuditTargetType.SCHEDULE, id, "Deleted schedule for project ${entity.projectId.value}")
        }
        return deleted
    }

    /**
     * Resolves the team ID for a project, performs access check, and returns the team ID.
     * Combines access check + team ID resolution in a single DB lookup.
     */
    private suspend fun resolveProjectTeamId(projectId: ProjectId, session: UserSession): String {
        val projectTeamId = projectsRepository.findTeamId(projectId)
            ?: throw NotFoundException("Project not found")
        if (session.role != UserRole.ADMIN) {
            teamAccessService.checkMembership(TeamId(projectTeamId), session)
        }
        return projectTeamId
    }

}

private fun ScheduleEntity.toModel() = Schedule(
    id = id,
    projectId = projectId,
    cronExpression = cronExpression,
    parameters = parameters,
    enabled = enabled,
)
