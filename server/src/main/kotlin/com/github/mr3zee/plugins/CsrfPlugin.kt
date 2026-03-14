package com.github.mr3zee.plugins

import com.github.mr3zee.ForbiddenException
import com.github.mr3zee.auth.UserSession
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.path
import io.ktor.server.sessions.*

const val CSRF_TOKEN_HEADER = "X-CSRF-Token"

private val MUTATING_METHODS = setOf(HttpMethod.Post, HttpMethod.Put, HttpMethod.Delete, HttpMethod.Patch)

/**
 * CSRF protection plugin. Validates X-CSRF-Token header on mutating HTTP methods
 * for authenticated sessions.
 *
 * Exempt:
 * - WebSocket upgrade requests
 * - Unauthenticated endpoints (no session cookie)
 * - Webhook/trigger endpoints that use their own auth (Bearer token, HMAC)
 */
val CsrfProtection = createApplicationPlugin(name = "CsrfProtection") {
    onCall { call ->
        // Only check mutating methods
        if (call.request.local.method !in MUTATING_METHODS) return@onCall

        // Exempt WebSocket upgrade requests
        val upgradeHeader = call.request.headers[HttpHeaders.Upgrade]
        if (upgradeHeader?.equals("websocket", ignoreCase = true) == true) return@onCall

        // Exempt unauthenticated webhook/trigger endpoints
        val path = call.request.path()
        // todo claude: ApiRoutes not used
        if (path.startsWith("/api/v1/webhooks/") || path.startsWith("/api/v1/triggers/webhook/")) return@onCall

        // todo claude: ApiRoutes not used
        // Exempt auth endpoints (login, register) — no session exists yet
        if (path == "/api/v1/auth/login" || path == "/api/v1/auth/register") return@onCall

        // Only enforce CSRF if there is an active session
        val session = call.sessions.get<UserSession>() ?: return@onCall
        if (session.csrfToken.isEmpty()) return@onCall

        val headerToken = call.request.headers[CSRF_TOKEN_HEADER]
        if (!java.security.MessageDigest.isEqual(
                headerToken?.toByteArray(Charsets.UTF_8) ?: ByteArray(0),
                session.csrfToken.toByteArray(Charsets.UTF_8)
            )) {
            throw ForbiddenException("Invalid or missing CSRF token")
        }
    }
}
