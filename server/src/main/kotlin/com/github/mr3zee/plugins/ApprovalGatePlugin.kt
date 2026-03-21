package com.github.mr3zee.plugins

import com.github.mr3zee.NotApprovedException
import com.github.mr3zee.api.ApiRoutes
import com.github.mr3zee.auth.AuthService
import com.github.mr3zee.auth.UserSession
import com.github.mr3zee.model.UserId
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.sessions.*
import org.koin.ktor.ext.getKoin
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("com.github.mr3zee.plugins.ApprovalGatePlugin")

/**
 * Approval gate plugin. Blocks unapproved users from accessing protected endpoints.
 *
 * Exempt paths:
 * - GET /api/v1/auth/me — so the client can check approval status
 * - POST /api/v1/auth/logout — so the user can sign out
 * - All public auth endpoints (login, register, password policy, password reset) — these
 *   are outside authenticate("session-auth") but may have a session cookie present
 *
 * When session.approved == false, re-reads from DB to close the stale-session window
 * (admin may have approved the user since the last session refresh). If still not approved,
 * throws NotApprovedException which is caught by StatusPages and returns 403.
 */
val ApprovalGate = createApplicationPlugin(name = "ApprovalGate") {
    val authService = application.getKoin().get<AuthService>()

    onCall { call ->
        val session = call.sessions.get<UserSession>() ?: return@onCall

        val path = call.request.path()

        if (isExemptFromApprovalGate(path)) return@onCall

        if (!session.approved) {
            // Re-read from DB to close stale-session window — approval may have happened
            // since the last session refresh
            val currentlyApproved = authService.isApproved(UserId(session.userId))
            if (currentlyApproved) {
                call.sessions.set(session.copy(approved = true))
                return@onCall
            }
            log.debug("Unapproved user '{}' blocked from {}", session.username, path)
            throw NotApprovedException()
        }
    }
}

/** Paths exempt from the approval gate — public auth endpoints + status check + logout. */
private val EXEMPT_PATHS = setOf(
    ApiRoutes.Auth.ME,
    ApiRoutes.Auth.LOGOUT,
    ApiRoutes.Auth.LOGIN,
    ApiRoutes.Auth.REGISTER,
    ApiRoutes.Auth.PASSWORD_POLICY,
    ApiRoutes.Auth.RESET_PASSWORD,
    ApiRoutes.Auth.VALIDATE_RESET_TOKEN,
)

private fun isExemptFromApprovalGate(path: String): Boolean =
    path in EXEMPT_PATHS
