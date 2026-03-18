package com.github.mr3zee.teams

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.mr3zee.api.*
import com.github.mr3zee.model.*
import com.github.mr3zee.util.UiMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class TeamManageViewModel(
    private val teamId: TeamId,
    private val apiClient: TeamApiClient,
) : ViewModel() {

    private val _teamName = MutableStateFlow("")
    val teamName: StateFlow<String> = _teamName

    private val _teamDescription = MutableStateFlow("")
    val teamDescription: StateFlow<String> = _teamDescription

    private val _members = MutableStateFlow<List<TeamMembership>>(emptyList())
    val members: StateFlow<List<TeamMembership>> = _members

    private val _invites = MutableStateFlow<List<TeamInvite>>(emptyList())
    val invites: StateFlow<List<TeamInvite>> = _invites

    private val _joinRequests = MutableStateFlow<List<JoinRequest>>(emptyList())
    val joinRequests: StateFlow<List<JoinRequest>> = _joinRequests

    private val _error = MutableStateFlow<UiMessage?>(null)
    val error: StateFlow<UiMessage?> = _error

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    init {
        loadAll()
    }

    fun loadAll() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val detail = apiClient.getTeamDetail(teamId)
                _teamName.value = detail.team.name
                _teamDescription.value = detail.team.description
                val membersResponse = apiClient.listMembers(teamId)
                _members.value = membersResponse.members
                val invitesResponse = apiClient.listTeamInvites(teamId)
                _invites.value = invitesResponse.invites
                val requestsResponse = apiClient.listJoinRequests(teamId)
                _joinRequests.value = requestsResponse.requests
            } catch (e: Exception) {
                _error.value = e.toUiMessage()
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun updateTeam(name: String, description: String) {
        viewModelScope.launch {
            try {
                apiClient.updateTeam(teamId, UpdateTeamRequest(name = name, description = description))
                loadAll()
            } catch (e: Exception) {
                _error.value = e.toUiMessage()
            }
        }
    }

    fun deleteTeam(onDeleted: () -> Unit) {
        viewModelScope.launch {
            try {
                apiClient.deleteTeam(teamId)
                onDeleted()
            } catch (e: Exception) {
                _error.value = e.toUiMessage()
            }
        }
    }

    fun updateMemberRole(userId: String, role: TeamRole) {
        viewModelScope.launch {
            try {
                apiClient.updateMemberRole(teamId, userId, UpdateMemberRoleRequest(role))
                loadAll()
            } catch (e: Exception) {
                _error.value = e.toUiMessage()
            }
        }
    }

    fun removeMember(userId: String) {
        viewModelScope.launch {
            try {
                apiClient.removeMember(teamId, userId)
                loadAll()
            } catch (e: Exception) {
                _error.value = e.toUiMessage()
            }
        }
    }

    fun inviteUser(userId: UserId) {
        viewModelScope.launch {
            try {
                apiClient.inviteUser(teamId, userId)
                loadAll()
            } catch (e: Exception) {
                _error.value = e.toUiMessage()
            }
        }
    }

    fun cancelInvite(inviteId: String) {
        viewModelScope.launch {
            try {
                apiClient.cancelInvite(teamId, inviteId)
                loadAll()
            } catch (e: Exception) {
                _error.value = e.toUiMessage()
            }
        }
    }

    fun approveJoinRequest(requestId: String) {
        viewModelScope.launch {
            try {
                apiClient.approveJoinRequest(teamId, requestId)
                loadAll()
            } catch (e: Exception) {
                _error.value = e.toUiMessage()
            }
        }
    }

    fun rejectJoinRequest(requestId: String) {
        viewModelScope.launch {
            try {
                apiClient.rejectJoinRequest(teamId, requestId)
                loadAll()
            } catch (e: Exception) {
                _error.value = e.toUiMessage()
            }
        }
    }

    fun dismissError() {
        _error.value = null
    }
}
