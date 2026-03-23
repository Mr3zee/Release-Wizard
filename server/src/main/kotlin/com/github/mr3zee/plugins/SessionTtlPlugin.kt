package com.github.mr3zee.plugins

import com.github.mr3zee.AuthConfig
import com.github.mr3zee.auth.AuthService
import com.github.mr3zee.auth.UserSession
import com.github.mr3zee.model.ClientType
import com.github.mr3zee.model.UserId
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.sessions.*
import org.koin.ktor.ext.getKoin
import org.slf4j.LoggerFactory
import kotlin.time.Clock

private val log = LoggerFactory.getLogger("com.github.mr3zee.plugins.SessionTtlPlugin")

/**
 * Session TTL & rotation plugin.
 *
 * Applies per-client-type TTLs: browser sessions (30 days default),
 * desktop sessions (1 year default).
 *
 * onCall phase:
 * - Checks `lastAccessedAt + ttl` against current time (TTL based on session's clientType).
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
    val maxTtlSeconds = 63_072_000L // 2 years ceiling
    val browserTtlMillis = authConfig.browserSessionTtlSeconds.coerceIn(0, maxTtlSeconds) * 1000
    val desktopTtlMillis = authConfig.desktopSessionTtlSeconds.coerceIn(0, maxTtlSeconds) * 1000
    val refreshThresholdMillis = authConfig.sessionRefreshThresholdSeconds.coerceIn(0, maxTtlSeconds) * 1000
    val browserAbsoluteLifetimeMillis = authConfig.browserAbsoluteLifetimeSeconds.coerceIn(0, maxTtlSeconds) * 1000
    val desktopAbsoluteLifetimeMillis = authConfig.desktopAbsoluteLifetimeSeconds.coerceIn(0, maxTtlSeconds) * 1000

    onCall { call ->
        var session = call.sessions.get<UserSession>() ?: return@onCall

        val nowMillis = Clock.System.now().toEpochMilliseconds()

        // Migrate legacy sessions (created before per-client TTL feature) by setting timestamps
        if (session.lastAccessedAt == 0L || session.createdAt == 0L) {
            session = session.copy(
                lastAccessedAt = if (session.lastAccessedAt == 0L) nowMillis else session.lastAccessedAt,
                createdAt = if (session.createdAt == 0L) nowMillis else session.createdAt,
            )
            call.sessions.set(session)
            // Fall through to TTL checks — migrated sessions are still subject to expiry
        }

        val ttlMillis = when (session.clientType) {
            ClientType.BROWSER -> browserTtlMillis
            ClientType.DESKTOP -> desktopTtlMillis
        }
        val absoluteLifetimeMillis = when (session.clientType) {
            ClientType.BROWSER -> browserAbsoluteLifetimeMillis
            ClientType.DESKTOP -> desktopAbsoluteLifetimeMillis
        }

        // Check absolute session lifetime (>= so that TTL=0 means "expire immediately")
        if (nowMillis - session.createdAt >= absoluteLifetimeMillis) {
            log.debug("Session absolute lifetime exceeded for user '{}' (age {}ms >= max {}ms)", session.username, nowMillis - session.createdAt, absoluteLifetimeMillis)
            call.sessions.clear<UserSession>()
            return@onCall
        }

        if (nowMillis - session.lastAccessedAt >= ttlMillis) {
            // Session expired — clear it; the auth challenge will emit 401
            log.debug("Session expired for user '{}' (idle {}ms >= ttl {}ms)", session.username, nowMillis - session.lastAccessedAt, ttlMillis)
            call.sessions.clear<UserSession>()
            return@onCall
        }

        // Refresh session if within refresh threshold window
        if (nowMillis - session.lastAccessedAt > refreshThresholdMillis) {
            // Re-validate role, username, and password-change status from DB
            val refreshInfo = authService.getSessionRefreshInfo(UserId(session.userId))
            if (refreshInfo == null) {
                // User deleted — clear session
                log.debug("User '{}' no longer exists, clearing session", session.username)
                call.sessions.clear<UserSession>()
                return@onCall
            }

            // Invalidate sessions created before the last password change
            val pwChangedAt = refreshInfo.passwordChangedAtMillis
            if (pwChangedAt != null && session.createdAt < pwChangedAt) {
                log.debug("Session for '{}' predates password change, clearing", session.username)
                call.sessions.clear<UserSession>()
                return@onCall
            }

            call.sessions.set(
                session.copy(
                    lastAccessedAt = nowMillis,
                    role = refreshInfo.role,
                    username = refreshInfo.username,
                    approved = refreshInfo.approved,
                )
            )
        }
    }

    onCallRespond { call, _ ->
        // Read the final session state (after route handlers may have created/updated it)
        // Sessions may not be available when responding from engine error handlers or
        // StatusPages that fire before the Sessions pipeline phase — skip gracefully.
        val session = try {
            call.sessions.get<UserSession>()
        } catch (_: SessionNotYetConfiguredException) {
            null
        } ?: return@onCallRespond
        // Only add cache/CSRF headers for API responses — static assets are managed by CachingHeaders plugin
        if (!call.request.path().startsWith("/api/")) return@onCallRespond
        if (session.csrfToken.isNotEmpty()) {
            call.response.header(CSRF_TOKEN_HEADER, session.csrfToken)
        }
        call.response.header("Cache-Control", "no-store")
        call.response.header("Vary", "Cookie")
    }
}
