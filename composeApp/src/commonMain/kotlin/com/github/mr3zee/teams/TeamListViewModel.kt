package com.github.mr3zee.teams

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.mr3zee.api.CreateTeamRequest
import com.github.mr3zee.api.TeamApiClient
import com.github.mr3zee.api.TeamResponse
import com.github.mr3zee.api.toUiMessage
import com.github.mr3zee.model.TeamId
import com.github.mr3zee.util.UiMessage
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

@OptIn(FlowPreview::class)
class TeamListViewModel(
    private val apiClient: TeamApiClient,
) : ViewModel() {

    private val _teams = MutableStateFlow<List<TeamResponse>>(emptyList())
    val teams: StateFlow<List<TeamResponse>> = _teams

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _error = MutableStateFlow<UiMessage?>(null)
    val error: StateFlow<UiMessage?> = _error

    private val _message = MutableStateFlow<UiMessage?>(null)
    val message: StateFlow<UiMessage?> = _message

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing

    private val _isManualRefresh = MutableStateFlow(false)
    val isManualRefresh: StateFlow<Boolean> = _isManualRefresh

    private val _refreshError = MutableStateFlow<UiMessage?>(null)
    val refreshError: StateFlow<UiMessage?> = _refreshError

    init {
        viewModelScope.launch {
            _searchQuery.debounce(300.milliseconds).distinctUntilChanged().collectLatest {
                loadTeamsInternal()
            }
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun refresh() {
        if (_isRefreshing.value) return
        viewModelScope.launch {
            _isManualRefresh.value = true
            _refreshError.value = null
            try {
                loadTeamsInternal(silent = true)
            } finally {
                _isManualRefresh.value = false
            }
        }
    }

    fun loadTeams() {
        viewModelScope.launch { loadTeamsInternal() }
    }

    private suspend fun loadTeamsInternal(silent: Boolean = false) {
        if (silent) {
            _isRefreshing.value = true
        } else {
            _isLoading.value = true
            _error.value = null
        }
        try {
            val search = _searchQuery.value.takeIf { it.isNotBlank() }
            val response = apiClient.listTeams(offset = 0, limit = 50, search = search)
            _teams.value = response.teams
            _refreshError.value = null
        } catch (e: Exception) {
            if (silent) {
                _refreshError.value = e.toUiMessage()
            } else {
                _error.value = e.toUiMessage()
            }
        } finally {
            _isLoading.value = false
            _isRefreshing.value = false
        }
    }

    fun createTeam(name: String, description: String = "", onCreated: ((TeamId) -> Unit)? = null) {
        viewModelScope.launch {
            _error.value = null
            try {
                val response = apiClient.createTeam(CreateTeamRequest(name = name, description = description))
                loadTeams()
                onCreated?.invoke(response.team.id)
            } catch (e: Exception) {
                _error.value = e.toUiMessage()
            }
        }
    }

    fun clearMessage() {
        _message.value = null
    }

    fun requestToJoin(teamId: TeamId) {
        viewModelScope.launch {
            _error.value = null
            _message.value = null
            try {
                apiClient.submitJoinRequest(teamId)
                _message.value = UiMessage.JoinRequestSubmitted
            } catch (e: Exception) {
                _error.value = e.toUiMessage()
            }
        }
    }

    fun dismissError() {
        _error.value = null
    }

    fun dismissRefreshError() {
        _refreshError.value = null
    }
}
