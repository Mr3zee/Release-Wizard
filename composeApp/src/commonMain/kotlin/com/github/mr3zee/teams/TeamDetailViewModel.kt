package com.github.mr3zee.teams

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.mr3zee.api.TeamApiClient
import com.github.mr3zee.api.toUiMessage
import com.github.mr3zee.model.Team
import com.github.mr3zee.util.UiMessage
import com.github.mr3zee.model.TeamId
import com.github.mr3zee.model.TeamMembership
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class TeamDetailViewModel(
    private val teamId: TeamId,
    private val apiClient: TeamApiClient,
) : ViewModel() {

    private val _team = MutableStateFlow<Team?>(null)
    val team: StateFlow<Team?> = _team

    private val _members = MutableStateFlow<List<TeamMembership>>(emptyList())
    val members: StateFlow<List<TeamMembership>> = _members

    private val _error = MutableStateFlow<UiMessage?>(null)
    val error: StateFlow<UiMessage?> = _error

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _actionError = MutableStateFlow<UiMessage?>(null)
    val actionError: StateFlow<UiMessage?> = _actionError

    private val _isLeaving = MutableStateFlow(false)
    val isLeaving: StateFlow<Boolean> = _isLeaving

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing

    private val _isManualRefresh = MutableStateFlow(false)
    val isManualRefresh: StateFlow<Boolean> = _isManualRefresh

    private val _refreshError = MutableStateFlow<UiMessage?>(null)
    val refreshError: StateFlow<UiMessage?> = _refreshError

    init {
        loadDetail()
    }

    fun loadDetail() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val detail = apiClient.getTeamDetail(teamId)
                _team.value = detail.team
                _members.value = detail.members
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
                val detail = apiClient.getTeamDetail(teamId)
                _team.value = detail.team
                _members.value = detail.members
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

    fun leaveTeam(onLeft: () -> Unit) {
        viewModelScope.launch {
            _isLeaving.value = true
            try {
                apiClient.leaveTeam(teamId)
                onLeft()
            } catch (e: Exception) {
                _actionError.value = e.toUiMessage()
            } finally {
                _isLeaving.value = false
            }
        }
    }

    fun dismissError() {
        _error.value = null
    }

    fun dismissActionError() {
        _actionError.value = null
    }
}
