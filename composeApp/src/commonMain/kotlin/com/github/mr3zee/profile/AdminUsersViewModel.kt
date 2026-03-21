package com.github.mr3zee.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.mr3zee.api.AuthApiClient
import com.github.mr3zee.api.toUiMessage
import com.github.mr3zee.model.User
import com.github.mr3zee.util.UiMessage
import com.github.mr3zee.util.copyToClipboard
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class AdminUsersViewModel(
    private val authApiClient: AuthApiClient,
) : ViewModel() {

    private val _users = MutableStateFlow<List<User>?>(null)
    val users: StateFlow<List<User>?> = _users

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing

    private val _isManualRefresh = MutableStateFlow(false)
    val isManualRefresh: StateFlow<Boolean> = _isManualRefresh

    private val _error = MutableStateFlow<UiMessage?>(null)
    val error: StateFlow<UiMessage?> = _error

    private val _generatedLinks = MutableStateFlow<Map<String, String>>(emptyMap())
    val generatedLinks: StateFlow<Map<String, String>> = _generatedLinks

    init {
        loadUsers()
    }

    fun loadUsers() {
        viewModelScope.launch {
            val isFirstLoad = _users.value == null
            if (isFirstLoad) {
                _isLoading.value = true
            } else {
                _isRefreshing.value = true
                _isManualRefresh.value = true
            }
            _error.value = null
            try {
                val response = authApiClient.getUsers()
                _users.value = response
            } catch (e: Exception) {
                _error.value = e.toUiMessage()
            } finally {
                _isLoading.value = false
                _isRefreshing.value = false
                _isManualRefresh.value = false
            }
        }
    }

    fun generateResetLink(userId: String) {
        viewModelScope.launch {
            _error.value = null
            try {
                val response = authApiClient.generatePasswordResetLink(userId)
                _generatedLinks.value += (userId to response.resetUrl)
                // Auto-copy to clipboard
                copyToClipboard(response.resetUrl)
            } catch (e: Exception) {
                _error.value = e.toUiMessage()
            }
        }
    }

    fun dismissError() {
        _error.value = null
    }
}
