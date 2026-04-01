package com.github.mr3zee.releases

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.mr3zee.api.CreateReleaseRequest
import com.github.mr3zee.api.ProjectApiClient
import com.github.mr3zee.api.ReleaseApiClient
import com.github.mr3zee.api.toUiMessage
import com.github.mr3zee.model.Parameter
import com.github.mr3zee.model.ProjectId
import com.github.mr3zee.model.ProjectTemplate
import com.github.mr3zee.model.ReleaseId
import com.github.mr3zee.util.UiMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class StartReleaseViewModel(
    private val projectId: ProjectId,
    private val projectApiClient: ProjectApiClient,
    private val releaseApiClient: ReleaseApiClient,
) : ViewModel() {

    private val _project = MutableStateFlow<ProjectTemplate?>(null)
    val project: StateFlow<ProjectTemplate?> = _project

    private val _releaseName = MutableStateFlow("")
    val releaseName: StateFlow<String> = _releaseName

    private val _parameters = MutableStateFlow<List<Parameter>>(emptyList())
    val parameters: StateFlow<List<Parameter>> = _parameters

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _isStarting = MutableStateFlow(false)
    val isStarting: StateFlow<Boolean> = _isStarting

    private val _error = MutableStateFlow<UiMessage?>(null)
    val error: StateFlow<UiMessage?> = _error

    init {
        loadProject()
    }

    private fun loadProject() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val project = projectApiClient.getProject(projectId)
                _project.value = project
                _releaseName.value = project.name
                _parameters.value = project.parameters
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _error.value = e.toUiMessage()
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun updateReleaseName(name: String) {
        _releaseName.value = name
        _error.value = null
    }

    fun updateParameter(key: String, newValue: String) {
        _parameters.value = _parameters.value.map { param ->
            if (param.key == key) param.copy(value = newValue) else param
        }
        _error.value = null
    }

    fun canStart(): Boolean {
        return _parameters.value.all { it.value.isNotBlank() } && _releaseName.value.isNotBlank()
    }

    fun startRelease(onCreated: (ReleaseId) -> Unit) {
        if (!canStart()) return
        viewModelScope.launch {
            _isStarting.value = true
            _error.value = null
            try {
                val response = releaseApiClient.startRelease(
                    CreateReleaseRequest(
                        projectTemplateId = projectId,
                        name = _releaseName.value,
                        parameters = _parameters.value,
                    )
                )
                onCreated(response.release.id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _error.value = e.toUiMessage()
            } finally {
                _isStarting.value = false
            }
        }
    }

    fun dismissError() {
        _error.value = null
    }
}
