package com.github.mr3zee.teams

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.mr3zee.api.CreateTeamRequest
import com.github.mr3zee.api.TeamApiClient
import com.github.mr3zee.api.TeamResponse
import com.github.mr3zee.api.toUserMessage
import com.github.mr3zee.model.TeamId
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

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

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

    fun loadTeams() {
        viewModelScope.launch { loadTeamsInternal() }
    }

    private suspend fun loadTeamsInternal() {
        _isLoading.value = true
        _error.value = null
        try {
            val search = _searchQuery.value.takeIf { it.isNotBlank() }
            val response = apiClient.listTeams(offset = 0, limit = 50, search = search)
            _teams.value = response.teams
        } catch (e: Exception) {
            _error.value = e.toUserMessage()
        } finally {
            _isLoading.value = false
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
                _error.value = e.toUserMessage()
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
                _message.value = "Join request submitted"
            } catch (e: Exception) {
                _error.value = e.toUserMessage()
            }
        }
    }
}
