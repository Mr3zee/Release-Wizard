package com.github.mr3zee.releases

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.mr3zee.api.CreateReleaseRequest
import com.github.mr3zee.api.PaginationInfo
import com.github.mr3zee.api.ProjectApiClient
import com.github.mr3zee.api.ReleaseApiClient
import com.github.mr3zee.api.toUserMessage
import com.github.mr3zee.model.ProjectId
import com.github.mr3zee.model.ProjectTemplate
import com.github.mr3zee.model.Release
import com.github.mr3zee.model.ReleaseId
import com.github.mr3zee.model.ReleaseStatus
import com.github.mr3zee.model.TeamId
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

@OptIn(FlowPreview::class)
class ReleaseListViewModel(
    private val releaseApiClient: ReleaseApiClient,
    private val projectApiClient: ProjectApiClient,
    private val activeTeamId: StateFlow<TeamId?>,
) : ViewModel() {

    private val _releases = MutableStateFlow<List<Release>>(emptyList())
    val releases: StateFlow<List<Release>> = _releases

    private val _projects = MutableStateFlow<List<ProjectTemplate>>(emptyList())
    val projects: StateFlow<List<ProjectTemplate>> = _projects

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    private val _statusFilter = MutableStateFlow<ReleaseStatus?>(null)
    val statusFilter: StateFlow<ReleaseStatus?> = _statusFilter

    private val _projectFilter = MutableStateFlow<ProjectId?>(null)
    val projectFilter: StateFlow<ProjectId?> = _projectFilter

    private val _pagination = MutableStateFlow<PaginationInfo?>(null)
    val pagination: StateFlow<PaginationInfo?> = _pagination

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore

    private val pageSize = 20

    init {
        viewModelScope.launch {
            combine(
                _searchQuery.debounce(300.milliseconds),
                _statusFilter,
                _projectFilter,
            ) { search, status, project -> Triple(search, status, project) }
                .distinctUntilChanged()
                .collectLatest {
                    loadReleasesInternal(reset = true)
                }
        }
        viewModelScope.launch {
            activeTeamId.filterNotNull().distinctUntilChanged().collectLatest {
                loadReleasesInternal(reset = true)
            }
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setStatusFilter(status: ReleaseStatus?) {
        _statusFilter.value = status
    }

    fun setProjectFilter(projectId: ProjectId?) {
        _projectFilter.value = projectId
    }

    fun loadReleases() {
        viewModelScope.launch {
            loadReleasesInternal(reset = true)
        }
    }

    fun loadMore() {
        val nextOffset = _pagination.value?.nextPageOffset() ?: return
        if (_isLoadingMore.value) return

        // Capture current filter state to detect changes during in-flight request
        val currentSearch = _searchQuery.value
        val currentStatus = _statusFilter.value
        val currentProject = _projectFilter.value

        viewModelScope.launch {
            _isLoadingMore.value = true
            try {
                val response = releaseApiClient.listReleases(
                    offset = nextOffset,
                    limit = pageSize,
                    search = currentSearch.takeIf { it.isNotBlank() },
                    status = currentStatus?.name,
                    projectId = currentProject?.value,
                )
                // Only append if filters haven't changed while we were loading
                if (_searchQuery.value == currentSearch &&
                    _statusFilter.value == currentStatus &&
                    _projectFilter.value == currentProject
                ) {
                    _releases.value += response.releases
                    _pagination.value = response.pagination
                }
            } catch (e: Exception) {
                _error.value = e.toUserMessage()
            } finally {
                _isLoadingMore.value = false
            }
        }
    }

    private suspend fun loadReleasesInternal(reset: Boolean) {
        if (reset) {
            _isLoading.value = true
            _error.value = null
        }
        try {
            val response = releaseApiClient.listReleases(
                offset = 0,
                limit = pageSize,
                search = _searchQuery.value.takeIf { it.isNotBlank() },
                status = _statusFilter.value?.name,
                projectId = _projectFilter.value?.value,
            )
            _releases.value = response.releases
            _pagination.value = response.pagination
        } catch (e: Exception) {
            _error.value = e.toUserMessage()
        } finally {
            _isLoading.value = false
        }
    }

    fun loadProjects() {
        viewModelScope.launch {
            try {
                val response = projectApiClient.listProjects(limit = 200)
                _projects.value = response.projects
            } catch (_: Exception) {
                // Projects loading failure is non-critical for the release list
            }
        }
    }

    fun startRelease(projectId: ProjectId) {
        viewModelScope.launch {
            try {
                releaseApiClient.startRelease(CreateReleaseRequest(projectTemplateId = projectId))
                loadReleases()
            } catch (e: Exception) {
                _error.value = e.toUserMessage()
            }
        }
    }

    fun archiveRelease(id: ReleaseId) {
        viewModelScope.launch {
            try {
                releaseApiClient.archiveRelease(id)
                loadReleases()
            } catch (e: Exception) {
                _error.value = e.toUserMessage()
            }
        }
    }

    fun deleteRelease(id: ReleaseId) {
        viewModelScope.launch {
            try {
                releaseApiClient.deleteRelease(id)
                loadReleases()
            } catch (e: Exception) {
                _error.value = e.toUserMessage()
            }
        }
    }

    fun dismissError() {
        _error.value = null
    }
}
