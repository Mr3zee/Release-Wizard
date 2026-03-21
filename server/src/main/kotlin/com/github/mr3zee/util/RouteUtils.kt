package com.github.mr3zee.util

import com.github.mr3zee.model.ProjectId
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.*
import java.util.UUID

/**
 * Extracts a Bearer UUID token from the Authorization header.
 * Returns null if the header is missing, not a Bearer token, or not a valid UUID.
 */
internal fun ApplicationCall.bearerTokenUuid(): UUID? {
    val authHeader = request.header("Authorization")
    if (authHeader == null || !authHeader.startsWith("Bearer ")) return null
    val tokenStr = authHeader.removePrefix("Bearer ").trim()
    return try {
        UUID.fromString(tokenStr)
    } catch (_: IllegalArgumentException) {
        null
    }
}

internal fun ApplicationCall.requireUuidParam(paramName: String): String {
    val raw = parameters[paramName]
        ?: throw IllegalArgumentException("Missing $paramName")
    try {
        UUID.fromString(raw)
    } catch (_: IllegalArgumentException) {
        throw IllegalArgumentException("Invalid $paramName format")
    }
    return raw
}

internal fun ApplicationCall.requireProjectId(): ProjectId {
    return ProjectId(requireUuidParam("projectId"))
}
