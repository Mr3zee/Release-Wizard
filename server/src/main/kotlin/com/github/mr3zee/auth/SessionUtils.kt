package com.github.mr3zee.auth

import io.ktor.server.application.ApplicationCall
import io.ktor.server.sessions.get
import io.ktor.server.sessions.sessions
import java.security.SecureRandom

/**
 * Extracts the UserSession from an authenticated route.
 * Should only be called within authenticate("session-auth") blocks.
 */
fun ApplicationCall.userSession(): UserSession =
    sessions.get<UserSession>() ?: error("UserSession not found — this should only be called within authenticated routes")

private val csrfRandom = SecureRandom()

/**
 * Generates a cryptographically random CSRF token (32-byte hex string).
 * Used at login, registration, and OAuth callback to populate UserSession.csrfToken.
 */
fun generateCsrfToken(): String {
    val bytes = ByteArray(32)
    csrfRandom.nextBytes(bytes)
    return bytes.joinToString("") { "%02x".format(it) }
}
