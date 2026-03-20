package com.github.mr3zee.triggers

import com.github.mr3zee.NotFoundException
import com.github.mr3zee.api.CreateTriggerRequest
import com.github.mr3zee.api.TriggerResponse
import com.github.mr3zee.audit.AuditService
import com.github.mr3zee.auth.UserSession
import com.github.mr3zee.model.*
import com.github.mr3zee.projects.ProjectsRepository
import com.github.mr3zee.teams.TeamAccessService
import com.github.mr3zee.releases.ReleasesService
import com.github.mr3zee.api.ApiRoutes
import org.slf4j.LoggerFactory
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

interface TriggerService {
    suspend fun listByProject(projectId: ProjectId, session: UserSession): List<TriggerResponse>
    suspend fun getById(id: String, session: UserSession): TriggerResponse?
    suspend fun create(projectId: ProjectId, request: CreateTriggerRequest, session: UserSession, baseUrl: String): TriggerResponse
    suspend fun toggle(id: String, enabled: Boolean, session: UserSession): TriggerResponse?
    suspend fun delete(id: String, session: UserSession): Boolean
    suspend fun fireWebhook(triggerId: String, secret: String): Boolean
}

class DefaultTriggerService(
    private val repository: TriggerRepository,
    private val releasesService: ReleasesService,
    private val projectsRepository: ProjectsRepository,
    private val webhookBaseUrl: String,
    private val teamAccessService: TeamAccessService,
    private val auditService: AuditService,
) : TriggerService {
    private val log = LoggerFactory.getLogger(DefaultTriggerService::class.java)

    companion object {
        const val MAX_TRIGGERS_PER_PROJECT = 50
    }

    override suspend fun listByProject(projectId: ProjectId, session: UserSession): List<TriggerResponse> {
        checkProjectAccess(projectId, session)
        return repository.findByProjectId(projectId).map { it.toResponse() }
    }

    override suspend fun getById(id: String, session: UserSession): TriggerResponse? {
        val entity = repository.findById(id) ?: return null
        checkAccess(entity, session)
        return entity.toResponse()
    }

    override suspend fun create(
        projectId: ProjectId,
        request: CreateTriggerRequest,
        session: UserSession,
        baseUrl: String,
    ): TriggerResponse {
        checkProjectAccess(projectId, session)
        val currentCount = repository.countByProjectId(projectId)
        require(currentCount < MAX_TRIGGERS_PER_PROJECT) {
            "Maximum $MAX_TRIGGERS_PER_PROJECT triggers per project reached"
        }
        val rawSecret = generateSecret()
        val secretHash = sha256Hex(rawSecret)
        val entity = repository.create(
            projectId = projectId,
            secret = secretHash,
            parametersTemplate = request.parametersTemplate,
        )
        val projectTeamId = projectsRepository.findTeamId(projectId)
        if (projectTeamId != null) {
            auditService.log(TeamId(projectTeamId), session, AuditAction.TRIGGER_CREATED, AuditTargetType.TRIGGER, entity.id, "Created trigger for project ${projectId.value}")
        }
        log.info("Trigger created: {} for project {}", entity.id, projectId.value)
        return TriggerResponse(
            id = entity.id,
            projectId = entity.projectId,
            secret = rawSecret,
            enabled = entity.enabled,
            webhookUrl = "$webhookBaseUrl${ApiRoutes.Triggers.webhook(entity.id)}",
        )
    }

    override suspend fun toggle(id: String, enabled: Boolean, session: UserSession): TriggerResponse? {
        val entity = repository.findById(id) ?: return null
        checkAccess(entity, session)
        val updated = repository.update(id, enabled = enabled) ?: return null
        return updated.toResponse()
    }

    override suspend fun delete(id: String, session: UserSession): Boolean {
        val entity = repository.findById(id) ?: return false
        checkAccess(entity, session)
        val deleted = repository.delete(id)
        if (deleted) {
            val projectTeamId = projectsRepository.findTeamId(entity.projectId)
            if (projectTeamId != null) {
                auditService.log(TeamId(projectTeamId), session, AuditAction.TRIGGER_DELETED, AuditTargetType.TRIGGER, id, "Deleted trigger for project ${entity.projectId.value}")
            }
            log.info("Trigger deleted: {}", id)
        }
        return deleted
    }

    override suspend fun fireWebhook(triggerId: String, secret: String): Boolean {
        val trigger = repository.findById(triggerId) ?: run {
            log.warn("Webhook fire rejected: trigger {} not found", triggerId)
            return false
        }
        if (!trigger.enabled) {
            log.warn("Webhook fire rejected: trigger {} is disabled", triggerId)
            return false
        }

        // Hash the incoming secret and compare against stored hash (timing-safe)
        val incomingHash = sha256Hex(secret)
        val storedHash = trigger.secret
        if (!MessageDigest.isEqual(incomingHash.toByteArray(Charsets.UTF_8), storedHash.toByteArray(Charsets.UTF_8))) {
            log.warn("Webhook fire rejected: invalid secret for trigger {}", triggerId)
            return false
        }

        log.info("Webhook fired: trigger {} for project {}", triggerId, trigger.projectId.value)
        releasesService.startScheduledRelease(trigger.projectId, trigger.parametersTemplate)
        return true
    }

    private suspend fun checkAccess(entity: TriggerEntity, session: UserSession) {
        checkProjectAccess(entity.projectId, session)
    }

    private suspend fun checkProjectAccess(projectId: ProjectId, session: UserSession) {
        if (session.role == UserRole.ADMIN) return
        val projectTeamId = projectsRepository.findTeamId(projectId)
            ?: throw NotFoundException("Project not found")
        teamAccessService.checkMembership(TeamId(projectTeamId), session)
    }

    // MAVEN-H4: SecureRandom singleton — avoid per-call instantiation (entropy pool depletion under load)
    private val secureRandom = SecureRandom()

    private fun generateSecret(): String {
        val bytes = ByteArray(32)
        secureRandom.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private fun sha256Hex(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(input.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }

    private fun TriggerEntity.toResponse() = TriggerResponse(
        id = id,
        projectId = projectId,
        secret = "********",
        enabled = enabled,
        webhookUrl = "$webhookBaseUrl${ApiRoutes.Triggers.webhook(id)}",
    )
}
