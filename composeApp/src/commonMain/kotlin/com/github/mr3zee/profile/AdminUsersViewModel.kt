package com.github.mr3zee.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.mr3zee.api.AuthApiClient
import com.github.mr3zee.api.DeleteUserPreCheckResponse
import com.github.mr3zee.api.PatApiClient
import com.github.mr3zee.api.PatInfo
import com.github.mr3zee.api.toUiMessage
import com.github.mr3zee.model.User
import com.github.mr3zee.util.UiMessage
import com.github.mr3zee.util.copyToClipboard
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed class AdminSuccessEvent {
    data class UserApproved(val username: String) : AdminSuccessEvent()
    data object TokenRevoked : AdminSuccessEvent()
    data class UserDeleted(val username: String) : AdminSuccessEvent()
}

class AdminUsersViewModel(
    private val authApiClient: AuthApiClient,
    private val patApiClient: PatApiClient,
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

    private val _successEvent = MutableStateFlow<AdminSuccessEvent?>(null)
    val successEvent: StateFlow<AdminSuccessEvent?> = _successEvent

    private val _deletePreCheck = MutableStateFlow<Pair<String, DeleteUserPreCheckResponse>?>(null)
    val deletePreCheck: StateFlow<Pair<String, DeleteUserPreCheckResponse>?> = _deletePreCheck

    private val _isDeleting = MutableStateFlow(false)
    val isDeleting: StateFlow<Boolean> = _isDeleting

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
                // Sort: pending first, then approved, both by createdAt
                _users.value = response.sortedWith(
                    compareBy<User> { it.approved }.thenBy { it.createdAt ?: 0L }
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _error.value = e.toUiMessage()
            } finally {
                _isLoading.value = false
                _isRefreshing.value = false
                _isManualRefresh.value = false
            }
        }
    }

    fun approveUser(userId: String, username: String) {
        viewModelScope.launch {
            _error.value = null
            try {
                authApiClient.approveUser(userId)
                _successEvent.value = AdminSuccessEvent.UserApproved(username)
                loadUsers()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _error.value = e.toUiMessage()
            }
        }
    }

    fun rejectUser(userId: String) {
        viewModelScope.launch {
            _error.value = null
            try {
                authApiClient.rejectUser(userId)
                loadUsers()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _error.value = e.toUiMessage()
            }
        }
    }

    fun fetchDeletePreCheck(userId: String) {
        viewModelScope.launch {
            _error.value = null
            try {
                val result = authApiClient.getDeletePreCheck(userId)
                _deletePreCheck.value = userId to result
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _deletePreCheck.value = null
                _error.value = e.toUiMessage()
            }
        }
    }

    fun deleteUser(userId: String, username: String, confirmTeamLeadTransfer: Boolean = false) {
        viewModelScope.launch {
            _error.value = null
            _isDeleting.value = true
            try {
                authApiClient.deleteUser(userId, confirmTeamLeadTransfer)
                _deletePreCheck.value = null
                _successEvent.value = AdminSuccessEvent.UserDeleted(username)
                loadUsers()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _error.value = e.toUiMessage()
            } finally {
                _isDeleting.value = false
            }
        }
    }

    fun dismissDeletePreCheck() {
        _deletePreCheck.value = null
    }

    fun dismissSuccess() {
        _successEvent.value = null
    }

    fun generateResetLink(userId: String) {
        viewModelScope.launch {
            _error.value = null
            try {
                val response = authApiClient.generatePasswordResetLink(userId)
                _generatedLinks.value += (userId to response.resetUrl)
                // Auto-copy to clipboard
                copyToClipboard(response.resetUrl)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _error.value = e.toUiMessage()
            }
        }
    }

    fun dismissError() {
        _error.value = null
    }

    // --- Admin PAT management ---

    private val _userTokens = MutableStateFlow<Map<String, List<PatInfo>?>>(emptyMap())
    val userTokens: StateFlow<Map<String, List<PatInfo>?>> = _userTokens

    private val _loadingTokensFor = MutableStateFlow<Set<String>>(emptySet())
    val loadingTokensFor: StateFlow<Set<String>> = _loadingTokensFor

    fun loadUserTokens(userId: String) {
        viewModelScope.launch {
            _loadingTokensFor.update { it + userId }
            try {
                val tokens = patApiClient.listUserTokens(userId)
                _userTokens.update { it + (userId to tokens) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _error.value = e.toUiMessage()
            } finally {
                _loadingTokensFor.update { it - userId }
            }
        }
    }

    fun revokeUserToken(userId: String, tokenId: String) {
        viewModelScope.launch {
            _error.value = null
            try {
                patApiClient.revokeUserToken(userId, tokenId)
                _successEvent.value = AdminSuccessEvent.TokenRevoked
                loadUserTokens(userId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _error.value = e.toUiMessage()
            }
        }
    }
}
