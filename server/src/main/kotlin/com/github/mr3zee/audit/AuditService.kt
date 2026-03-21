package com.github.mr3zee.audit

import com.github.mr3zee.auth.UserSession
import com.github.mr3zee.model.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory

class AuditService(
    private val repository: AuditRepository,
    private val scope: CoroutineScope,
) {
    private val logger = LoggerFactory.getLogger(AuditService::class.java)

    fun log(
        teamId: TeamId?,
        session: UserSession,
        action: AuditAction,
        targetType: AuditTargetType,
        targetId: String,
        details: String = "",
    ) {
        insertAsync(
            teamId = teamId,
            actorUserId = UserId(session.userId),
            actorUsername = session.username,
            action = action,
            targetType = targetType,
            targetId = targetId,
            details = details,
        )
    }

    /**
     * SCHED-M6: Log an audit event for system-initiated actions (e.g., scheduled releases)
     * where there is no user session.
     */
    fun logSystem(
        teamId: TeamId?,
        actorDescription: String,
        action: AuditAction,
        targetType: AuditTargetType,
        targetId: String,
        details: String = "",
    ) {
        insertAsync(
            teamId = teamId,
            actorUserId = null,
            actorUsername = "[system:$actorDescription]",
            action = action,
            targetType = targetType,
            targetId = targetId,
            details = details,
        )
    }

    // todo claude: unused
    suspend fun logSync(
        teamId: TeamId?,
        session: UserSession,
        action: AuditAction,
        targetType: AuditTargetType,
        targetId: String,
        details: String = "",
    ) {
        val sanitizedDetails = sanitize(details, MAX_DETAILS_LENGTH)
        val sanitizedTargetId = sanitize(targetId, MAX_TARGET_ID_LENGTH)
        repository.insert(
            AuditEvent(
                id = "",
                teamId = teamId,
                actorUserId = UserId(session.userId),
                actorUsername = sanitize(session.username, MAX_USERNAME_LENGTH),
                action = action,
                targetType = targetType,
                targetId = sanitizedTargetId,
                details = sanitizedDetails,
            )
        )
    }

    private fun insertAsync(
        teamId: TeamId?,
        actorUserId: UserId?,
        actorUsername: String,
        action: AuditAction,
        targetType: AuditTargetType,
        targetId: String,
        details: String,
    ) {
        // TAG-M1: Sanitize user-controlled data to prevent log injection
        val sanitizedDetails = sanitize(details, MAX_DETAILS_LENGTH)
        val sanitizedTargetId = sanitize(targetId, MAX_TARGET_ID_LENGTH)
        val sanitizedUsername = sanitize(actorUsername, MAX_USERNAME_LENGTH)
        scope.launch {
            try {
                repository.insert(
                    AuditEvent(
                        id = "",
                        teamId = teamId,
                        actorUserId = actorUserId,
                        actorUsername = sanitizedUsername,
                        action = action,
                        targetType = targetType,
                        targetId = sanitizedTargetId,
                        details = sanitizedDetails,
                    )
                )
            } catch (e: Exception) {
                logger.error("Failed to write audit event: action={}, target={}", action, sanitizedTargetId, e)
            }
        }
    }

    companion object {
        private const val MAX_DETAILS_LENGTH = 2000
        private const val MAX_TARGET_ID_LENGTH = 255
        private const val MAX_USERNAME_LENGTH = 100

        /**
         * TAG-M1: Remove control characters and cap length to prevent log injection
         * and oversized payloads from user-controlled inputs.
         */
        internal fun sanitize(input: String, maxLength: Int = MAX_DETAILS_LENGTH): String {
            val cleaned = input
                .replace(Regex("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\x7F]"), "")
                .replace("\r\n", " ")
                .replace("\n", " ")
                .replace("\r", " ")
            return cleaned.take(maxLength)
        }
    }
}
