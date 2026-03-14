package com.github.mr3zee.releases

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.mr3zee.api.ReleaseApiClient
import com.github.mr3zee.api.ReleaseEvent
import com.github.mr3zee.api.toUserMessage
import com.github.mr3zee.model.*
import com.github.mr3zee.model.isTerminal
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

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

    private val _reconnectAttempt = MutableStateFlow(0)
    val reconnectAttempt: StateFlow<Int> = _reconnectAttempt

    /** Tracks the highest sequence number seen from the server for incremental replay on reconnect. */
    private var lastSequenceNumber: Long = 0

    private var wsJob: Job? = null

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
        _reconnectAttempt.value = 0
        lastSequenceNumber = 0
    }

    private suspend fun connectWithRetry() {
        while (true) {
            try {
                _reconnectAttempt.value = 0

                // Pass lastSequenceNumber for incremental replay on reconnect.
                // On first connect this is 0 so the server sends a full snapshot.
                val seqToSend = lastSequenceNumber.takeIf { it > 0 }
                releaseApiClient.watchRelease(releaseId, lastSeq = seqToSend).collect { event ->
                    // Mark connected on first event (the Snapshot or first replayed event)
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
            } catch (_: Exception) {
                _isConnected.value = false
                // Don't reconnect if release is in terminal state
                val currentRelease = _release.value
                if (currentRelease != null && currentRelease.status.isTerminal) {
                    return
                }

                _reconnectAttempt.value++
                // Exponential backoff: 1s, 2s, 4s, 8s, 16s, 30s cap
                val delayMs = (1000L * (1L shl minOf(_reconnectAttempt.value - 1, 4))).coerceAtMost(30_000L)
                delay(delayMs.milliseconds)
            }
        }
    }

    private fun handleEvent(event: ReleaseEvent) {
        // Track highest sequence number for incremental replay on reconnect
        if (event.sequenceNumber > lastSequenceNumber) {
            lastSequenceNumber = event.sequenceNumber
        }

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
                _error.value = e.toUserMessage()
            }
        }
    }

    fun rerunRelease(onRerunCreated: (ReleaseId) -> Unit) {
        viewModelScope.launch {
            try {
                val response = releaseApiClient.rerunRelease(releaseId)
                onRerunCreated(response.release.id)
            } catch (e: Exception) {
                _error.value = e.toUserMessage()
            }
        }
    }

    fun archiveRelease() {
        viewModelScope.launch {
            try {
                val response = releaseApiClient.archiveRelease(releaseId)
                _release.value = response.release
            } catch (e: Exception) {
                _error.value = e.toUserMessage()
            }
        }
    }

    fun approveBlock(blockId: BlockId, input: Map<String, String> = emptyMap()) {
        viewModelScope.launch {
            try {
                releaseApiClient.approveBlock(releaseId, blockId, input)
            } catch (e: Exception) {
                _error.value = e.toUserMessage()
            }
        }
    }

    override fun onCleared() {
        disconnect()
        super.onCleared()
    }

}
