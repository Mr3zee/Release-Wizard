package com.github.mr3zee.teams

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.mr3zee.api.PaginationInfo
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

    private val _pagination = MutableStateFlow<PaginationInfo?>(null)
    val pagination: StateFlow<PaginationInfo?> = _pagination

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing

    private val _isManualRefresh = MutableStateFlow(false)
    val isManualRefresh: StateFlow<Boolean> = _isManualRefresh

    private val _refreshError = MutableStateFlow<UiMessage?>(null)
    val refreshError: StateFlow<UiMessage?> = _refreshError

    private val pageSize = 50

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
                _pagination.value = response.pagination
            } catch (e: Exception) {
                _error.value = e.toUiMessage()
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun refresh() {
        if (_isRefreshing.value) return
        viewModelScope.launch {
            _isManualRefresh.value = true
            _isRefreshing.value = true
            _refreshError.value = null
            try {
                val response = apiClient.getAuditLog(teamId, offset = 0, limit = pageSize)
                _events.value = response.events
                _pagination.value = response.pagination
            } catch (e: Exception) {
                _refreshError.value = e.toUiMessage()
            } finally {
                _isRefreshing.value = false
                _isManualRefresh.value = false
            }
        }
    }

    fun dismissRefreshError() {
        _refreshError.value = null
    }

    fun loadMore() {
        if (_isLoadingMore.value || _isRefreshing.value) return
        val nextOffset = _pagination.value?.nextPageOffset() ?: return
        _isLoadingMore.value = true
        viewModelScope.launch {
            try {
                val response = apiClient.getAuditLog(teamId, offset = nextOffset, limit = pageSize)
                _events.value += response.events
                _pagination.value = response.pagination
            } catch (e: Exception) {
                _error.value = e.toUiMessage()
            } finally {
                _isLoadingMore.value = false
            }
        }
    }

    fun dismissError() {
        _error.value = null
    }
}
