package com.github.mr3zee.releases

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.mr3zee.api.CreateReleaseRequest
import com.github.mr3zee.api.PaginationInfo
import com.github.mr3zee.api.ProjectApiClient
import com.github.mr3zee.api.ReleaseApiClient
import com.github.mr3zee.api.toUiMessage
import com.github.mr3zee.util.UiMessage
import com.github.mr3zee.model.ProjectId
import com.github.mr3zee.model.ProjectTemplate
import com.github.mr3zee.model.Release
import com.github.mr3zee.model.ReleaseId
import com.github.mr3zee.model.ReleaseStatus
import com.github.mr3zee.model.TeamId
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

@OptIn(FlowPreview::class)
class ReleaseListViewModel(
    private val releaseApiClient: ReleaseApiClient,
    private val projectApiClient: ProjectApiClient,
    private val activeTeamId: StateFlow<TeamId?>,
    private val pollingIntervalMs: Long = 10_000L,
) : ViewModel() {
    init {
        require(pollingIntervalMs >= 1_000L) { "Polling interval must be >= 1000 ms" }
    }

    private val _releases = MutableStateFlow<List<Release>>(emptyList())
    val releases: StateFlow<List<Release>> = _releases

    private val _projects = MutableStateFlow<List<ProjectTemplate>>(emptyList())
    val projects: StateFlow<List<ProjectTemplate>> = _projects

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _error = MutableStateFlow<UiMessage?>(null)
    val error: StateFlow<UiMessage?> = _error

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

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing

    private val _isManualRefresh = MutableStateFlow(false)
    val isManualRefresh: StateFlow<Boolean> = _isManualRefresh

    private val _refreshError = MutableStateFlow<UiMessage?>(null)
    val refreshError: StateFlow<UiMessage?> = _refreshError

    private val _isActive = MutableStateFlow(false)

    private val _lastRefreshedAt = MutableStateFlow<TimeMark?>(null)
    val lastRefreshedAt: StateFlow<TimeMark?> = _lastRefreshedAt

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
        viewModelScope.launch {
            _isActive.collectLatest { active ->
                if (active) {
                    while (true) {
                        delay(pollingIntervalMs.milliseconds)
                        loadReleasesInternal(reset = true, silent = true)
                    }
                }
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

    fun setActive(active: Boolean) {
        _isActive.value = active
    }

    fun refresh() {
        if (_isRefreshing.value) return
        viewModelScope.launch {
            _isManualRefresh.value = true
            _refreshError.value = null
            try {
                loadReleasesInternal(reset = true, silent = true)
            } finally {
                _isManualRefresh.value = false
            }
        }
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
                _error.value = e.toUiMessage()
            } finally {
                _isLoadingMore.value = false
            }
        }
    }

    private suspend fun loadReleasesInternal(reset: Boolean, silent: Boolean = false) {
        // Skip loading when no team is selected (e.g., before login or on Teams screen)
        if (activeTeamId.value == null) return

        if (silent) {
            _isRefreshing.value = true
        } else if (reset) {
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
            _lastRefreshedAt.value = TimeSource.Monotonic.markNow()
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

    fun startRelease(projectId: ProjectId, onCreated: (ReleaseId) -> Unit = {}) {
        viewModelScope.launch {
            try {
                val response = releaseApiClient.startRelease(CreateReleaseRequest(projectTemplateId = projectId))
                onCreated(response.release.id)
                loadReleases()
            } catch (e: Exception) {
                _error.value = e.toUiMessage()
            }
        }
    }

    fun archiveRelease(id: ReleaseId) {
        viewModelScope.launch {
            try {
                releaseApiClient.archiveRelease(id)
                loadReleases()
            } catch (e: Exception) {
                _error.value = e.toUiMessage()
            }
        }
    }

    fun deleteRelease(id: ReleaseId) {
        viewModelScope.launch {
            try {
                releaseApiClient.deleteRelease(id)
                loadReleases()
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
