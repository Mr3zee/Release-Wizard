package com.github.mr3zee.plugins

import com.github.mr3zee.AuthConfig
import com.github.mr3zee.auth.AuthService
import com.github.mr3zee.auth.UserSession
import com.github.mr3zee.model.UserId
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.sessions.*
import org.koin.ktor.ext.getKoin
import org.slf4j.LoggerFactory
import kotlin.time.Clock

private val log = LoggerFactory.getLogger("com.github.mr3zee.plugins.SessionTtlPlugin")

/**
 * Session TTL & rotation plugin.
 *
 * onCall phase:
 * - Checks `lastAccessedAt + sessionTtlSeconds` against current time.
 *   If expired → clears session (the auth challenge plugin will respond 401).
 * - If session is still valid but past the refresh threshold → updates `lastAccessedAt`.
 *
 * onCallRespond phase:
 * - Sends X-CSRF-Token response header for every authenticated response so
 *   the client always has the current CSRF token. Done in onCallRespond so it
 *   reads the final session state (after route handlers may have created/updated it).
 */
val SessionTtl = createApplicationPlugin(name = "SessionTtl") {
    val authConfig = application.getKoin().get<AuthConfig>()
    val authService = application.getKoin().get<AuthService>()
    val ttlMillis = authConfig.sessionTtlSeconds.coerceIn(0, 31536000) * 1000  // max 1 year
    val refreshThresholdMillis = authConfig.sessionRefreshThresholdSeconds.coerceIn(0, 31536000) * 1000
    // AUTH-M5: Absolute session lifetime — sessions older than this are invalidated regardless of activity
    val absoluteLifetimeMillis = authConfig.absoluteSessionLifetimeSeconds.coerceIn(0, 31536000) * 1000

    onCall { call ->
        val session = call.sessions.get<UserSession>() ?: return@onCall

        val nowMillis = Clock.System.now().toEpochMilliseconds()

        // Migrate legacy sessions (created before TTL feature) by setting timestamps now
        if (session.lastAccessedAt == 0L) {
            call.sessions.set(session.copy(lastAccessedAt = nowMillis, createdAt = if (session.createdAt == 0L) nowMillis else session.createdAt))
            return@onCall
        }

        // AUTH-M5: Check absolute session lifetime (e.g. 7 days from creation)
        if (session.createdAt > 0 && nowMillis - session.createdAt > absoluteLifetimeMillis) {
            log.debug("Session absolute lifetime exceeded for user '{}' (age {}ms > max {}ms)", session.username, nowMillis - session.createdAt, absoluteLifetimeMillis)
            call.sessions.clear<UserSession>()
            return@onCall
        }

        if (nowMillis - session.lastAccessedAt > ttlMillis) {
            // Session expired — clear it; the auth challenge will emit 401
            log.debug("Session expired for user '{}' (idle {}ms > ttl {}ms)", session.username, nowMillis - session.lastAccessedAt, ttlMillis)
            call.sessions.clear<UserSession>()
            return@onCall
        }

        // Refresh session if within refresh threshold window
        if (nowMillis - session.lastAccessedAt > refreshThresholdMillis) {
            // Re-validate role from DB to pick up admin demotions / promotions
            val currentUser = authService.getUserById(UserId(session.userId))
            val updatedRole = currentUser?.role ?: session.role
            call.sessions.set(session.copy(lastAccessedAt = nowMillis, role = updatedRole))
        }
    }

    onCallRespond { call, _ ->
        // Read the final session state (after route handlers may have created/updated it)
        val session = call.sessions.get<UserSession>() ?: return@onCallRespond
        if (session.csrfToken.isNotEmpty()) {
            call.response.header(CSRF_TOKEN_HEADER, session.csrfToken)
        }
        call.response.header("Cache-Control", "no-store")
        call.response.header("Vary", "Cookie")
    }
}
