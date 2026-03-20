package com.github.mr3zee.mavenpublication

import com.github.mr3zee.NotFoundException
import com.github.mr3zee.api.CreateMavenTriggerRequest
import com.github.mr3zee.audit.AuditService
import com.github.mr3zee.auth.UserSession
import com.github.mr3zee.connections.ConnectionTester
import com.github.mr3zee.model.*
import com.github.mr3zee.projects.ProjectsRepository
import com.github.mr3zee.teams.TeamAccessService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.seconds

// Rejects consecutive dots (would produce double slashes in Maven path) and leading/trailing dots.
private val GROUP_ID_REGEX = Regex("^[a-z0-9]([a-z0-9._-]*[a-z0-9])?$")
private val ARTIFACT_ID_REGEX = Regex("^[a-zA-Z0-9]([a-zA-Z0-9._-]*[a-zA-Z0-9])?$")
private val PARAMETER_KEY_REGEX = Regex("^[a-zA-Z0-9_.-]+$")

/** MAVEN-M2: Per-project Maven trigger count cap */
private const val MAX_TRIGGERS_PER_PROJECT = 20

interface MavenTriggerService {
    suspend fun listByProject(projectId: ProjectId, session: UserSession): List<MavenTrigger>
    suspend fun getById(id: String, session: UserSession): MavenTrigger?
    suspend fun create(projectId: ProjectId, request: CreateMavenTriggerRequest, session: UserSession): MavenTrigger
    suspend fun toggle(id: String, enabled: Boolean, session: UserSession): MavenTrigger?
    suspend fun delete(id: String, session: UserSession): Boolean
}

class DefaultMavenTriggerService(
    private val repository: MavenTriggerRepository,
    private val fetcher: MavenMetadataFetcher,
    private val projectsRepository: ProjectsRepository,
    private val teamAccessService: TeamAccessService,
    private val auditService: AuditService,
) : MavenTriggerService {

    override suspend fun listByProject(projectId: ProjectId, session: UserSession): List<MavenTrigger> {
        checkProjectAccess(projectId, session)
        return repository.findByProjectId(projectId)
    }

    override suspend fun getById(id: String, session: UserSession): MavenTrigger? {
        val trigger = repository.findById(id) ?: return null
        checkProjectAccess(trigger.projectId, session)
        return trigger
    }

    override suspend fun create(
        projectId: ProjectId,
        request: CreateMavenTriggerRequest,
        session: UserSession,
    ): MavenTrigger {
        checkProjectAccess(projectId, session)
        // MAVEN-M2: Enforce per-project trigger count cap
        val currentCount = repository.countByProjectId(projectId)
        require(currentCount < MAX_TRIGGERS_PER_PROJECT) {
            "Maximum $MAX_TRIGGERS_PER_PROJECT Maven triggers per project reached"
        }
        validateRequest(request)

        // MAVEN-M5: Tighter timeout for Maven fetch during create to prevent slow requests
        // from blocking the handler for the full 30s HTTP client timeout
        val initialVersions = withTimeoutOrNull(5.seconds) {
            fetcher.fetch(request.repoUrl, request.groupId, request.artifactId)
        } ?: throw IllegalArgumentException(
            "Could not fetch Maven metadata at the given URL. " +
                "Verify the repository URL, groupId, and artifactId."
        )

        val trigger = repository.create(
            projectId = projectId,
            repoUrl = request.repoUrl,
            groupId = request.groupId,
            artifactId = request.artifactId,
            parameterKey = request.parameterKey,
            includeSnapshots = request.includeSnapshots,
            enabled = request.enabled,
            knownVersions = initialVersions,
            createdBy = session.userId,
        )
        val projectTeamId = projectsRepository.findTeamId(projectId)
        if (projectTeamId != null) {
            auditService.log(TeamId(projectTeamId), session, AuditAction.TRIGGER_CREATED, AuditTargetType.TRIGGER, trigger.id, "Created Maven trigger for ${request.groupId}:${request.artifactId}")
        }
        return trigger
    }

    override suspend fun toggle(id: String, enabled: Boolean, session: UserSession): MavenTrigger? {
        val trigger = repository.findById(id) ?: return null
        checkProjectAccess(trigger.projectId, session)
        val updated = repository.updateEnabled(id, enabled)
        if (updated != null) {
            val projectTeamId = projectsRepository.findTeamId(trigger.projectId)
            if (projectTeamId != null) {
                auditService.log(TeamId(projectTeamId), session, AuditAction.TRIGGER_UPDATED, AuditTargetType.TRIGGER, id, "Maven trigger toggled to enabled=$enabled")
            }
        }
        return updated
    }

    override suspend fun delete(id: String, session: UserSession): Boolean {
        val trigger = repository.findById(id) ?: return false
        checkProjectAccess(trigger.projectId, session)
        val deleted = repository.delete(id)
        if (deleted) {
            val projectTeamId = projectsRepository.findTeamId(trigger.projectId)
            if (projectTeamId != null) {
                auditService.log(TeamId(projectTeamId), session, AuditAction.TRIGGER_DELETED, AuditTargetType.TRIGGER, id, "Deleted Maven trigger for ${trigger.groupId}:${trigger.artifactId}")
            }
        }
        return deleted
    }

    private suspend fun validateRequest(request: CreateMavenTriggerRequest) {
        val isHttps = request.repoUrl.startsWith("https://")
        val isLocalhostHttp = request.repoUrl.startsWith("http://") &&
            (request.repoUrl.startsWith("http://localhost") || request.repoUrl.startsWith("http://127.0.0.1"))
        require(isHttps || isLocalhostHttp) {
            "repoUrl must use https:// (http:// is only allowed for localhost)"
        }
        // DNS resolution is blocking — run on IO dispatcher to avoid blocking Ktor workers.
        withContext(Dispatchers.IO) { ConnectionTester.validateUrlNotPrivate(request.repoUrl) }
        require(request.groupId.isNotBlank()) { "groupId must not be blank" }
        require(GROUP_ID_REGEX.matches(request.groupId)) {
            "groupId must contain only lowercase letters, digits, dots, underscores, or hyphens, and must not start or end with a dot"
        }
        require(!request.groupId.contains("..")) { "groupId must not contain consecutive dots" }
        require(request.artifactId.isNotBlank()) { "artifactId must not be blank" }
        require(ARTIFACT_ID_REGEX.matches(request.artifactId)) {
            "artifactId must contain only letters, digits, dots, underscores, or hyphens, and must not start or end with a dot"
        }
        require(!request.artifactId.contains("..")) { "artifactId must not contain consecutive dots" }
        require(request.parameterKey.isNotBlank()) { "parameterKey must not be blank" }
        require(PARAMETER_KEY_REGEX.matches(request.parameterKey)) {
            "parameterKey must contain only letters, digits, underscores, dots, or hyphens"
        }
    }

    private suspend fun checkProjectAccess(projectId: ProjectId, session: UserSession) {
        if (session.role == UserRole.ADMIN) return
        val projectTeamId = projectsRepository.findTeamId(projectId)
            ?: throw NotFoundException("Project not found")
        teamAccessService.checkMembership(TeamId(projectTeamId), session)
    }
}
