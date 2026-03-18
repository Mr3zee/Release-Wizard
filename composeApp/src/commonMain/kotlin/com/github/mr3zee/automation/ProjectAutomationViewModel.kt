package com.github.mr3zee.automation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.mr3zee.api.CreateMavenTriggerRequest
import com.github.mr3zee.api.CreateScheduleRequest
import com.github.mr3zee.api.CreateTriggerRequest
import com.github.mr3zee.api.MavenTriggerApiClient
import com.github.mr3zee.api.ScheduleApiClient
import com.github.mr3zee.api.TriggerResponse
import com.github.mr3zee.api.WebhookTriggerApiClient
import com.github.mr3zee.api.toUiMessage
import com.github.mr3zee.model.MavenTrigger
import com.github.mr3zee.model.ProjectId
import com.github.mr3zee.model.Schedule
import com.github.mr3zee.util.UiMessage
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class ProjectAutomationViewModel(
    val projectId: ProjectId,
    private val scheduleClient: ScheduleApiClient,
    private val webhookClient: WebhookTriggerApiClient,
    private val mavenClient: MavenTriggerApiClient,
) : ViewModel() {

    private val _schedules = MutableStateFlow<List<Schedule>>(emptyList())
    val schedules: StateFlow<List<Schedule>> = _schedules

    private val _webhookTriggers = MutableStateFlow<List<TriggerResponse>>(emptyList())
    val webhookTriggers: StateFlow<List<TriggerResponse>> = _webhookTriggers

    private val _mavenTriggers = MutableStateFlow<List<MavenTrigger>>(emptyList())
    val mavenTriggers: StateFlow<List<MavenTrigger>> = _mavenTriggers

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving

    private val _error = MutableStateFlow<UiMessage?>(null)
    val error: StateFlow<UiMessage?> = _error

    // Emits the created webhook trigger response (for showing secret once)
    private val _webhookCreated = MutableSharedFlow<TriggerResponse>()
    val webhookCreated: SharedFlow<TriggerResponse> = _webhookCreated

    // Emits the newly created MavenTrigger so the UI can show artifact identity in the confirmation dialog
    private val _mavenTriggerCreated = MutableSharedFlow<MavenTrigger>()
    val mavenTriggerCreated: SharedFlow<MavenTrigger> = _mavenTriggerCreated

    fun load() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                _schedules.value = scheduleClient.listSchedules(projectId)
                _webhookTriggers.value = webhookClient.listTriggers(projectId)
                _mavenTriggers.value = mavenClient.listMavenTriggers(projectId)
            } catch (e: Exception) {
                _error.value = e.toUiMessage()
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun createSchedule(request: CreateScheduleRequest) {
        viewModelScope.launch {
            _isSaving.value = true
            try {
                val created = scheduleClient.createSchedule(projectId, request)
                _schedules.value = _schedules.value + created
            } catch (e: Exception) {
                _error.value = e.toUiMessage()
            } finally {
                _isSaving.value = false
            }
        }
    }

    fun toggleSchedule(scheduleId: String, enabled: Boolean) {
        viewModelScope.launch {
            try {
                val updated = scheduleClient.toggleSchedule(projectId, scheduleId, enabled)
                _schedules.value = _schedules.value.map { if (it.id == scheduleId) updated else it }
            } catch (e: Exception) {
                _error.value = e.toUiMessage()
            }
        }
    }

    fun deleteSchedule(scheduleId: String) {
        viewModelScope.launch {
            try {
                scheduleClient.deleteSchedule(projectId, scheduleId)
                _schedules.value = _schedules.value.filter { it.id != scheduleId }
            } catch (e: Exception) {
                _error.value = e.toUiMessage()
            }
        }
    }

    fun createWebhookTrigger(request: CreateTriggerRequest) {
        viewModelScope.launch {
            _isSaving.value = true
            try {
                val created = webhookClient.createTrigger(projectId, request)
                _webhookTriggers.value = _webhookTriggers.value + created
                _webhookCreated.emit(created)
            } catch (e: Exception) {
                _error.value = e.toUiMessage()
            } finally {
                _isSaving.value = false
            }
        }
    }

    fun toggleWebhookTrigger(triggerId: String, enabled: Boolean) {
        viewModelScope.launch {
            try {
                val updated = webhookClient.toggleTrigger(projectId, triggerId, enabled)
                _webhookTriggers.value = _webhookTriggers.value.map { if (it.id == triggerId) updated else it }
            } catch (e: Exception) {
                _error.value = e.toUiMessage()
            }
        }
    }

    fun deleteWebhookTrigger(triggerId: String) {
        viewModelScope.launch {
            try {
                webhookClient.deleteTrigger(projectId, triggerId)
                _webhookTriggers.value = _webhookTriggers.value.filter { it.id != triggerId }
            } catch (e: Exception) {
                _error.value = e.toUiMessage()
            }
        }
    }

    fun createMavenTrigger(request: CreateMavenTriggerRequest) {
        viewModelScope.launch {
            _isSaving.value = true
            try {
                val created = mavenClient.createMavenTrigger(projectId, request)
                _mavenTriggers.value = _mavenTriggers.value + created
                _mavenTriggerCreated.emit(created)
            } catch (e: Exception) {
                _error.value = e.toUiMessage()
            } finally {
                _isSaving.value = false
            }
        }
    }

    fun toggleMavenTrigger(triggerId: String, enabled: Boolean) {
        viewModelScope.launch {
            try {
                val updated = mavenClient.toggleMavenTrigger(projectId, triggerId, enabled)
                _mavenTriggers.value = _mavenTriggers.value.map { if (it.id == triggerId) updated else it }
            } catch (e: Exception) {
                _error.value = e.toUiMessage()
            }
        }
    }

    fun deleteMavenTrigger(triggerId: String) {
        viewModelScope.launch {
            try {
                mavenClient.deleteMavenTrigger(projectId, triggerId)
                _mavenTriggers.value = _mavenTriggers.value.filter { it.id != triggerId }
            } catch (e: Exception) {
                _error.value = e.toUiMessage()
            }
        }
    }

    fun dismissError() {
        _error.value = null
    }
}
