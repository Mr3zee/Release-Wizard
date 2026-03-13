package com.github.mr3zee.triggers

import com.github.mr3zee.ForbiddenException
import com.github.mr3zee.api.CreateTriggerRequest
import com.github.mr3zee.api.TriggerResponse
import com.github.mr3zee.auth.UserSession
import com.github.mr3zee.model.ProjectId
import com.github.mr3zee.model.UserRole
import com.github.mr3zee.projects.ProjectsRepository
import com.github.mr3zee.releases.ReleasesService
import com.github.mr3zee.api.ApiRoutes
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
) : TriggerService {

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
        val rawSecret = generateSecret()
        val secretHash = sha256Hex(rawSecret)
        val entity = repository.create(
            projectId = projectId,
            secret = secretHash,
            parametersTemplate = request.parametersTemplate,
        )
        // Return the raw secret only in the creation response so the caller can store it
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
        return repository.delete(id)
    }

    override suspend fun fireWebhook(triggerId: String, secret: String): Boolean {
        val trigger = repository.findById(triggerId) ?: return false
        if (!trigger.enabled) return false

        // Hash the incoming secret and compare against stored hash (timing-safe)
        val incomingHash = sha256Hex(secret)
        val storedHash = trigger.secret
        if (!MessageDigest.isEqual(incomingHash.toByteArray(Charsets.UTF_8), storedHash.toByteArray(Charsets.UTF_8))) {
            return false
        }

        releasesService.startScheduledRelease(trigger.projectId, trigger.parametersTemplate)
        return true
    }

    private suspend fun checkAccess(entity: TriggerEntity, session: UserSession) {
        checkProjectAccess(entity.projectId, session)
    }

    private suspend fun checkProjectAccess(projectId: ProjectId, session: UserSession) {
        if (session.role == UserRole.ADMIN) return
        val projectOwner = projectsRepository.findOwner(projectId)
        if (projectOwner != null && projectOwner != session.userId) {
            throw ForbiddenException("Access denied")
        }
    }

    private fun generateSecret(): String {
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
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
