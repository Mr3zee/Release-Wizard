package com.github.mr3zee.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.mr3zee.api.AuthApiClient
import com.github.mr3zee.api.UserInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class AuthViewModel(
    private val apiClient: AuthApiClient,
) : ViewModel() {

    private val _user = MutableStateFlow<UserInfo?>(null)
    val user: StateFlow<UserInfo?> = _user

    private val _isCheckingSession = MutableStateFlow(true)
    val isCheckingSession: StateFlow<Boolean> = _isCheckingSession

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    fun checkSession() {
        viewModelScope.launch {
            _isCheckingSession.value = true
            _error.value = null
            try {
                _user.value = apiClient.me()
            } catch (_: Exception) {
                _user.value = null
            } finally {
                _isCheckingSession.value = false
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
            } catch (_: Exception) {
                _error.value = "Invalid credentials"
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
            } catch (_: Exception) {
                _error.value = "Registration failed. Username may already be taken."
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

    fun onSessionExpired() {
        _user.value = null
        _error.value = "Session expired. Please log in again."
    }

    fun dismissError() {
        _error.value = null
    }
}
