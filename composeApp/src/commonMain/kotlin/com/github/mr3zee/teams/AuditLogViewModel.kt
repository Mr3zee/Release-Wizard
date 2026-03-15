package com.github.mr3zee.teams

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.mr3zee.api.TeamApiClient
import com.github.mr3zee.api.toUiMessage
import com.github.mr3zee.model.AuditEvent
import com.github.mr3zee.util.UiMessage
import com.github.mr3zee.model.TeamId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class AuditLogViewModel(
    private val teamId: TeamId,
    private val apiClient: TeamApiClient,
) : ViewModel() {

    private val _events = MutableStateFlow<List<AuditEvent>>(emptyList())
    val events: StateFlow<List<AuditEvent>> = _events

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _error = MutableStateFlow<UiMessage?>(null)
    val error: StateFlow<UiMessage?> = _error

    private val _hasMore = MutableStateFlow(true)
    val hasMore: StateFlow<Boolean> = _hasMore

    private val pageSize = 50
    private var currentOffset = 0

    init {
        loadEvents()
    }

    private fun loadEvents() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val response = apiClient.getAuditLog(teamId, offset = 0, limit = pageSize)
                _events.value = response.events
                currentOffset = response.events.size
                _hasMore.value = response.events.size >= pageSize
            } catch (e: Exception) {
                _error.value = e.toUiMessage()
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun loadMore() {
        viewModelScope.launch {
            try {
                val response = apiClient.getAuditLog(teamId, offset = currentOffset, limit = pageSize)
                _events.value += response.events
                currentOffset += response.events.size
                _hasMore.value = response.events.size >= pageSize
            } catch (e: Exception) {
                _error.value = e.toUiMessage()
            }
        }
    }

    fun dismissError() {
        _error.value = null
    }
}
