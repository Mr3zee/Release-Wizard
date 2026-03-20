package com.github.mr3zee.editor

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlin.math.min
import kotlin.time.Duration.Companion.milliseconds

sealed interface AutoSaveStatus {
    data object Idle : AutoSaveStatus
    data object Pending : AutoSaveStatus
    data object Saving : AutoSaveStatus
    data object Saved : AutoSaveStatus
    data class Failed(val retryCount: Int, val exhausted: Boolean) : AutoSaveStatus
}

/**
 * Manages debounced auto-save with retry and backoff.
 *
 * @param scope coroutine scope (typically viewModelScope)
 * @param debounceMs delay after the last [scheduleAutoSave] call before firing
 * @param savedLingerMs how long the [AutoSaveStatus.Saved] status is shown before reverting to Idle
 * @param maxRetries maximum consecutive failures before stopping
 * @param save the suspend function that performs the actual save; throw to signal failure
 */
class AutoSaveManager(
    private val scope: CoroutineScope,
    private val debounceMs: Long = 3_000L,
    private val savedLingerMs: Long = 2_000L,
    private val maxRetries: Int = 5,
    private val save: suspend () -> Unit,
) {
    private val _status = MutableStateFlow<AutoSaveStatus>(AutoSaveStatus.Idle)
    val status: StateFlow<AutoSaveStatus> = _status

    private var autoSaveJob: Job? = null
    private var savedFadeJob: Job? = null
    private var retryCount = 0

    fun scheduleAutoSave() {
        autoSaveJob?.cancel()
        savedFadeJob?.cancel()
        retryCount = 0
        _status.value = AutoSaveStatus.Pending
        autoSaveJob = scope.launch {
            delay(debounceMs.milliseconds)
            performAutoSave()
        }
    }

    fun cancelPendingAutoSave() {
        autoSaveJob?.cancel()
        autoSaveJob = null
        savedFadeJob?.cancel()
        savedFadeJob = null
        val current = _status.value
        if (current is AutoSaveStatus.Pending) {
            _status.value = AutoSaveStatus.Idle
        }
    }

    fun resetRetryCounter() {
        retryCount = 0
    }

    fun setStatus(status: AutoSaveStatus) {
        savedFadeJob?.cancel()
        _status.value = status
        if (status is AutoSaveStatus.Saved) {
            launchSavedFade()
        }
    }

    private suspend fun performAutoSave() {
        _status.value = AutoSaveStatus.Saving
        try {
            save()
            retryCount = 0
            _status.value = AutoSaveStatus.Saved
            launchSavedFade()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            retryCount++
            val exhausted = retryCount >= maxRetries
            _status.value = AutoSaveStatus.Failed(retryCount, exhausted)
            if (!exhausted) {
                autoSaveJob = scope.launch {
                    delay(currentDelay())
                    performAutoSave()
                }
            }
        }
    }

    private fun launchSavedFade() {
        savedFadeJob?.cancel()
        savedFadeJob = scope.launch {
            delay(savedLingerMs.milliseconds)
            if (_status.value is AutoSaveStatus.Saved) {
                _status.value = AutoSaveStatus.Idle
            }
        }
    }

    private fun currentDelay() =
        min(debounceMs * (1L shl retryCount.coerceAtMost(4)), 30_000L).milliseconds
}
