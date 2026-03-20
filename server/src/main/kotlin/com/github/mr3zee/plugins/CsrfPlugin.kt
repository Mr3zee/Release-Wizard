package com.github.mr3zee.plugins

import com.github.mr3zee.ForbiddenException
import com.github.mr3zee.api.ApiRoutes
import com.github.mr3zee.auth.UserSession
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.path
import io.ktor.server.sessions.*
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("com.github.mr3zee.plugins.CsrfPlugin")

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
        if (path.startsWith(ApiRoutes.Webhooks.BASE + "/") || path.startsWith(ApiRoutes.Triggers.webhook(""))) return@onCall

        // Exempt auth endpoints (login, register) — no session exists yet
        if (path == ApiRoutes.Auth.LOGIN || path == ApiRoutes.Auth.REGISTER) return@onCall

        // Only enforce CSRF if there is an active session
        val session = call.sessions.get<UserSession>() ?: return@onCall

        // AUTH-H6: Fail closed — reject requests if session has no CSRF token
        if (session.csrfToken.isEmpty()) {
            log.warn("CSRF token missing from session for user '{}' — failing closed", session.username)
            throw ForbiddenException("Session CSRF token not initialized")
        }

        // INFRA-H2: Constant-time comparison even when header token is null/missing/wrong-length.
        // Always compare equal-length byte arrays to prevent length-based timing oracle.
        val sessionBytes = session.csrfToken.toByteArray(Charsets.UTF_8)
        val headerToken = call.request.headers[CSRF_TOKEN_HEADER]
        val rawHeaderBytes = headerToken?.toByteArray(Charsets.UTF_8) ?: ByteArray(0)
        // Pad/truncate to session token length for constant-time comparison regardless of input length
        val headerBytes = ByteArray(sessionBytes.size)
        rawHeaderBytes.copyInto(headerBytes, 0, 0, minOf(rawHeaderBytes.size, headerBytes.size))
        // If lengths differ, the padded comparison will fail but in constant time
        val lengthMatch = rawHeaderBytes.size == sessionBytes.size
        if (!lengthMatch || !java.security.MessageDigest.isEqual(headerBytes, sessionBytes)) {
            log.warn("CSRF validation failed for {} {} (user={})", call.request.local.method.value, path, session.username)
            throw ForbiddenException("Invalid or missing CSRF token")
        }
    }
}
