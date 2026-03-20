package com.github.mr3zee.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.mr3zee.api.AuthApiClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class ResetPasswordViewModel(
    private val token: String,
    private val authApiClient: AuthApiClient,
) : ViewModel() {

    private val _isValidating = MutableStateFlow(true)
    val isValidating: StateFlow<Boolean> = _isValidating

    private val _isTokenValid = MutableStateFlow<Boolean?>(null)
    val isTokenValid: StateFlow<Boolean?> = _isTokenValid

    private val _isSubmitting = MutableStateFlow(false)
    val isSubmitting: StateFlow<Boolean> = _isSubmitting

    private val _isComplete = MutableStateFlow(false)
    val isComplete: StateFlow<Boolean> = _isComplete

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    init {
        validateToken()
    }

    private fun validateToken() {
        viewModelScope.launch {
            _isValidating.value = true
            try {
                val valid = authApiClient.validateResetToken(token)
                _isTokenValid.value = valid
            } catch (_: Exception) {
                _isTokenValid.value = false
            } finally {
                _isValidating.value = false
            }
        }
    }

    fun resetPassword(newPassword: String, confirmPassword: String) {
        if (newPassword != confirmPassword) {
            _error.value = "Passwords do not match"
            return
        }
        viewModelScope.launch {
            _isSubmitting.value = true
            _error.value = null
            try {
                authApiClient.resetPassword(token, newPassword)
                _isComplete.value = true
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to reset password"
            } finally {
                _isSubmitting.value = false
            }
        }
    }

    fun dismissError() {
        _error.value = null
    }
}
