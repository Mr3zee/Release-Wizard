package com.github.mr3zee.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.mr3zee.api.AuthApiClient
import com.github.mr3zee.api.UserInfo
import com.github.mr3zee.api.toUiMessage
import com.github.mr3zee.util.UiMessage
import com.github.mr3zee.util.navigateToExternalUrl
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class AuthViewModel(
    private val apiClient: AuthApiClient,
) : ViewModel() {

    private val _user = MutableStateFlow<UserInfo?>(null)
    val user: StateFlow<UserInfo?> = _user

    /** True once the user has been authenticated at least once in this session. */
    private var hadSession = false

    private val _isCheckingSession = MutableStateFlow(true)
    val isCheckingSession: StateFlow<Boolean> = _isCheckingSession

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _error = MutableStateFlow<UiMessage?>(null)
    val error: StateFlow<UiMessage?> = _error

    fun checkSession() {
        viewModelScope.launch {
            _isCheckingSession.value = true
            _error.value = null
            try {
                val userInfo = apiClient.me()
                _user.value = userInfo
                if (userInfo != null) {
                    hadSession = true
                }
            } catch (_: Exception) {
                _user.value = null
            } finally {
                _isCheckingSession.value = false
                AuthEventBus.initialAuthCheckComplete = true
            }
        }
    }

    /** Refresh user info silently without showing the full-screen loading spinner. */
    fun refreshUser() {
        viewModelScope.launch {
            try {
                val userInfo = apiClient.me()
                _user.value = userInfo
                if (userInfo != null) {
                    hadSession = true
                }
            } catch (_: Exception) {
                // Silently ignore — user stays as-is
            }
        }
    }

    fun login(username: String, password: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val userInfo = apiClient.login(username, password)
                _user.value = userInfo
                hadSession = true
            } catch (e: Exception) {
                _error.value = e.toUiMessage()
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun register(username: String, password: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val userInfo = apiClient.register(username, password)
                _user.value = userInfo
                hadSession = true
            } catch (e: Exception) {
                _error.value = e.toUiMessage()
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            try {
                apiClient.logout()
            } catch (_: Exception) {
                // Ignore logout errors
            }
            _user.value = null
        }
    }

    /**
     * Called after successful account deletion. Clears [hadSession] so that
     * any in-flight 401 responses do not trigger a "Session expired" message,
     * then clears the user state.
     */
    fun onAccountDeleted() {
        hadSession = false
        _user.value = null
    }

    fun onSessionExpired() {
        _user.value = null
        // Only show "session expired" if the user was previously authenticated.
        // Avoids confusing the message on first visit with no prior session.
        if (hadSession) {
            _error.value = UiMessage.SessionExpired
        }
    }

    fun setError(message: UiMessage) {
        _error.value = message
    }

    fun dismissError() {
        _error.value = null
    }

    fun loginWithGoogle() {
        navigateToExternalUrl(apiClient.googleOAuthBrowserUrl())
    }

    fun updateUser(userInfo: UserInfo) {
        _user.value = userInfo
    }
}
