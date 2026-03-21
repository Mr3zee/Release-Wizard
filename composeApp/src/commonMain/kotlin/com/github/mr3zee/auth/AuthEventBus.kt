package com.github.mr3zee.auth

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

sealed class AuthEvent {
    data object SessionExpired : AuthEvent()
}

object AuthEventBus {
    private val _events = MutableSharedFlow<AuthEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<AuthEvent> = _events.asSharedFlow()

    /**
     * Set to true once the initial auth check completes (regardless of result).
     * Prevents spurious "session expired" events from API calls that race
     * ahead of the auth check on first app load.
     */
    var initialAuthCheckComplete = false

    fun emitSessionExpired() {
        if (initialAuthCheckComplete) {
            _events.tryEmit(AuthEvent.SessionExpired)
        }
    }
}
