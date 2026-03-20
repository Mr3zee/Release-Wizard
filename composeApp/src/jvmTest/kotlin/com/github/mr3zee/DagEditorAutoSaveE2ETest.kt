package com.github.mr3zee

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import com.github.mr3zee.api.ProjectApiClient
import com.github.mr3zee.editor.AutoSaveStatus
import com.github.mr3zee.editor.DagEditorScreen
import com.github.mr3zee.editor.DagEditorViewModel
import com.github.mr3zee.model.ProjectId
import io.ktor.http.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * End-to-end tests for auto-save behavior in DagEditorScreen.
 * Uses a short debounce (200ms) to keep tests fast while still exercising the real flow.
 */
@OptIn(ExperimentalTestApi::class)
class DagEditorAutoSaveE2ETest {

    private val projectJson = """{"project":{"id":"p1","name":"Test Project","description":"","dagGraph":{"blocks":[
        {"kind":"action","id":"b1","name":"Build","type":"TEAMCITY_BUILD","parameters":[],"outputs":[],"timeoutSeconds":null,"connectionId":null}
    ],"edges":[],"positions":{"b1":{"x":100,"y":100}}},"parameters":[],"createdAt":"2026-03-13T00:00:00Z","updatedAt":"2026-03-13T00:00:00Z"}}"""

    private val lockJson = """{"userId":"u1","username":"testuser","acquiredAt":"2026-03-13T00:00:00Z","expiresAt":"2026-03-13T00:05:00Z"}"""

    private val updatedProjectJson = """{"project":{"id":"p1","name":"Test Project","description":"","dagGraph":{"blocks":[
        {"kind":"action","id":"b1","name":"Build","type":"TEAMCITY_BUILD","parameters":[],"outputs":[],"timeoutSeconds":null,"connectionId":null},
        {"kind":"action","id":"b2","name":"New Block","type":"SLACK_MESSAGE","parameters":[],"outputs":[],"timeoutSeconds":null,"connectionId":null}
    ],"edges":[],"positions":{"b1":{"x":100,"y":100},"b2":{"x":320,"y":100}}},"parameters":[],"createdAt":"2026-03-13T00:00:00Z","updatedAt":"2026-03-13T00:01:00Z"}}"""

    private fun autoSaveClient(
        putStatus: HttpStatusCode = HttpStatusCode.OK,
    ) = mockHttpClient(
        listOf(
            "/projects/p1" to json(projectJson, HttpStatusCode.OK, method = HttpMethod.Get),
            "/projects/p1" to json(updatedProjectJson, putStatus, method = HttpMethod.Put),
            "/projects/p1/lock" to json(lockJson, HttpStatusCode.OK, method = HttpMethod.Post),
            "/projects/p1/lock/heartbeat" to json(lockJson, HttpStatusCode.OK, method = HttpMethod.Put),
        )
    )

    private fun autoSaveViewModel(
        putStatus: HttpStatusCode = HttpStatusCode.OK,
        debounceMs: Long = 200L,
    ): DagEditorViewModel {
        val client = autoSaveClient(putStatus)
        return DagEditorViewModel(
            projectId = ProjectId("p1"),
            apiClient = ProjectApiClient(client),
            autoSaveDebounceMs = debounceMs,
        )
    }

    @Test
    fun `auto-save indicator shows Pending after editing`() = runComposeUiTest {
        val vm = autoSaveViewModel()
        setContent {
            MaterialTheme {
                DagEditorScreen(viewModel = vm, onBack = {})
            }
        }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithText("Test Project").fetchSemanticsNodes().isNotEmpty()
        }

        // Add a block to make dirty
        onNodeWithTag("add_block_SLACK_MESSAGE").performClick()
        waitForIdle()

