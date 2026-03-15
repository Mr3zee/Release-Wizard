package com.github.mr3zee.projects

import com.github.mr3zee.ForbiddenException
import com.github.mr3zee.NotFoundException
import com.github.mr3zee.api.ProjectLockInfo
import com.github.mr3zee.audit.AuditService
import com.github.mr3zee.auth.UserSession
import com.github.mr3zee.model.*
import com.github.mr3zee.teams.TeamAccessService

interface ProjectLockService {
    suspend fun acquireLock(projectId: ProjectId, session: UserSession): ProjectLockInfo
    suspend fun releaseLock(projectId: ProjectId, session: UserSession)
    suspend fun heartbeat(projectId: ProjectId, session: UserSession): ProjectLockInfo?
    suspend fun getLockInfo(projectId: ProjectId, session: UserSession): ProjectLockInfo?
    suspend fun forceReleaseLock(projectId: ProjectId, session: UserSession)
}

class DefaultProjectLockService(
    private val lockRepository: ProjectLockRepository,
    private val projectsService: ProjectsService,
    private val teamAccessService: TeamAccessService,
    private val auditService: AuditService,
) : ProjectLockService {

    companion object {
        const val DEFAULT_TTL_MINUTES = 5L
    }

    override suspend fun acquireLock(projectId: ProjectId, session: UserSession): ProjectLockInfo {
        checkAccess(projectId, session)
        val lock = lockRepository.tryAcquire(
            projectId = projectId.value,
            userId = session.userId,
            username = session.username,
            ttlMinutes = DEFAULT_TTL_MINUTES,
        )
        if (lock != null) return lock

        // Lock held by another user — find the active lock for the conflict response.
        // If the lock expired between tryAcquire and now (race), retry once.
        val existingLock = lockRepository.findActiveLock(projectId.value)
        if (existingLock == null) {
            // Race: lock expired between check and here — retry once
            val retryLock = lockRepository.tryAcquire(
                projectId = projectId.value,
                userId = session.userId,
                username = session.username,
                ttlMinutes = DEFAULT_TTL_MINUTES,
            )
            if (retryLock != null) return retryLock
            val retryExisting = lockRepository.findActiveLock(projectId.value)
                ?: throw NotFoundException("Lock state inconsistent — please retry")
            throw LockConflictException(retryExisting)
        }
        throw LockConflictException(existingLock)
    }

    override suspend fun releaseLock(projectId: ProjectId, session: UserSession) {
        checkAccess(projectId, session)
        // Idempotent — no error if lock not found
        lockRepository.release(projectId.value, session.userId)
    }

    override suspend fun heartbeat(projectId: ProjectId, session: UserSession): ProjectLockInfo? {
        checkAccess(projectId, session)
        return lockRepository.heartbeat(projectId.value, session.userId, DEFAULT_TTL_MINUTES)
    }

    override suspend fun getLockInfo(projectId: ProjectId, session: UserSession): ProjectLockInfo? {
        checkAccess(projectId, session)
        return lockRepository.findActiveLock(projectId.value)
    }

    override suspend fun forceReleaseLock(projectId: ProjectId, session: UserSession) {
        val teamId = projectsService.findTeamId(projectId)
            ?: throw NotFoundException("Project not found")
        // Require ADMIN or TEAM_LEAD
        teamAccessService.checkTeamLead(TeamId(teamId), session)
        lockRepository.forceRelease(projectId.value)
        auditService.log(
            TeamId(teamId),
            session,
            AuditAction.LOCK_FORCE_RELEASED,
            AuditTargetType.PROJECT,
            projectId.value,
            "Force-released editing lock",
        )
    }

    private suspend fun checkAccess(projectId: ProjectId, session: UserSession) {
        val teamId = projectsService.findTeamId(projectId)
            ?: throw NotFoundException("Project not found")
        if (session.role == UserRole.ADMIN) return
        teamAccessService.checkMembership(TeamId(teamId), session)
    }
}
