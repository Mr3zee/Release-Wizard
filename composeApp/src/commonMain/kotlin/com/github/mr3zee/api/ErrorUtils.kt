package com.github.mr3zee.api

import com.github.mr3zee.AppJson
import com.github.mr3zee.util.UiMessage
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
 * Convert an exception to a [UiMessage] for localized error display.
 * For server errors (ClientRequestException), tries to parse ErrorResponse first.
 * If a server-provided error message exists, wraps it in [UiMessage.Raw].
 * Otherwise maps HTTP status to a typed [UiMessage] variant.
 */
suspend fun Exception.toUiMessage(): UiMessage {
    return when (this) {
        is ClientRequestException -> {
            val errorResponse = parseError()
            if (errorResponse != null) {
                when (errorResponse.code) {
                    "INVALID_CREDENTIALS" -> UiMessage.InvalidCredentials
                    "REGISTRATION_FAILED" -> UiMessage.RegistrationFailed
                    "USERNAME_TAKEN" -> UiMessage.UsernameTaken
                    else -> if (errorResponse.error.isNotBlank()) {
                        UiMessage.Raw(errorResponse.error)
                    } else {
                        UiMessage.ServerError
                    }
                }
            } else {
                when (response.status.value) {
                    401 -> UiMessage.NotAuthenticated
                    403 -> UiMessage.AccessDenied
                    404 -> UiMessage.NotFound
                    in 400..499 -> UiMessage.InvalidRequest
                    in 500..599 -> UiMessage.ServerError
                    else -> UiMessage.UnknownError
                }
            }
        }
        is ServerResponseException -> UiMessage.ServerError
        else -> {
            val msg = message ?: ""
            if (msg.contains("connect", ignoreCase = true) || msg.contains("refused", ignoreCase = true)) {
                UiMessage.CannotConnect
            } else {
                UiMessage.UnknownError
            }
        }
    }
}
