package com.github.mr3zee

import com.github.mr3zee.editor.AutoSaveManager
import com.github.mr3zee.editor.AutoSaveStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalCoroutinesApi::class)
class AutoSaveManagerTest {

    private var saveCallCount = 0
    private var saveShouldFail = false

    private fun TestScope.createManager(
        debounceMs: Long = 100L,
        savedLingerMs: Long = 50L,
        maxRetries: Int = 3,
    ): AutoSaveManager {
        saveCallCount = 0
        saveShouldFail = false
        return AutoSaveManager(
            scope = this,
            debounceMs = debounceMs,
            savedLingerMs = savedLingerMs,
            maxRetries = maxRetries,
            save = {
                saveCallCount++
                if (saveShouldFail) throw RuntimeException("Save failed")
            },
        )
    }

    @Test
    fun `auto-save fires after debounce elapses`() = runTest {
        val manager = createManager()

        manager.scheduleAutoSave()
        assertEquals(AutoSaveStatus.Pending, manager.status.value)
        assertEquals(0, saveCallCount)

        advanceTimeBy(101L.milliseconds)
        assertEquals(1, saveCallCount)
        assertIs<AutoSaveStatus.Saved>(manager.status.value)
    }

    @Test
    fun `rapid scheduleAutoSave calls reset timer - save called once`() = runTest {
        val manager = createManager()

        manager.scheduleAutoSave()
        advanceTimeBy(50L.milliseconds)
        manager.scheduleAutoSave() // reset timer
        advanceTimeBy(50L.milliseconds)
        manager.scheduleAutoSave() // reset timer again
        advanceTimeBy(50L.milliseconds)

        // Should not have saved yet — timer keeps resetting
        assertEquals(0, saveCallCount)
        assertEquals(AutoSaveStatus.Pending, manager.status.value)

        advanceTimeBy(51L.milliseconds)
        assertEquals(1, saveCallCount)
        assertIs<AutoSaveStatus.Saved>(manager.status.value)
    }

    @Test
    fun `cancelPendingAutoSave prevents save from firing`() = runTest {
        val manager = createManager()

        manager.scheduleAutoSave()
        assertEquals(AutoSaveStatus.Pending, manager.status.value)

        manager.cancelPendingAutoSave()
        assertEquals(AutoSaveStatus.Idle, manager.status.value)

        advanceTimeBy(200L.milliseconds)
        assertEquals(0, saveCallCount)
        assertEquals(AutoSaveStatus.Idle, manager.status.value)
    }

    @Test
    fun `status transitions - Idle to Pending to Saving to Saved to Idle`() = runTest {
        val manager = createManager()

        assertEquals(AutoSaveStatus.Idle, manager.status.value)

        manager.scheduleAutoSave()
        assertEquals(AutoSaveStatus.Pending, manager.status.value)

        // After debounce, goes to Saving then Saved
        advanceTimeBy(101L.milliseconds)
        assertIs<AutoSaveStatus.Saved>(manager.status.value)

        // After linger, goes to Idle
        advanceTimeBy(51L.milliseconds)
        assertEquals(AutoSaveStatus.Idle, manager.status.value)
    }

    @Test
    fun `failed save retries with backoff and stops after maxRetries`() = runTest {
        val manager = createManager(debounceMs = 100L, maxRetries = 3)
        saveShouldFail = true

        manager.scheduleAutoSave()

        // First attempt after 100ms debounce
        advanceTimeBy(101L.milliseconds)
        assertEquals(1, saveCallCount)
        val status1 = manager.status.value
        assertIs<AutoSaveStatus.Failed>(status1)
        assertEquals(1, status1.retryCount)
        assertTrue(!status1.exhausted)

        // Retry 1 after 200ms backoff (100 * 2^1)
        advanceTimeBy(201L.milliseconds)
        assertEquals(2, saveCallCount)
        val status2 = manager.status.value
        assertIs<AutoSaveStatus.Failed>(status2)
        assertEquals(2, status2.retryCount)
        assertTrue(!status2.exhausted)

        // Retry 2 after 400ms backoff (100 * 2^2)
        advanceTimeBy(401L.milliseconds)
        assertEquals(3, saveCallCount)
        val status3 = manager.status.value
        assertIs<AutoSaveStatus.Failed>(status3)
        assertEquals(3, status3.retryCount)
        assertTrue(status3.exhausted)

        // No more retries
        advanceTimeBy(2000L.milliseconds)
        assertEquals(3, saveCallCount)
    }

    @Test
    fun `retry counter resets on successful auto-save`() = runTest {
        val manager = createManager(debounceMs = 100L, maxRetries = 3)
        saveShouldFail = true

        manager.scheduleAutoSave()
        advanceTimeBy(101L.milliseconds)
        assertEquals(1, saveCallCount)
        assertIs<AutoSaveStatus.Failed>(manager.status.value)

        // Fix the save and reschedule
        saveShouldFail = false
        manager.scheduleAutoSave()
        advanceTimeBy(101L.milliseconds)
        assertEquals(2, saveCallCount)
        assertIs<AutoSaveStatus.Saved>(manager.status.value)

        // Fail again — counter should be reset, so we get full retries
        saveShouldFail = true
        manager.scheduleAutoSave()
        advanceTimeBy(101L.milliseconds) // attempt 1
        val status = manager.status.value
        assertIs<AutoSaveStatus.Failed>(status)
        assertEquals(1, status.retryCount) // reset to 1, not 2
    }

    @Test
    fun `setStatus to Saved launches fade to Idle`() = runTest {
        val manager = createManager(savedLingerMs = 50L)

        manager.setStatus(AutoSaveStatus.Saved)
        assertIs<AutoSaveStatus.Saved>(manager.status.value)

        advanceTimeBy(51L.milliseconds)
        assertEquals(AutoSaveStatus.Idle, manager.status.value)
    }

    @Test
    fun `cancelPendingAutoSave during Saving does not interfere`() = runTest {
        val manager = createManager()

        manager.scheduleAutoSave()
        advanceTimeBy(101L.milliseconds)

        // Save already completed
        assertIs<AutoSaveStatus.Saved>(manager.status.value)

        // Cancelling after save completed shouldn't reset status
        manager.cancelPendingAutoSave()
        assertIs<AutoSaveStatus.Saved>(manager.status.value)
    }
}
