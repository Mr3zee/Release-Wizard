package com.github.mr3zee.usernotifications

import com.github.mr3zee.api.ReleaseEvent
import com.github.mr3zee.execution.ExecutionEngine
import com.github.mr3zee.model.*
import com.github.mr3zee.releases.ReleasesRepository
import com.github.mr3zee.teams.TeamRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import kotlin.time.Duration.Companion.days

class UserNotificationGenerator(
    private val repository: UserNotificationRepository,
    private val releasesRepository: ReleasesRepository,
    private val teamRepository: TeamRepository,
    private val authService: com.github.mr3zee.auth.AuthService,
) {
    private val log = LoggerFactory.getLogger(UserNotificationGenerator::class.java)

    /**
     * Start listening to execution engine events.
     * Must be called before recovery so events during recovery are captured.
     */
    fun start(engine: ExecutionEngine, scope: CoroutineScope): Job {
        return scope.launch {
            engine.events.collect { event ->
                try {
                    when (event) {
                        is ReleaseEvent.BlockExecutionUpdated -> handleBlockUpdate(event)
                        is ReleaseEvent.ReleaseCompleted -> handleReleaseCompleted(event)
                        else -> { /* ignore other events */ }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                    log.error("Failed to generate notification for event {}", event, e)
                }
            }
        }
    }

    private suspend fun handleBlockUpdate(event: ReleaseEvent.BlockExecutionUpdated) {
        val blockExec = event.blockExecution
        if (blockExec.status != BlockStatus.WAITING_FOR_INPUT) return

        val release = releasesRepository.findById(event.releaseId) ?: return
        val block = release.dagSnapshot.findBlock(blockExec.blockId) ?: return

        val gatePhase = blockExec.gatePhase ?: return
        val gate = when (gatePhase) {
            GatePhase.PRE -> block.preGate
            GatePhase.POST -> block.postGate
        }
        val rule = gate?.approvalRule ?: return
        if (rule.requiredCount < 1) return

        val teamId = releasesRepository.findTeamId(event.releaseId)
        val teamName = if (teamId != null) {
            teamRepository.findById(TeamId(teamId))?.name
        } else null

        // Resolve recipient user IDs
        val recipientUserIds = mutableSetOf<String>()

        // Add explicitly named users
        recipientUserIds.addAll(rule.requiredUserIds)

        // Resolve users matching requiredRole within the team
        if (rule.requiredRole != null && teamId != null) {
            val members = teamRepository.findMembers(TeamId(teamId))
            for (member in members) {
                // Map TeamRole to UserRole for comparison
                val memberMatchesRole = when (rule.requiredRole) {
                    UserRole.SUPERADMIN -> member.role == TeamRole.TEAM_LEAD
                    UserRole.ADMIN -> member.role == TeamRole.TEAM_LEAD // Team leads are the closest match
                    UserRole.USER -> true // All members match USER role
                    null -> false
                }
                if (memberMatchesRole) {
                    recipientUserIds.add(member.userId.value)
                }
            }
        }

        if (recipientUserIds.isEmpty()) return

        val targetType = "release"
        val targetId = event.releaseId.value

        for (userId in recipientUserIds) {
            // Dedup check
            val exists = repository.existsByUserAndTypeAndTarget(
                userId, UserNotificationType.APPROVAL_REQUESTED, targetType, targetId
            )
            if (exists) continue

            repository.insert(
                UserNotification(
                    id = "",
                    userId = userId,
                    type = UserNotificationType.APPROVAL_REQUESTED,
                    teamId = teamId?.let { TeamId(it) },
                    teamName = teamName?.let { sanitize(it) },
                    title = "Approval requested: ${sanitize(block.name)}",
                    message = "Block '${sanitize(block.name)}' in release ${event.releaseId.value.take(8)} is waiting for your approval.",
                    targetType = targetType,
                    targetId = targetId,
                )
            )
        }
    }

    private suspend fun handleReleaseCompleted(event: ReleaseEvent.ReleaseCompleted) {
        val release = releasesRepository.findById(event.releaseId) ?: return
        val createdByUserId = release.createdByUserId ?: return

        val teamId = releasesRepository.findTeamId(event.releaseId)
        val teamName = if (teamId != null) {
            teamRepository.findById(TeamId(teamId))?.name
        } else null

        val statusLabel = when (event.status) {
            ReleaseStatus.SUCCEEDED -> "succeeded"
            ReleaseStatus.FAILED -> "failed"
            ReleaseStatus.CANCELLED -> "cancelled"
            else -> event.status.name.lowercase()
        }

        repository.insert(
            UserNotification(
                id = "",
                userId = createdByUserId,
                type = UserNotificationType.RELEASE_COMPLETED,
                teamId = teamId?.let { TeamId(it) },
                teamName = teamName?.let { sanitize(it) },
                title = "Release $statusLabel",
                message = "Release ${event.releaseId.value.take(8)} has $statusLabel.",
                targetType = "release",
                targetId = event.releaseId.value,
            )
        )
    }

    // ── Direct call methods (from TeamService / AuthRoutes) ─────────────

    suspend fun onTeamInviteReceived(invitedUserId: String, teamId: TeamId, teamName: String) {
        repository.insert(
            UserNotification(
                id = "",
                userId = invitedUserId,
                type = UserNotificationType.TEAM_INVITE_RECEIVED,
                teamId = teamId,
                teamName = sanitize(teamName),
                title = "Team invite received",
                message = "You've been invited to join team '${sanitize(teamName)}'.",
                targetType = "team",
                targetId = teamId.value,
            )
        )
    }

    suspend fun onJoinRequestSubmitted(
        requestingUserId: String,
        requestingUsername: String,
        teamId: TeamId,
        teamName: String,
    ) {
        val members = teamRepository.findMembers(teamId)
        val teamLeadIds = members
            .filter { it.role == TeamRole.TEAM_LEAD }
            .map { it.userId.value }

        for (leadId in teamLeadIds) {
            repository.insert(
                UserNotification(
                    id = "",
                    userId = leadId,
                    type = UserNotificationType.JOIN_REQUEST_RECEIVED,
                    teamId = teamId,
                    teamName = sanitize(teamName),
                    title = "Join request received",
                    message = "User '${sanitize(requestingUsername)}' has requested to join team '${sanitize(teamName)}'.",
                    targetType = "team-manage",
                    targetId = teamId.value,
                )
            )
        }
    }

    suspend fun onJoinRequestDecided(
        requestingUserId: String,
        teamId: TeamId,
        teamName: String,
        approved: Boolean,
    ) {
        val decision = if (approved) "approved" else "rejected"
        repository.insert(
            UserNotification(
                id = "",
                userId = requestingUserId,
                type = UserNotificationType.JOIN_REQUEST_DECIDED,
                teamId = teamId,
                teamName = sanitize(teamName),
                title = "Join request $decision",
                message = "Your request to join team '${sanitize(teamName)}' was $decision.",
                targetType = if (approved) "team" else null,
                targetId = if (approved) teamId.value else null,
            )
        )
    }

    suspend fun onMemberRoleChanged(
        userId: String,
        teamId: TeamId,
        teamName: String,
        newRole: TeamRole,
    ) {
        repository.insert(
            UserNotification(
                id = "",
                userId = userId,
                type = UserNotificationType.MEMBER_ROLE_CHANGED,
                teamId = teamId,
                teamName = sanitize(teamName),
                title = "Role changed",
                message = "Your role in team '${sanitize(teamName)}' was changed to ${newRole.name.lowercase().replace('_', ' ')}.",
                targetType = "team",
                targetId = teamId.value,
            )
        )
    }

    suspend fun onAccountPendingApproval(newUsername: String) {
        val adminIds = authService.listAdminUserIds()
        for (adminId in adminIds) {
            repository.insert(
                UserNotification(
                    id = "",
                    userId = adminId,
                    type = UserNotificationType.ACCOUNT_PENDING_APPROVAL,
                    teamId = null,
                    teamName = null,
                    title = "New user registration",
                    message = "User '${sanitize(newUsername)}' registered and is pending approval.",
                    targetType = "admin-users",
                    targetId = "pending",
                )
            )
        }
    }

    /**
     * Housekeeping: delete notifications older than the cutoff and enforce per-user cap.
     */
    suspend fun cleanup(maxAgeDays: Int = 30, perUserCap: Int = 500) {
        try {
            val cutoff = kotlin.time.Clock.System.now() - maxAgeDays.days
            val deleted = repository.deleteOlderThan(cutoff)
            if (deleted > 0) {
                log.info("Cleaned up {} old notifications", deleted)
            }

            // Enforce per-user cap
            val userIds = repository.findUserIdsWithNotifications()
            for (userId in userIds) {
                val excess = repository.deleteExcessPerUser(userId, perUserCap)
                if (excess > 0) {
                    log.info("Trimmed {} excess notifications for user {}", excess, userId)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            log.error("Notification cleanup failed", e)
        }
    }

    companion object {
        /** Strip characters that could cause rendering issues in notification strings. */
        fun sanitize(value: String): String {
            return value.replace(Regex("[<>\\[\\]()]"), "").trim()
        }
    }
}
