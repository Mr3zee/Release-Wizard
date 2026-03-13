package com.github.mr3zee.releases

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.mr3zee.api.ReleaseApiClient
import com.github.mr3zee.api.ReleaseEvent
import com.github.mr3zee.model.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class ReleaseDetailViewModel(
    private val releaseId: ReleaseId,
    private val releaseApiClient: ReleaseApiClient,
) : ViewModel() {

    private val _release = MutableStateFlow<Release?>(null)
    val release: StateFlow<Release?> = _release

    private val _blockExecutions = MutableStateFlow<List<BlockExecution>>(emptyList())
    val blockExecutions: StateFlow<List<BlockExecution>> = _blockExecutions

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private var wsJob: Job? = null
    private var reconnectAttempt = 0

    fun connect() {
        wsJob?.cancel()
        wsJob = viewModelScope.launch {
            connectWithRetry()
        }
    }

    fun disconnect() {
        wsJob?.cancel()
        wsJob = null
        _isConnected.value = false
    }

    private suspend fun connectWithRetry() {
        while (true) {
            try {
                reconnectAttempt = 0

                releaseApiClient.watchRelease(releaseId).collect { event ->
                    // Mark connected on first event (the Snapshot)
                    if (!_isConnected.value) {
                        _isConnected.value = true
                    }
                    handleEvent(event)
                }

                // Flow completed normally (release finished)
                _isConnected.value = false
                return
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _isConnected.value = false
                // Don't reconnect if release is in terminal state
                val currentRelease = _release.value
                if (currentRelease != null && currentRelease.status in terminalStatuses) {
                    return
                }

                reconnectAttempt++
                // Exponential backoff: 1s, 2s, 4s, 8s, 16s, 30s cap
                val delayMs = (1000L * (1L shl minOf(reconnectAttempt - 1, 4))).coerceAtMost(30_000L)
                delay(delayMs)
            }
        }
    }

    private fun handleEvent(event: ReleaseEvent) {
        when (event) {
            is ReleaseEvent.Snapshot -> {
                _release.value = event.release
                _blockExecutions.value = event.blockExecutions
            }
            is ReleaseEvent.ReleaseStatusChanged -> {
                _release.value = _release.value?.copy(
                    status = event.status,
                    startedAt = event.startedAt ?: _release.value?.startedAt,
                    finishedAt = event.finishedAt ?: _release.value?.finishedAt,
                )
            }
            is ReleaseEvent.BlockExecutionUpdated -> {
                val updated = event.blockExecution
                val current = _blockExecutions.value
                val index = current.indexOfFirst { it.blockId == updated.blockId }
                _blockExecutions.value = if (index >= 0) {
                    current.toMutableList().apply { set(index, updated) }
                } else {
                    current + updated
                }
            }
            is ReleaseEvent.ReleaseCompleted -> {
                _release.value = _release.value?.copy(
                    status = event.status,
                    finishedAt = event.finishedAt,
                )
            }
        }
    }

    fun cancelRelease() {
        viewModelScope.launch {
            try {
                releaseApiClient.cancelRelease(releaseId)
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to cancel release"
            }
        }
    }

    fun approveBlock(blockId: BlockId, input: Map<String, String> = emptyMap()) {
        viewModelScope.launch {
            try {
                releaseApiClient.approveBlock(releaseId, blockId, input)
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to approve block"
            }
        }
    }

    override fun onCleared() {
        disconnect()
        super.onCleared()
    }

    companion object {
        private val terminalStatuses = setOf(
            ReleaseStatus.SUCCEEDED,
            ReleaseStatus.FAILED,
            ReleaseStatus.CANCELLED,
        )
    }
}
