package com.github.mr3zee.api

import com.github.mr3zee.AppJson
import io.ktor.client.plugins.*
import io.ktor.client.statement.*

/**
 * Try to parse lock conflict response from a 409 response.
 * Returns null if parsing fails or status is not 409.
 */
suspend fun ClientRequestException.parseLockConflict(): ProjectLockConflictResponse? {
    if (response.status.value != 409) return null
    return try {
        val body = response.bodyAsText()
        AppJson.decodeFromString<ProjectLockConflictResponse>(body)
    } catch (_: Exception) {
        null
    }
}

/**
 * Try to parse ErrorResponse from the server's JSON response body.
 * Returns null if parsing fails.
 */
suspend fun ClientRequestException.parseError(): ErrorResponse? {
    return try {
        val body = response.bodyAsText()
        AppJson.decodeFromString<ErrorResponse>(body)
    } catch (_: Exception) {
        null
    }
}

/**
 * Extract a user-friendly error message from an exception.
 * For server errors (ClientRequestException), tries to parse ErrorResponse first.
 * Falls back to category-based messages.
 */
suspend fun Exception.toUserMessage(): String {
    return when (this) {
        is ClientRequestException -> {
            val errorResponse = parseError()
            errorResponse?.error ?: when (response.status.value) {
                401 -> "Not authenticated"
                403 -> "Access denied"
                404 -> "Not found"
                in 400..499 -> "Invalid request"
                in 500..599 -> "Server error"
                else -> message
            }
        }
        is ServerResponseException -> "Server error"
        else -> {
            val msg = message ?: ""
            if (msg.contains("connect", ignoreCase = true) || msg.contains("refused", ignoreCase = true)) {
                "Cannot connect to server"
            } else {
                message ?: "Unknown error"
            }
        }
    }
}
