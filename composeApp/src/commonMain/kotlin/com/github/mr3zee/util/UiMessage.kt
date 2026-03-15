package com.github.mr3zee.util

import androidx.compose.runtime.Composable
import com.github.mr3zee.i18n.packStringResource
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
    is UiMessage.InvalidCredentials -> packStringResource(Res.string.error_invalid_credentials)
    is UiMessage.RegistrationFailed -> packStringResource(Res.string.error_registration_failed)
    is UiMessage.SessionExpired -> packStringResource(Res.string.error_session_expired)
    is UiMessage.NotAuthenticated -> packStringResource(Res.string.error_not_authenticated)
    is UiMessage.AccessDenied -> packStringResource(Res.string.error_access_denied)
    is UiMessage.NotFound -> packStringResource(Res.string.error_not_found)
    is UiMessage.InvalidRequest -> packStringResource(Res.string.error_invalid_request)
    is UiMessage.ServerError -> packStringResource(Res.string.error_server)
    is UiMessage.CannotConnect -> packStringResource(Res.string.error_cannot_connect)
    is UiMessage.UnknownError -> packStringResource(Res.string.common_unknown_error)
    is UiMessage.ConnectionTestSucceeded -> packStringResource(Res.string.connections_test_succeeded, detail)
    is UiMessage.ConnectionTestFailed -> packStringResource(Res.string.connections_test_failed, detail)
    is UiMessage.JoinRequestSubmitted -> packStringResource(Res.string.teams_join_request_submitted)
    is UiMessage.LockReacquireFailed -> packStringResource(Res.string.editor_lock_reacquire_failed, detail)
    is UiMessage.Raw -> text
}
