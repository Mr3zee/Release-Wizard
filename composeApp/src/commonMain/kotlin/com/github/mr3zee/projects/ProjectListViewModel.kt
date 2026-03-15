package com.github.mr3zee.projects

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.mr3zee.api.CreateProjectRequest
import com.github.mr3zee.api.PaginationInfo
import com.github.mr3zee.api.ProjectApiClient
import com.github.mr3zee.api.toUiMessage
import com.github.mr3zee.model.ProjectId
import com.github.mr3zee.util.UiMessage
import com.github.mr3zee.model.ProjectTemplate
import com.github.mr3zee.model.TeamId
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

@OptIn(FlowPreview::class)
class ProjectListViewModel(
    private val apiClient: ProjectApiClient,
    private val activeTeamId: StateFlow<TeamId?>,
) : ViewModel() {

    private val _projects = MutableStateFlow<List<ProjectTemplate>>(emptyList())
    val projects: StateFlow<List<ProjectTemplate>> = _projects

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _error = MutableStateFlow<UiMessage?>(null)
    val error: StateFlow<UiMessage?> = _error

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

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

    private val pageSize = 20

    init {
        viewModelScope.launch {
            _searchQuery.debounce(300.milliseconds).distinctUntilChanged().collectLatest {
                loadProjectsInternal(reset = true)
            }
        }
        viewModelScope.launch {
            activeTeamId.filterNotNull().distinctUntilChanged().collectLatest {
                loadProjectsInternal(reset = true)
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
                loadProjectsInternal(reset = true, silent = true)
            } finally {
                _isManualRefresh.value = false
            }
        }
    }

    fun loadProjects() {
        viewModelScope.launch {
            loadProjectsInternal(reset = true)
        }
    }

    fun loadMore() {
        val nextOffset = _pagination.value?.nextPageOffset() ?: return
        if (_isLoadingMore.value) return

        val currentSearch = _searchQuery.value  // capture current state

        viewModelScope.launch {
            _isLoadingMore.value = true
            try {
                val search = currentSearch.takeIf { it.isNotBlank() }
                val response = apiClient.listProjects(offset = nextOffset, limit = pageSize, search = search)
                // Only append if search hasn't changed while we were loading
                if (_searchQuery.value == currentSearch) {
                    _projects.value += response.projects
                    _pagination.value = response.pagination
                }
            } catch (e: Exception) {
                _error.value = e.toUiMessage()
            } finally {
                _isLoadingMore.value = false
            }
        }
    }

    private suspend fun loadProjectsInternal(reset: Boolean, silent: Boolean = false) {
        if (silent) {
            _isRefreshing.value = true
        } else if (reset) {
            _isLoading.value = true
            _error.value = null
        }
        try {
            val search = _searchQuery.value.takeIf { it.isNotBlank() }
            val response = apiClient.listProjects(offset = 0, limit = pageSize, search = search)
            _projects.value = response.projects
            _pagination.value = response.pagination
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

    fun createProject(name: String, onCreated: ((ProjectId) -> Unit)? = null) {
        viewModelScope.launch {
            _error.value = null
            try {
                val teamId = activeTeamId.value ?: error("No active team selected")
                val project = apiClient.createProject(CreateProjectRequest(name = name, teamId = teamId))
                loadProjects()
                onCreated?.invoke(project.id)
            } catch (e: Exception) {
                _error.value = e.toUiMessage()
            }
        }
    }

    fun deleteProject(id: ProjectId) {
        viewModelScope.launch {
            _error.value = null
            try {
                apiClient.deleteProject(id)
                loadProjects()
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