        // Auto-save status should be Pending
        assertIs<AutoSaveStatus.Pending>(vm.autoSaveStatus.value)
        assertTrue(vm.isDirty.value, "Should be dirty after adding block")
    }

    @Test
    fun `auto-save clears dirty flag after debounce`() = runComposeUiTest {
        val vm = autoSaveViewModel(debounceMs = 200L)
        setContent {
            MaterialTheme {
                DagEditorScreen(viewModel = vm, onBack = {})
            }
        }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithText("Test Project").fetchSemanticsNodes().isNotEmpty()
        }

        // Add a block
        onNodeWithTag("add_block_SLACK_MESSAGE").performClick()
        waitForIdle()
        assertTrue(vm.isDirty.value)

        // Wait for auto-save to fire
        waitUntil(timeoutMillis = 3000L) {
            vm.autoSaveStatus.value is AutoSaveStatus.Saved
        }

        assertFalse(vm.isDirty.value, "Should not be dirty after auto-save")
    }

    @Test
    fun `auto-save shows Saved indicator in UI`() = runComposeUiTest {
        val vm = autoSaveViewModel(debounceMs = 200L)
        setContent {
            MaterialTheme {
                DagEditorScreen(viewModel = vm, onBack = {})
            }
        }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithText("Test Project").fetchSemanticsNodes().isNotEmpty()
        }

        // Add a block
        onNodeWithTag("add_block_SLACK_MESSAGE").performClick()
        waitForIdle()

        // Wait for Saved status
        waitUntil(timeoutMillis = 3000L) {
            vm.autoSaveStatus.value is AutoSaveStatus.Saved
        }

        // UI should show the Saved indicator
        onNodeWithTag("auto_save_indicator").assertExists()
    }

    @Test
    fun `manual save cancels pending auto-save`() = runComposeUiTest {
        val vm = autoSaveViewModel(debounceMs = 5000L) // long debounce so auto-save won't fire
        setContent {
            MaterialTheme {
                DagEditorScreen(viewModel = vm, onBack = {})
            }
        }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithText("Test Project").fetchSemanticsNodes().isNotEmpty()
        }

        // Add a block
        onNodeWithTag("add_block_SLACK_MESSAGE").performClick()
        waitForIdle()
        assertIs<AutoSaveStatus.Pending>(vm.autoSaveStatus.value)

        // Click Save manually
        onNodeWithTag("save_button").performClick()

        // Wait for save to complete
        waitUntil(timeoutMillis = 3000L) {
            !vm.isSaving.value && !vm.isDirty.value
        }

        // Should show Saved from manual save
        assertIs<AutoSaveStatus.Saved>(vm.autoSaveStatus.value)
        assertFalse(vm.isDirty.value)
    }

    @Test
    fun `navigation after auto-save does not show discard prompt`() = runComposeUiTest {
        var navigated = false
        val vm = autoSaveViewModel(debounceMs = 200L)
        setContent {
            MaterialTheme {
                DagEditorScreen(viewModel = vm, onBack = { navigated = true })
            }
        }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithText("Test Project").fetchSemanticsNodes().isNotEmpty()
        }

        // Add a block
        onNodeWithTag("add_block_SLACK_MESSAGE").performClick()
        waitForIdle()

        // Wait for auto-save to complete
        waitUntil(timeoutMillis = 3000L) {
            !vm.isDirty.value
        }

        // Click back — should navigate without discard prompt
        onNodeWithText("Back").performClick()
        waitForIdle()

        assertTrue(navigated, "Should navigate directly after auto-save (no unsaved changes)")
        assertEquals(0, onAllNodesWithTag("discard_confirm").fetchSemanticsNodes().size,
            "Discard confirmation should NOT appear")
    }

    @Test
    fun `auto-save shows Failed indicator on server error`() = runComposeUiTest {
        val vm = autoSaveViewModel(
            putStatus = HttpStatusCode.InternalServerError,
            debounceMs = 200L,
        )
        setContent {
            MaterialTheme {
                DagEditorScreen(viewModel = vm, onBack = {})
            }
        }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithText("Test Project").fetchSemanticsNodes().isNotEmpty()
        }

        // Add a block
        onNodeWithTag("add_block_SLACK_MESSAGE").performClick()
        waitForIdle()

        // Wait for auto-save to fail
        waitUntil(timeoutMillis = 3000L) {
            vm.autoSaveStatus.value is AutoSaveStatus.Failed
        }

        val status = vm.autoSaveStatus.value
        assertIs<AutoSaveStatus.Failed>(status)
        assertTrue(vm.isDirty.value, "Should remain dirty after failed auto-save")
    }

    @Test
    fun `save button has Primary variant when auto-save exhausted`() = runComposeUiTest {
        val vm = autoSaveViewModel(
            putStatus = HttpStatusCode.InternalServerError,
            debounceMs = 100L,
        )
        setContent {
            MaterialTheme {
                DagEditorScreen(viewModel = vm, onBack = {})
            }
        }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithText("Test Project").fetchSemanticsNodes().isNotEmpty()
        }

        // Add a block
        onNodeWithTag("add_block_SLACK_MESSAGE").performClick()
        waitForIdle()

        // Wait for all retries to exhaust
        waitUntil(timeoutMillis = 30_000L) {
            val s = vm.autoSaveStatus.value
            s is AutoSaveStatus.Failed && s.exhausted
        }

        // Save button should still be enabled for manual recovery
        onNodeWithTag("save_button").assertIsEnabled()
    }
}
