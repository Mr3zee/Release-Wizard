package com.github.mr3zee.util

import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.stringResource
import releasewizard.composeapp.generated.resources.*

sealed interface UiMessage {
    // Auth
    data object InvalidCredentials : UiMessage
    data object RegistrationFailed : UiMessage
    data object SessionExpired : UiMessage

    // Errors from ErrorUtils
    data object NotAuthenticated : UiMessage
    data object AccessDenied : UiMessage
    data object NotFound : UiMessage
    data object InvalidRequest : UiMessage
    data object ServerError : UiMessage
    data object CannotConnect : UiMessage
    data object UnknownError : UiMessage

    // Connection test
    data class ConnectionTestSucceeded(val detail: String) : UiMessage
    data class ConnectionTestFailed(val detail: String) : UiMessage

    // Team
    data object JoinRequestSubmitted : UiMessage

    // Editor lock
    data class LockReacquireFailed(val detail: String) : UiMessage

    // Fallback for any string that hasn't been converted yet
    data class Raw(val text: String) : UiMessage
}

@Composable
fun UiMessage.resolve(): String = when (this) {
    is UiMessage.InvalidCredentials -> stringResource(Res.string.error_invalid_credentials)
    is UiMessage.RegistrationFailed -> stringResource(Res.string.error_registration_failed)
    is UiMessage.SessionExpired -> stringResource(Res.string.error_session_expired)
    is UiMessage.NotAuthenticated -> stringResource(Res.string.error_not_authenticated)
    is UiMessage.AccessDenied -> stringResource(Res.string.error_access_denied)
    is UiMessage.NotFound -> stringResource(Res.string.error_not_found)
    is UiMessage.InvalidRequest -> stringResource(Res.string.error_invalid_request)
    is UiMessage.ServerError -> stringResource(Res.string.error_server)
    is UiMessage.CannotConnect -> stringResource(Res.string.error_cannot_connect)
    is UiMessage.UnknownError -> stringResource(Res.string.common_unknown_error)
    is UiMessage.ConnectionTestSucceeded -> stringResource(Res.string.connections_test_succeeded, detail)
    is UiMessage.ConnectionTestFailed -> stringResource(Res.string.connections_test_failed, detail)
    is UiMessage.JoinRequestSubmitted -> stringResource(Res.string.teams_join_request_submitted)
    is UiMessage.LockReacquireFailed -> stringResource(Res.string.editor_lock_reacquire_failed, detail)
    is UiMessage.Raw -> text
}
