package com.github.mr3zee.teams

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.mr3zee.api.TeamApiClient
import com.github.mr3zee.api.toUserMessage
import com.github.mr3zee.model.TeamInvite
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class MyInvitesViewModel(
    private val apiClient: TeamApiClient,
) : ViewModel() {

    private val _invites = MutableStateFlow<List<TeamInvite>>(emptyList())
    val invites: StateFlow<List<TeamInvite>> = _invites

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    init {
        loadInvites()
    }

    fun loadInvites() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val response = apiClient.getMyInvites()
                _invites.value = response.invites
            } catch (e: Exception) {
                _error.value = e.toUserMessage()
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun acceptInvite(inviteId: String, onAccepted: () -> Unit = {}) {
        viewModelScope.launch {
            try {
                apiClient.acceptInvite(inviteId)
                loadInvites()
                onAccepted()
            } catch (e: Exception) {
                _error.value = e.toUserMessage()
            }
        }
    }

    fun declineInvite(inviteId: String) {
        viewModelScope.launch {
            try {
                apiClient.declineInvite(inviteId)
                loadInvites()
            } catch (e: Exception) {
                _error.value = e.toUserMessage()
            }
        }
    }
}
