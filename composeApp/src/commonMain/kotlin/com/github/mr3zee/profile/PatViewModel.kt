package com.github.mr3zee.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.mr3zee.api.CreatePatResponse
import com.github.mr3zee.api.PatApiClient
import com.github.mr3zee.api.PatInfo
import com.github.mr3zee.api.toUiMessage
import com.github.mr3zee.util.UiMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class PatViewModel(
    private val patApiClient: PatApiClient,
) : ViewModel() {

    private val _tokens = MutableStateFlow<List<PatInfo>?>(null)
    val tokens: StateFlow<List<PatInfo>?> = _tokens

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _error = MutableStateFlow<UiMessage?>(null)
    val error: StateFlow<UiMessage?> = _error

    private val _successMessage = MutableStateFlow<UiMessage?>(null)
    val successMessage: StateFlow<UiMessage?> = _successMessage

    private val _newlyCreatedToken = MutableStateFlow<CreatePatResponse?>(null)
    val newlyCreatedToken: StateFlow<CreatePatResponse?> = _newlyCreatedToken

    /** Track whether a create just succeeded so the UI can close the form */
    private val _createSuccess = MutableStateFlow(false)
    val createSuccess: StateFlow<Boolean> = _createSuccess

    fun loadTokens() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                _tokens.value = patApiClient.listTokens()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _error.value = e.toUiMessage()
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun createToken(name: String, expiresInDays: Int?) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            _createSuccess.value = false
            try {
                val response = patApiClient.createToken(name, expiresInDays)
                _newlyCreatedToken.value = response
                _createSuccess.value = true
                _tokens.value = patApiClient.listTokens()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _error.value = e.toUiMessage()
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun consumeCreateSuccess() {
        _createSuccess.value = false
    }

    fun revokeToken(id: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                patApiClient.revokeToken(id)
                _successMessage.value = UiMessage.Raw("pat_revoked")
                _tokens.value = patApiClient.listTokens()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _error.value = e.toUiMessage()
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun dismissNewlyCreatedToken() {
        _newlyCreatedToken.value = null
    }

    fun dismissError() {
        _error.value = null
    }

    fun dismissSuccess() {
        _successMessage.value = null
    }
}
