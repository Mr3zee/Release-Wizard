package com.github.mr3zee.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.mr3zee.api.AuthApiClient
import com.github.mr3zee.api.toUiMessage
import com.github.mr3zee.util.UiMessage
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

    private val _error = MutableStateFlow<UiMessage?>(null)
    val error: StateFlow<UiMessage?> = _error

    /** Client-side validation error (passwords don't match) — separate from server errors. */
    private val _validationError = MutableStateFlow<String?>(null)
    val validationError: StateFlow<String?> = _validationError

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
            _validationError.value = "Passwords do not match"
            return
        }
        _validationError.value = null
        viewModelScope.launch {
            _isSubmitting.value = true
            _error.value = null
            try {
                authApiClient.resetPassword(token, newPassword)
                _isComplete.value = true
            } catch (e: Exception) {
                _error.value = e.toUiMessage()
            } finally {
                _isSubmitting.value = false
            }
        }
    }

    fun dismissError() {
        _error.value = null
        _validationError.value = null
    }
}
