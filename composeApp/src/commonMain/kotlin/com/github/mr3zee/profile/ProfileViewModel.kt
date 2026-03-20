package com.github.mr3zee.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.mr3zee.api.AuthApiClient
import com.github.mr3zee.api.UserInfo
import com.github.mr3zee.api.toUiMessage
import com.github.mr3zee.util.UiMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class ProfileViewModel(
    private val authApiClient: AuthApiClient,
) : ViewModel() {

    private val _userInfo = MutableStateFlow<UserInfo?>(null)
    val userInfo: StateFlow<UserInfo?> = _userInfo

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _error = MutableStateFlow<UiMessage?>(null)
    val error: StateFlow<UiMessage?> = _error

    private val _successMessage = MutableStateFlow<UiMessage?>(null)
    val successMessage: StateFlow<UiMessage?> = _successMessage

    private val _usernameChangeSuccess = MutableStateFlow(false)
    val usernameChangeSuccess: StateFlow<Boolean> = _usernameChangeSuccess

    private val _passwordChangeSuccess = MutableStateFlow(false)
    val passwordChangeSuccess: StateFlow<Boolean> = _passwordChangeSuccess

    /** Callback set from App.kt to propagate username changes to AuthViewModel. */
    var onUsernameChanged: ((UserInfo) -> Unit)? = null

    init {
        // Initial load — AppNavigation also calls loadProfile() on each navigation to refresh
        loadProfile()
    }

    fun loadProfile() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val info = authApiClient.me()
                _userInfo.value = info
            } catch (e: Exception) {
                _error.value = e.toUiMessage()
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun changeUsername(newUsername: String, currentPassword: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val updatedInfo = authApiClient.changeUsername(newUsername, currentPassword)
                _userInfo.value = updatedInfo
                _successMessage.value = UiMessage.UsernameChanged
                _usernameChangeSuccess.value = true
                onUsernameChanged?.invoke(updatedInfo)
            } catch (e: Exception) {
                _error.value = e.toUiMessage()
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun changePassword(currentPassword: String, newPassword: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                authApiClient.changePassword(currentPassword, newPassword)
                _successMessage.value = UiMessage.PasswordChanged
                _passwordChangeSuccess.value = true
            } catch (e: Exception) {
                _error.value = e.toUiMessage()
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun deleteAccount(confirmUsername: String, currentPassword: String, onDeleted: () -> Unit) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                authApiClient.deleteAccount(confirmUsername, currentPassword)
                onDeleted()
            } catch (e: Exception) {
                _error.value = e.toUiMessage()
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun dismissError() {
        _error.value = null
    }

    fun dismissSuccessMessage() {
        _successMessage.value = null
    }

    fun consumeUsernameChangeSuccess() {
        _usernameChangeSuccess.value = false
    }

    fun consumePasswordChangeSuccess() {
        _passwordChangeSuccess.value = false
    }
}
