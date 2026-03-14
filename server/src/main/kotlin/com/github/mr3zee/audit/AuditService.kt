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
        scope.launch {
            try {
                repository.insert(
                    AuditEvent(
                        id = "",
                        teamId = teamId,
                        actorUserId = UserId(session.userId),
                        actorUsername = session.username,
                        action = action,
                        targetType = targetType,
                        targetId = targetId,
                        details = details,
                    )
                )
            } catch (e: Exception) {
                logger.error("Failed to write audit event: action={}, target={}", action, targetId, e)
            }
        }
    }
}
