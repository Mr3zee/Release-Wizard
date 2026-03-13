package com.github.mr3zee.releases

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.mr3zee.api.CreateReleaseRequest
import com.github.mr3zee.api.ProjectApiClient
import com.github.mr3zee.api.ReleaseApiClient
import com.github.mr3zee.api.toUserMessage
import com.github.mr3zee.model.ProjectId
import com.github.mr3zee.model.ProjectTemplate
import com.github.mr3zee.model.Release
import com.github.mr3zee.model.ReleaseId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class ReleaseListViewModel(
    private val releaseApiClient: ReleaseApiClient,
    private val projectApiClient: ProjectApiClient,
) : ViewModel() {

    private val _releases = MutableStateFlow<List<Release>>(emptyList())
    val releases: StateFlow<List<Release>> = _releases

    private val _projects = MutableStateFlow<List<ProjectTemplate>>(emptyList())
    val projects: StateFlow<List<ProjectTemplate>> = _projects

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    fun loadReleases() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                _releases.value = releaseApiClient.listReleases().releases
            } catch (e: Exception) {
                _error.value = e.toUserMessage()
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun loadProjects() {
        viewModelScope.launch {
            try {
                _projects.value = projectApiClient.listProjects()
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
}
