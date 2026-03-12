package com.github.mr3zee.projects

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.mr3zee.api.CreateProjectRequest
import com.github.mr3zee.api.ProjectApiClient
import com.github.mr3zee.model.ProjectId
import com.github.mr3zee.model.ProjectTemplate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class ProjectListViewModel(
    private val apiClient: ProjectApiClient,
) : ViewModel() {

    private val _projects = MutableStateFlow<List<ProjectTemplate>>(emptyList())
    val projects: StateFlow<List<ProjectTemplate>> = _projects

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    fun loadProjects() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                _projects.value = apiClient.listProjects()
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to load projects"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun createProject(name: String) {
        viewModelScope.launch {
            _error.value = null
            try {
                apiClient.createProject(CreateProjectRequest(name = name))
                loadProjects()
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to create project"
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
                _error.value = e.message ?: "Failed to delete project"
            }
        }
    }
}
