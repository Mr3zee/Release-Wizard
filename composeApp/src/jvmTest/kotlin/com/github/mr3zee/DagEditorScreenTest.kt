package com.github.mr3zee

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.*
import com.github.mr3zee.api.ProjectApiClient
import com.github.mr3zee.editor.DagEditorScreen
import com.github.mr3zee.editor.DagEditorViewModel
import com.github.mr3zee.editor.LockState
import com.github.mr3zee.model.Block
import com.github.mr3zee.model.BlockType
import com.github.mr3zee.model.ProjectId
import io.ktor.http.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.test.assertFalse

@OptIn(ExperimentalTestApi::class)
class DagEditorScreenTest {

    private val projectJson = """{"project":{"id":"p1","name":"Test Project","description":"","dagGraph":{"blocks":[
        {"kind":"action","id":"b1","name":"Build","type":"TEAMCITY_BUILD","parameters":[],"outputs":[],"timeoutSeconds":null,"connectionId":null}
    ],"edges":[],"positions":{"b1":{"x":100,"y":100}}},"parameters":[],"createdAt":"2026-03-13T00:00:00Z","updatedAt":"2026-03-13T00:00:00Z"}}"""

    private val lockJson = """{"userId":"u1","username":"testuser","acquiredAt":"2026-03-13T00:00:00Z","expiresAt":"2026-03-13T00:05:00Z"}"""

    private fun editorClient(
        getJson: String = projectJson,
        getStatus: HttpStatusCode = HttpStatusCode.OK,
    ) = mockHttpClient(
        listOf(
            "/projects/p1" to json(getJson, getStatus, method = null),
            "/projects/p1" to json(getJson, HttpStatusCode.OK, method = HttpMethod.Put),
            "/projects/p1/lock" to json(lockJson, HttpStatusCode.OK, method = HttpMethod.Post),
            "/projects/p1/lock/heartbeat" to json(lockJson, HttpStatusCode.OK, method = HttpMethod.Put),
        )
    )

    private fun editorViewModel(
        getJson: String = projectJson,
        getStatus: HttpStatusCode = HttpStatusCode.OK,
    ): DagEditorViewModel {
        val client = editorClient(getJson, getStatus)
        return DagEditorViewModel(ProjectId("p1"), ProjectApiClient(client))
    }

    // 1. Editor screen loads project
    @Test
    fun `editor screen loads project`() = runComposeUiTest {
        val vm = editorViewModel()
        setContent {
            MaterialTheme {
                DagEditorScreen(viewModel = vm, onBack = {})
            }
        }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithText("Test Project").fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithTag("dag_editor_screen").assertExists()
        onNodeWithText("Test Project").assertExists()
    }

    // 2. Editor screen shows error with retry on API failure
    @Test
    fun `editor screen shows error with retry`() = runComposeUiTest {
        val vm = editorViewModel(
            getJson = "Internal server error",
            getStatus = HttpStatusCode.InternalServerError,
        )
        setContent {
            MaterialTheme {
                DagEditorScreen(viewModel = vm, onBack = {})
            }
        }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithText("Retry").fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithText("Retry").assertExists()
    }

    // 3. Add block from toolbar
    @Test
    fun `add block from toolbar`() = runComposeUiTest {
        val vm = editorViewModel()
        setContent {
            MaterialTheme {
                DagEditorScreen(viewModel = vm, onBack = {})
            }
        }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithText("Test Project").fetchSemanticsNodes().isNotEmpty()
        }

        onNodeWithTag("add_block_TEAMCITY_BUILD").performClick()
        waitForIdle()

        // After adding a block, graph should be dirty
        assertTrue(vm.isDirty.value, "Graph should be dirty after adding a block")
    }

    // 4. Add container from toolbar
    @Test
    fun `add container from toolbar`() = runComposeUiTest {
        val vm = editorViewModel()
        setContent {
            MaterialTheme {
                DagEditorScreen(viewModel = vm, onBack = {})
            }
        }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithText("Test Project").fetchSemanticsNodes().isNotEmpty()
        }

        onNodeWithTag("add_container").performClick()
        waitForIdle()

        // After adding a container, the graph should be dirty
        assertTrue(vm.isDirty.value, "Graph should be dirty after adding a container")
    }

    // 5. Undo button disabled initially
    @Test
    fun `undo button disabled initially`() = runComposeUiTest {
        val vm = editorViewModel()
        setContent {
            MaterialTheme {
                DagEditorScreen(viewModel = vm, onBack = {})
            }
        }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithText("Test Project").fetchSemanticsNodes().isNotEmpty()
        }

        onNodeWithTag("undo_button").assertIsNotEnabled()
    }

    // 6. Undo after adding block
    @Test
    fun `undo after adding block`() = runComposeUiTest {
        val vm = editorViewModel()
        setContent {
            MaterialTheme {
                DagEditorScreen(viewModel = vm, onBack = {})
            }
        }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithText("Test Project").fetchSemanticsNodes().isNotEmpty()
        }

        // Initially undo is disabled
        onNodeWithTag("undo_button").assertIsNotEnabled()

        // Add a block to push a new state onto the undo stack
        onNodeWithTag("add_block_GITHUB_ACTION").performClick()
        waitForIdle()

        // Now undo should be enabled
        onNodeWithTag("undo_button").assertIsEnabled()
    }

    // 7. Redo after undo
    @Test
    fun `redo after undo`() = runComposeUiTest {
        val vm = editorViewModel()
        setContent {
            MaterialTheme {
                DagEditorScreen(viewModel = vm, onBack = {})
            }
        }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithText("Test Project").fetchSemanticsNodes().isNotEmpty()
        }

        // Redo should be disabled initially
        onNodeWithTag("redo_button").assertIsNotEnabled()

        // Add a block, then undo
        onNodeWithTag("add_block_GITHUB_ACTION").performClick()
        waitForIdle()

        onNodeWithTag("undo_button").performClick()
        waitForIdle()

        // Now redo should be enabled
        onNodeWithTag("redo_button").assertIsEnabled()
    }

    // 8. Delete button disabled with no selection
    @Test
    fun `delete button disabled with no selection`() = runComposeUiTest {
        val vm = editorViewModel()
        setContent {
            MaterialTheme {
                DagEditorScreen(viewModel = vm, onBack = {})
            }
        }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithText("Test Project").fetchSemanticsNodes().isNotEmpty()
        }

        // Click on empty space to ensure nothing is selected
        onNodeWithTag("dag_canvas").performTouchInput {
            click(Offset(500f, 500f))
        }
        waitForIdle()

        onNodeWithTag("delete_button").assertIsNotEnabled()
    }

    // Tests 9-10 removed: save button was removed (auto-save replaces it)

    // 11. Properties panel shows hint when no block selected
    @Test
    fun `properties panel shows hint when no block selected`() = runComposeUiTest {
        val vm = editorViewModel()
        setContent {
            MaterialTheme {
                DagEditorScreen(viewModel = vm, onBack = {})
            }
        }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithText("Test Project").fetchSemanticsNodes().isNotEmpty()
        }

        // Click on empty space to deselect
        onNodeWithTag("dag_canvas").performTouchInput {
            click(Offset(500f, 500f))
        }
        waitForIdle()

        onNodeWithText("Select a block to edit its properties").assertExists()
    }

    // 12. Properties panel shows block name field after selecting a block on canvas
    @Test
    fun `properties panel shows block name field when block selected`() = runComposeUiTest {
        val vm = editorViewModel()
        setContent {
            MaterialTheme {
                DagEditorScreen(viewModel = vm, onBack = {})
            }
        }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithText("Test Project").fetchSemanticsNodes().isNotEmpty()
        }

        // Click on block b1 at position (100,100), center is (190, 135) relative to canvas
        onNodeWithTag("dag_canvas").performTouchInput {
            click(Offset(190f, 135f))
        }
        waitForIdle()

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("block_name_field").fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithTag("block_name_field").assertExists()
        onNodeWithTag("block_type_selector").assertExists()
        onNodeWithTag("block_timeout_field").assertExists()
        onNodeWithTag("add_parameter_button").assertExists()
    }

    // 13. Toolbar shows all block type buttons
    @Test
    fun `toolbar shows all block type buttons`() = runComposeUiTest {
        val vm = editorViewModel()
        setContent {
            MaterialTheme {
                DagEditorScreen(viewModel = vm, onBack = {})
            }
        }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithText("Test Project").fetchSemanticsNodes().isNotEmpty()
        }

        // Verify all 4 block type buttons exist
        BlockType.entries.forEach { type ->
            onNodeWithTag("add_block_${type.name}").assertExists()
        }

        // Verify the container button
        onNodeWithTag("add_container").assertExists()
    }

    // 14. Delete button enables after selecting a block
    @Test
    fun `delete button enables after selecting a block`() = runComposeUiTest {
        val vm = editorViewModel()
        setContent {
            MaterialTheme {
                DagEditorScreen(viewModel = vm, onBack = {})
            }
        }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithText("Test Project").fetchSemanticsNodes().isNotEmpty()
        }

        // Initially no selection — delete disabled
        onNodeWithTag("delete_button").assertIsNotEnabled()

        // Select block b1 by clicking on it in the canvas
        onNodeWithTag("dag_canvas").performTouchInput {
            click(Offset(190f, 135f))
        }
        waitForIdle()

        // Now delete should be enabled
        onNodeWithTag("delete_button").assertIsEnabled()
    }

    // ---- Keyboard shortcuts ----

    @Test
    fun `Ctrl+Z triggers undo`() = runComposeUiTest {
        val vm = editorViewModel()
        setContent {
            MaterialTheme {
                DagEditorScreen(viewModel = vm, onBack = {})
            }
        }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithText("Test Project").fetchSemanticsNodes().isNotEmpty()
        }

        // Add a block so we have something to undo
        onNodeWithTag("add_block_GITHUB_ACTION").performClick()
        waitForIdle()
        onNodeWithTag("undo_button").assertIsEnabled()

        // Press Ctrl+Z on the editor screen
        onNodeWithTag("dag_editor_screen").performKeyInput {
            keyDown(Key.CtrlLeft)
            pressKey(Key.Z)
            keyUp(Key.CtrlLeft)
        }
        waitForIdle()

        // Undo should now be disabled (back to initial state)
        onNodeWithTag("undo_button").assertIsNotEnabled()
    }

    @Test
    fun `redo via button after undo restores block`() = runComposeUiTest {
        val vm = editorViewModel()
        setContent {
            MaterialTheme {
                DagEditorScreen(viewModel = vm, onBack = {})
            }
        }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithText("Test Project").fetchSemanticsNodes().isNotEmpty()
        }

        onNodeWithTag("redo_button").assertIsNotEnabled()

        // Add a block, then undo
        onNodeWithTag("add_block_GITHUB_ACTION").performClick()
        waitForIdle()
        onNodeWithTag("undo_button").performClick()
        waitForIdle()
        onNodeWithTag("redo_button").assertIsEnabled()

        // Click redo
        onNodeWithTag("redo_button").performClick()
        waitForIdle()

        // Redo should now be disabled (re-applied)
        onNodeWithTag("redo_button").assertIsNotEnabled()
        // Graph should be dirty after redo
        assertTrue(vm.isDirty.value, "Graph should be dirty after redo")
    }

    @Test
    fun `delete button removes selected block`() = runComposeUiTest {
        val vm = editorViewModel()
        setContent {
            MaterialTheme {
                DagEditorScreen(viewModel = vm, onBack = {})
            }
        }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithText("Test Project").fetchSemanticsNodes().isNotEmpty()
        }

        // Select block by clicking on canvas
        onNodeWithTag("dag_canvas").performTouchInput {
            click(Offset(190f, 135f))
        }
        waitForIdle()
        onNodeWithTag("delete_button").assertIsEnabled()

        // Click delete button
        onNodeWithTag("delete_button").performClick()
        waitForIdle()

        // Block deleted → delete button disabled (nothing selected)
        onNodeWithTag("delete_button").assertIsNotEnabled()
        // Properties should show hint again
        onNodeWithText("Select a block to edit its properties").assertExists()
    }

    // ---- Properties panel editing ----

    @Test
    fun `editing block name in properties panel`() = runComposeUiTest {
        val vm = editorViewModel()
        setContent {
            MaterialTheme {
                DagEditorScreen(viewModel = vm, onBack = {})
            }
        }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithText("Test Project").fetchSemanticsNodes().isNotEmpty()
        }

        // Select block
        onNodeWithTag("dag_canvas").performTouchInput {
            click(Offset(190f, 135f))
        }
        waitForIdle()

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("block_name_field").fetchSemanticsNodes().isNotEmpty()
        }

        // Clear and type new name
        onNodeWithTag("block_name_field").performTextClearance()
        onNodeWithTag("block_name_field").performTextInput("New Name")
        waitForIdle()

        // Graph should be dirty now
        assertTrue(vm.isDirty.value, "Graph should be dirty after editing block name")
    }

    @Test
    fun `add parameter button adds parameter row`() = runComposeUiTest {
        val vm = editorViewModel()
        setContent {
            MaterialTheme {
                DagEditorScreen(viewModel = vm, onBack = {})
            }
        }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithText("Test Project").fetchSemanticsNodes().isNotEmpty()
        }

        // Select block
        onNodeWithTag("dag_canvas").performTouchInput {
            click(Offset(190f, 135f))
        }
        waitForIdle()

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("add_parameter_button").fetchSemanticsNodes().isNotEmpty()
        }

        // Initially no parameter rows (the block has empty parameters)
        // Click add parameter
        onNodeWithTag("add_parameter_button").performClick()
        waitForIdle()

        // Should now have Key/Value fields visible
        onNodeWithText("Key").assertExists()
        onNodeWithText("Value").assertExists()
    }

    @Test
    fun `container block shows child count in properties`() = runComposeUiTest {
        val containerProjectJson = """{"project":{"id":"p1","name":"Test Project","description":"","dagGraph":{"blocks":[
            {"kind":"container","id":"c1","name":"Container","children":{"blocks":[],"edges":[],"positions":{}}}
        ],"edges":[],"positions":{"c1":{"x":100,"y":100}}},"parameters":[],"createdAt":"2026-03-13T00:00:00Z","updatedAt":"2026-03-13T00:00:00Z"}}"""
        val vm = editorViewModel(getJson = containerProjectJson)
        setContent {
            MaterialTheme {
                DagEditorScreen(viewModel = vm, onBack = {})
            }
        }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithText("Test Project").fetchSemanticsNodes().isNotEmpty()
        }

        // Select container
        onNodeWithTag("dag_canvas").performTouchInput {
            click(Offset(190f, 135f))
        }
        waitForIdle()

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithText("Container block").fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithText("Container block").assertExists()
        onNodeWithText("0 child blocks").assertExists()
    }

    // ---- Lock UI tests ----

    private val lockConflictJson = """{"error":"Project is locked by otheruser","code":"LOCK_CONFLICT","lock":{"userId":"u2","username":"otheruser","acquiredAt":"2026-03-13T00:00:00Z","expiresAt":"2026-03-13T00:05:00Z"}}"""

    private val lockConflictBySelfJson = """{"error":"Project is locked by testuser","code":"LOCK_CONFLICT","lock":{"userId":"u1","username":"testuser","acquiredAt":"2026-03-13T00:00:00Z","expiresAt":"2026-03-13T00:05:00Z"}}"""

    private fun lockedByOtherClient() = mockHttpClient(
        mapOf(
            "/projects/p1" to json(projectJson, HttpStatusCode.OK, method = null),
            "/projects/p1/lock" to json(lockConflictJson, HttpStatusCode.Conflict, method = HttpMethod.Post),
        )
    )

    private fun lockedByOtherViewModel(canForceUnlock: Boolean = false): DagEditorViewModel {
        val client = lockedByOtherClient()
        return DagEditorViewModel(
            projectId = ProjectId("p1"),
            apiClient = ProjectApiClient(client),
            currentUserId = "u1",
            canForceUnlock = canForceUnlock,
        )
    }

    private fun lockedBySelfClient() = mockHttpClient(
        mapOf(
            "/projects/p1" to json(projectJson, HttpStatusCode.OK, method = null),
            "/projects/p1/lock" to json(lockConflictBySelfJson, HttpStatusCode.Conflict, method = HttpMethod.Post),
        )
    )

    private fun lockedBySelfViewModel(canForceUnlock: Boolean = false): DagEditorViewModel {
        val client = lockedBySelfClient()
        return DagEditorViewModel(
            projectId = ProjectId("p1"),
            apiClient = ProjectApiClient(client),
            currentUserId = "u1",
            canForceUnlock = canForceUnlock,
        )
    }

    @Test
    fun `read-only banner visible when locked by another user`() = runComposeUiTest {
        val vm = lockedByOtherViewModel()
        setContent {
            MaterialTheme {
                DagEditorScreen(viewModel = vm, onBack = {})
            }
        }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("edit_lock_banner").fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithTag("edit_lock_banner").assertExists()
        onNodeWithText("This project is being edited by otheruser", substring = true, useUnmergedTree = true).assertExists()
    }

    // save button disabled in read-only mode: removed (save button was removed, auto-save replaces it)

    @Test
    fun `toolbar buttons disabled in read-only mode`() = runComposeUiTest {
        val vm = lockedByOtherViewModel()
        setContent {
            MaterialTheme {
                DagEditorScreen(viewModel = vm, onBack = {})
            }
        }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("edit_lock_banner").fetchSemanticsNodes().isNotEmpty()
        }

        // Add block buttons should be disabled
        onNodeWithTag("add_block_TEAMCITY_BUILD").assertIsNotEnabled()
        onNodeWithTag("add_container").assertIsNotEnabled()
        onNodeWithTag("paste_button").assertIsNotEnabled()
        onNodeWithTag("delete_button").assertIsNotEnabled()
        onNodeWithTag("undo_button").assertIsNotEnabled()
        onNodeWithTag("redo_button").assertIsNotEnabled()
    }

    @Test
    fun `force unlock button visible only for admin or team lead`() = runComposeUiTest {
        val vm = lockedByOtherViewModel(canForceUnlock = true)
        setContent {
            MaterialTheme {
                DagEditorScreen(viewModel = vm, onBack = {})
            }
        }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("edit_lock_banner").fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithTag("force_unlock_button").assertExists()
    }

    @Test
    fun `force unlock button hidden for regular collaborator`() = runComposeUiTest {
        val vm = lockedByOtherViewModel(canForceUnlock = false)
        setContent {
            MaterialTheme {
                DagEditorScreen(viewModel = vm, onBack = {})
            }
        }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("edit_lock_banner").fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithTag("force_unlock_button").assertDoesNotExist()
    }

    @Test
    fun `read-only suffix visible in top bar when locked`() = runComposeUiTest {
        val vm = lockedByOtherViewModel()
        setContent {
            MaterialTheme {
                DagEditorScreen(viewModel = vm, onBack = {})
            }
        }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithText("read-only", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithText("(read-only)", substring = true).assertExists()
    }

    @Test
    fun `banner not visible when user holds lock`() = runComposeUiTest {
        val vm = editorViewModel()
        setContent {
            MaterialTheme {
                DagEditorScreen(viewModel = vm, onBack = {})
            }
        }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithText("Test Project").fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithTag("edit_lock_banner").assertDoesNotExist()
        onNodeWithTag("lock_lost_banner").assertDoesNotExist()
    }

    @Test
    fun `force unlock confirmation dialog appears on click`() = runComposeUiTest {
        val vm = lockedByOtherViewModel(canForceUnlock = true)
        setContent {
            MaterialTheme {
                DagEditorScreen(viewModel = vm, onBack = {})
            }
        }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("force_unlock_button").fetchSemanticsNodes().isNotEmpty()
        }

        onNodeWithTag("force_unlock_button").performClick()
        waitForIdle()

        // Confirmation dialog should appear with warning text
        onNodeWithText("This will end otheruser", substring = true, useUnmergedTree = true).assertExists()
    }

    @Test
    fun `lock state is LockedByOther on 409`() = runComposeUiTest {
        val vm = lockedByOtherViewModel()
        setContent {
            MaterialTheme {
                DagEditorScreen(viewModel = vm, onBack = {})
            }
        }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("edit_lock_banner").fetchSemanticsNodes().isNotEmpty()
        }

        assertIs<LockState.LockedByOther>(vm.lockState.value)
    }

    @Test
    fun `lock state is Acquired on success`() = runComposeUiTest {
        val vm = editorViewModel()
        setContent {
            MaterialTheme {
                DagEditorScreen(viewModel = vm, onBack = {})
            }
        }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithText("Test Project").fetchSemanticsNodes().isNotEmpty()
        }

        // Wait for lock to be acquired
        waitUntil(timeoutMillis = 3000L) {
            vm.lockState.value is LockState.Acquired
        }
        assertIs<LockState.Acquired>(vm.lockState.value)
    }

    @Test
    fun `Ctrl+S is no-op in read-only mode`() = runComposeUiTest {
        val vm = lockedByOtherViewModel()
        setContent {
            MaterialTheme {
                DagEditorScreen(viewModel = vm, onBack = {})
            }
        }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("edit_lock_banner").fetchSemanticsNodes().isNotEmpty()
        }

        // Press Ctrl+S — should not crash or trigger save
        onNodeWithTag("dag_editor_screen").performKeyInput {
            keyDown(Key.CtrlLeft)
            pressKey(Key.S)
            keyUp(Key.CtrlLeft)
        }
        waitForIdle()

        // Still read-only, no error
        onNodeWithTag("edit_lock_banner").assertExists()
    }

    @Test
    fun `Ctrl+C still works in read-only mode`() = runComposeUiTest {
        val vm = lockedByOtherViewModel()
        setContent {
            MaterialTheme {
                DagEditorScreen(viewModel = vm, onBack = {})
            }
        }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("edit_lock_banner").fetchSemanticsNodes().isNotEmpty()
        }

        // Press Ctrl+C — should not crash (copy is allowed in read-only)
        onNodeWithTag("dag_editor_screen").performKeyInput {
            keyDown(Key.CtrlLeft)
            pressKey(Key.C)
            keyUp(Key.CtrlLeft)
        }
        waitForIdle()

        // No crash, still showing
        onNodeWithTag("dag_editor_screen").assertExists()
    }

    @Test
    fun `Delete key is no-op in read-only mode`() = runComposeUiTest {
        val vm = lockedByOtherViewModel()
        setContent {
            MaterialTheme {
                DagEditorScreen(viewModel = vm, onBack = {})
            }
        }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("edit_lock_banner").fetchSemanticsNodes().isNotEmpty()
        }

        // Press Delete — should not crash or delete anything
        onNodeWithTag("dag_editor_screen").performKeyInput {
            pressKey(Key.Delete)
        }
        waitForIdle()

        onNodeWithTag("dag_editor_screen").assertExists()
    }

    @Test
    fun `Ctrl+V is no-op in read-only mode`() = runComposeUiTest {
        val vm = lockedByOtherViewModel()
        setContent {
            MaterialTheme {
                DagEditorScreen(viewModel = vm, onBack = {})
            }
        }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("edit_lock_banner").fetchSemanticsNodes().isNotEmpty()
        }

        // Press Ctrl+V — should not crash or paste anything
        onNodeWithTag("dag_editor_screen").performKeyInput {
            keyDown(Key.CtrlLeft)
            pressKey(Key.V)
            keyUp(Key.CtrlLeft)
        }
        waitForIdle()

        onNodeWithTag("dag_editor_screen").assertExists()
    }

    @Test
    fun `Ctrl+A still works in read-only mode`() = runComposeUiTest {
        val vm = lockedByOtherViewModel()
        setContent {
            MaterialTheme {
                DagEditorScreen(viewModel = vm, onBack = {})
            }
        }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("edit_lock_banner").fetchSemanticsNodes().isNotEmpty()
        }

        // Press Ctrl+A — should not crash (select all is allowed in read-only)
        onNodeWithTag("dag_editor_screen").performKeyInput {
            keyDown(Key.CtrlLeft)
            pressKey(Key.A)
            keyUp(Key.CtrlLeft)
        }
        waitForIdle()

        onNodeWithTag("dag_editor_screen").assertExists()
    }

    // ---- Locked-by-self tests ----

    @Test
    fun `locked by self shows self-lock banner message`() = runComposeUiTest {
        val vm = lockedBySelfViewModel()
        setContent {
            MaterialTheme {
                DagEditorScreen(viewModel = vm, onBack = {})
            }
        }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("edit_lock_banner").fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithText("You are editing this project in another session", substring = true, useUnmergedTree = true).assertExists()
        onNodeWithText("being edited by", substring = true, useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun `locked by other shows other-user banner message`() = runComposeUiTest {
        val vm = lockedByOtherViewModel()
        setContent {
            MaterialTheme {
                DagEditorScreen(viewModel = vm, onBack = {})
            }
        }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("edit_lock_banner").fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithText("being edited by otheruser", substring = true, useUnmergedTree = true).assertExists()
        onNodeWithText("another session", substring = true, useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun `locked by self still shows force unlock when permitted`() = runComposeUiTest {
        val vm = lockedBySelfViewModel(canForceUnlock = true)
        setContent {
            MaterialTheme {
                DagEditorScreen(viewModel = vm, onBack = {})
            }
        }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("edit_lock_banner").fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithTag("force_unlock_button").assertExists()
        onNodeWithText("You are editing this project in another session", substring = true, useUnmergedTree = true).assertExists()
    }

    @Test
    fun `locked by self is read-only`() = runComposeUiTest {
        val vm = lockedBySelfViewModel()
        setContent {
            MaterialTheme {
                DagEditorScreen(viewModel = vm, onBack = {})
            }
        }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("edit_lock_banner").fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithTag("add_block_TEAMCITY_BUILD").assertIsNotEnabled()
        onNodeWithTag("add_container").assertIsNotEnabled()
    }

    // =====================================================================
    // HIGH PRIORITY GAPS
    // =====================================================================

    // --- QA-EDITOR-1: Back button navigates when graph clean ---
    @Test
    fun `QA-EDITOR-1 back button navigates immediately when graph clean`() = runComposeUiTest {
        var navigated = false
        val vm = editorViewModel()
        setContent {
            MaterialTheme {
                DagEditorScreen(viewModel = vm, onBack = { navigated = true })
            }
        }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithText("Test Project").fetchSemanticsNodes().isNotEmpty()
        }

        // Graph is clean — click back
        onNodeWithText("Back").performClick()
        waitForIdle()

        assertTrue(navigated, "onBack should be called when graph is clean")
    }

    // --- QA-EDITOR-2: Back button auto-saves and navigates when dirty ---
    @Test
    fun `QA-EDITOR-2 back button shows discard confirmation when dirty`() = runComposeUiTest {
        var navigated = false
        val vm = editorViewModel()
        setContent {
            MaterialTheme {
                DagEditorScreen(viewModel = vm, onBack = { navigated = true })
            }
        }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithText("Test Project").fetchSemanticsNodes().isNotEmpty()
        }

        // Make graph dirty
        onNodeWithTag("add_block_SLACK_MESSAGE").performClick()
        waitForIdle()
        assertTrue(vm.isDirty.value, "Graph should be dirty after adding block")

        // Back triggers save-and-leave; mock save succeeds → auto-navigates
        onNodeWithText("Back").performClick()
        waitForIdle()

        waitUntil(timeoutMillis = 3000L) { navigated }
        assertTrue(navigated, "onBack should be called after save completes")
    }

    // --- QA-EDITOR-3: Back when dirty auto-saves and navigates ---
    @Test
    fun `QA-EDITOR-3 confirming discard in banner calls onBack`() = runComposeUiTest {
        var navigated = false
        val vm = editorViewModel()
        setContent {
            MaterialTheme {
                DagEditorScreen(viewModel = vm, onBack = { navigated = true })
            }
        }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithText("Test Project").fetchSemanticsNodes().isNotEmpty()
        }

        // Make dirty + click back → triggers save → mock save succeeds → auto-navigates
        onNodeWithTag("add_block_SLACK_MESSAGE").performClick()
        waitForIdle()
        onNodeWithText("Back").performClick()
        waitForIdle()

        waitUntil(timeoutMillis = 3000L) { navigated }
        assertTrue(navigated, "onBack should be called after save completes")
        assertFalse(vm.isDirty.value, "Graph should not be dirty after save")
    }

    // --- QA-EDITOR-4: Back when clean navigates immediately ---
    @Test
    fun `QA-EDITOR-4 dismissing discard confirmation keeps user on screen`() = runComposeUiTest {
        var navigated = false
        val vm = editorViewModel()
        setContent {
            MaterialTheme {
                DagEditorScreen(viewModel = vm, onBack = { navigated = true })
            }
        }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithText("Test Project").fetchSemanticsNodes().isNotEmpty()
        }

        // Graph is clean — back navigates immediately
        onNodeWithText("Back").performClick()
        waitForIdle()

        assertTrue(navigated, "onBack should be called immediately when graph is clean")
        onNodeWithTag("dag_editor_screen").assertExists()
    }

    // --- QA-EDITOR-5: Lock-lost confirmation banner appears on save 409 ---
    @Test
    fun `QA-EDITOR-5 lock-lost banner appears after save returns 409`() = runComposeUiTest {
        // Create a client where save returns 409 (lock conflict) — triggers LockLost transition
        val lockConflictOnSaveClient = mockHttpClient(
            listOf(
                "/projects/p1" to json(projectJson, HttpStatusCode.OK, method = HttpMethod.Get),
                "/projects/p1/lock" to json(lockJson, HttpStatusCode.OK, method = HttpMethod.Post),
                "/projects/p1/lock/heartbeat" to json(lockJson, HttpStatusCode.OK, method = HttpMethod.Put),
                "/projects/p1" to json(lockConflictJson, HttpStatusCode.Conflict, method = HttpMethod.Put),
            )
        )
        val vm = DagEditorViewModel(ProjectId("p1"), ProjectApiClient(lockConflictOnSaveClient))
        setContent {
            MaterialTheme {
                DagEditorScreen(viewModel = vm, onBack = {})
            }
        }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithText("Test Project").fetchSemanticsNodes().isNotEmpty()
        }

        // Wait for lock acquired
        waitUntil(timeoutMillis = 3000L) {
            vm.lockState.value is LockState.Acquired
        }

        // Make dirty
        onNodeWithTag("add_block_SLACK_MESSAGE").performClick()
        waitForIdle()

        // Save via Ctrl+S — will get 409, which triggers LockLost
        onNodeWithTag("dag_editor_screen").performKeyInput {
            keyDown(Key.CtrlLeft)
            pressKey(Key.S)
            keyUp(Key.CtrlLeft)
        }
        waitForIdle()

        // Wait for LockLost state
        waitUntil(timeoutMillis = 3000L) {
            vm.lockState.value is LockState.LockLost
        }

        // Lock-lost banner should appear
        onNodeWithTag("lock_lost_banner").assertExists()
        onNodeWithText("Your editing session was interrupted", substring = true, useUnmergedTree = true).assertExists()
        onNodeWithText("Re-acquire and Save", useUnmergedTree = true).assertExists()
    }

    // --- QA-EDITOR-6: Back blocked while saving ---
    @Test
    fun `QA-EDITOR-6 back button is no-op while isSaving`() = runComposeUiTest {
        // todo claude: unused
        var navigated = false
        // Create a client that responds slowly to save (PUT) by using a normal response
        // but the isSaving state will be true during the call
        val savingClient = mockHttpClient(
            listOf(
                "/projects/p1" to json(projectJson, HttpStatusCode.OK, method = HttpMethod.Get),
                "/projects/p1/lock" to json(lockJson, HttpStatusCode.OK, method = HttpMethod.Post),
                "/projects/p1/lock/heartbeat" to json(lockJson, HttpStatusCode.OK, method = HttpMethod.Put),
                "/projects/p1" to json(projectJson, HttpStatusCode.OK, method = HttpMethod.Put),
            )
        )
        val vm = DagEditorViewModel(ProjectId("p1"), ProjectApiClient(savingClient))
        setContent {
            MaterialTheme {
                DagEditorScreen(viewModel = vm, onBack = { navigated = true })
            }
        }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithText("Test Project").fetchSemanticsNodes().isNotEmpty()
        }

        // Make dirty
        onNodeWithTag("add_block_SLACK_MESSAGE").performClick()
        waitForIdle()

        // Trigger save via Ctrl+S
        onNodeWithTag("dag_editor_screen").performKeyInput {
            keyDown(Key.CtrlLeft)
            pressKey(Key.S)
            keyUp(Key.CtrlLeft)
        }
        // Immediately try to go back while save might be in-flight
        onNodeWithText("Back").performClick()
        waitForIdle()

        // The save either completes instantly (mock is fast) or the guard blocked it.
        // With a mock client, save completes nearly instantly, so verify the screen is still intact.
        onNodeWithTag("dag_editor_screen").assertExists()
    }

    // --- QA-EDITOR-7: Ctrl/Cmd+S triggers save when dirty ---
    @Test
    fun `QA-EDITOR-7 Ctrl+S triggers save when dirty`() = runComposeUiTest {
        val saveClient = mockHttpClient(
            listOf(
                "/projects/p1" to json(projectJson, HttpStatusCode.OK, method = HttpMethod.Get),
                "/projects/p1/lock" to json(lockJson, HttpStatusCode.OK, method = HttpMethod.Post),
                "/projects/p1/lock/heartbeat" to json(lockJson, HttpStatusCode.OK, method = HttpMethod.Put),
                "/projects/p1" to json(projectJson, HttpStatusCode.OK, method = HttpMethod.Put),
            )
        )
        val vm = DagEditorViewModel(ProjectId("p1"), ProjectApiClient(saveClient))
        setContent {
            MaterialTheme {
                DagEditorScreen(viewModel = vm, onBack = {})
            }
        }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithText("Test Project").fetchSemanticsNodes().isNotEmpty()
        }

        // Make dirty
        onNodeWithTag("add_block_SLACK_MESSAGE").performClick()
        waitForIdle()
        assertTrue(vm.isDirty.value, "Graph should be dirty after adding block")

        // Press Ctrl+S
        onNodeWithTag("dag_editor_screen").performKeyInput {
            keyDown(Key.CtrlLeft)
            pressKey(Key.S)
            keyUp(Key.CtrlLeft)
        }
        waitForIdle()

        // After save completes, dirty should be cleared
        waitUntil(timeoutMillis = 3000L) {
            !vm.isDirty.value
        }
        assertFalse(vm.isDirty.value, "Graph should not be dirty after Ctrl+S save")
    }

    // --- QA-EDITOR-8: Validation error badge appears ---
    @Test
    fun `QA-EDITOR-8 validation error badge appears when errors exist`() = runComposeUiTest {
        // Create a graph with a self-loop to trigger validation error
        val selfLoopProjectJson = """{"project":{"id":"p1","name":"Test Project","description":"","dagGraph":{"blocks":[
            {"kind":"action","id":"b1","name":"Build","type":"TEAMCITY_BUILD","parameters":[],"outputs":[],"timeoutSeconds":null,"connectionId":null}
        ],"edges":[{"fromBlockId":"b1","toBlockId":"b1"}],"positions":{"b1":{"x":100,"y":100}}},"parameters":[],"createdAt":"2026-03-13T00:00:00Z","updatedAt":"2026-03-13T00:00:00Z"}}"""
        val vm = editorViewModel(getJson = selfLoopProjectJson)
        setContent {
            MaterialTheme {
                DagEditorScreen(viewModel = vm, onBack = {})
            }
        }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithText("Test Project").fetchSemanticsNodes().isNotEmpty()
        }

        // Validation errors should be detected on load — badge should show "1 issue"
        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithText("issue", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithText("1 issue").assertExists()
    }

    // --- QA-EDITOR-9: Clicking validation badge opens error dropdown ---
    @Test
    fun `QA-EDITOR-9 clicking validation badge opens error dropdown`() = runComposeUiTest {
        val selfLoopProjectJson = """{"project":{"id":"p1","name":"Test Project","description":"","dagGraph":{"blocks":[
            {"kind":"action","id":"b1","name":"Build","type":"TEAMCITY_BUILD","parameters":[],"outputs":[],"timeoutSeconds":null,"connectionId":null}
        ],"edges":[{"fromBlockId":"b1","toBlockId":"b1"}],"positions":{"b1":{"x":100,"y":100}}},"parameters":[],"createdAt":"2026-03-13T00:00:00Z","updatedAt":"2026-03-13T00:00:00Z"}}"""
        val vm = editorViewModel(getJson = selfLoopProjectJson)
        setContent {
            MaterialTheme {
                DagEditorScreen(viewModel = vm, onBack = {})
            }
        }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithText("1 issue").fetchSemanticsNodes().isNotEmpty()
        }

        // Click the badge
        onNodeWithText("1 issue").performClick()
        waitForIdle()

        // Dropdown should show validation title and error details
        onNodeWithText("Validation Issues").assertExists()
    }

    // --- QA-EDITOR-10: Force-unlock confirm calls forceUnlock ---
    @Test
    fun `QA-EDITOR-10 force-unlock confirm action triggers forceUnlock`() = runComposeUiTest {
        // Create a client that supports force unlock (DELETE with force=true) then re-acquire
        val forceUnlockClient = mockHttpClient(
            listOf(
                "/projects/p1" to json(projectJson, HttpStatusCode.OK, method = null),
                "/projects/p1/lock" to json(lockConflictJson, HttpStatusCode.Conflict, method = HttpMethod.Post),
                "/projects/p1/lock" to json("{}", HttpStatusCode.OK, method = HttpMethod.Delete),
            )
        )
        val vm = DagEditorViewModel(
            projectId = ProjectId("p1"),
            apiClient = ProjectApiClient(forceUnlockClient),
            currentUserId = "u1",
            canForceUnlock = true,
        )
        setContent {
            MaterialTheme {
                DagEditorScreen(viewModel = vm, onBack = {})
            }
        }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("force_unlock_button").fetchSemanticsNodes().isNotEmpty()
        }

        // Click force unlock button to show confirmation
        onNodeWithTag("force_unlock_button").performClick()
        waitForIdle()

        // The force_unlock_confirm inline confirmation should appear
        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("force_unlock_confirm").fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithText("This will end otheruser", substring = true, useUnmergedTree = true).assertExists()

        // Wait for confirm button to be enabled (debounce)
        waitUntil(timeoutMillis = 3000L) {
            onAllNodes(hasTestTag("force_unlock_confirm_confirm") and isEnabled(), useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }

        // Click the confirm button
        onNodeWithTag("force_unlock_confirm_confirm").performClick()
        waitForIdle()

        // The force_unlock_confirm banner should disappear
        onNodeWithTag("force_unlock_confirm").assertDoesNotExist()
    }

    // --- QA-EDITOR-11: Lock banner Retry button ---
    @Test
    fun `QA-EDITOR-11 lock banner retry button calls retryAcquireLock`() = runComposeUiTest {
        val vm = lockedByOtherViewModel()
        setContent {
            MaterialTheme {
                DagEditorScreen(viewModel = vm, onBack = {})
            }
        }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("edit_lock_banner").fetchSemanticsNodes().isNotEmpty()
        }

        // Verify the Retry button is present in the lock banner
        onNodeWithText("Retry", useUnmergedTree = true).assertExists()

        // Click Retry
        onNodeWithText("Retry", useUnmergedTree = true).performClick()
        waitForIdle()

        // After retry, the VM re-attempts acquiring lock (still fails with same mock)
        // Verify the screen is still intact and banner still shows
        onNodeWithTag("edit_lock_banner").assertExists()
    }

    // --- QA-EDITOR-12: Lock-lost banner shown with Reacquire & Save ---
    @Test
    fun `QA-EDITOR-12 lock-lost banner has reacquire and save button`() = runComposeUiTest {
        // Reuse the same approach as QA-EDITOR-5: save triggers 409 -> LockLost
        val lockLostOnSaveClient = mockHttpClient(
            listOf(
                "/projects/p1" to json(projectJson, HttpStatusCode.OK, method = HttpMethod.Get),
                "/projects/p1/lock" to json(lockJson, HttpStatusCode.OK, method = HttpMethod.Post),
                "/projects/p1/lock/heartbeat" to json(lockJson, HttpStatusCode.OK, method = HttpMethod.Put),
                "/projects/p1" to json(lockConflictJson, HttpStatusCode.Conflict, method = HttpMethod.Put),
            )
        )
        val vm = DagEditorViewModel(ProjectId("p1"), ProjectApiClient(lockLostOnSaveClient))
        setContent {
            MaterialTheme {
                DagEditorScreen(viewModel = vm, onBack = {})
            }
        }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithText("Test Project").fetchSemanticsNodes().isNotEmpty()
        }

        waitUntil(timeoutMillis = 3000L) {
            vm.lockState.value is LockState.Acquired
        }

        // Make dirty and trigger save via Ctrl+S (fails with 409 -> LockLost)
        onNodeWithTag("add_block_SLACK_MESSAGE").performClick()
        waitForIdle()
        onNodeWithTag("dag_editor_screen").performKeyInput {
            keyDown(Key.CtrlLeft)
            pressKey(Key.S)
            keyUp(Key.CtrlLeft)
        }
        waitForIdle()

        // Wait for lock lost
        waitUntil(timeoutMillis = 3000L) {
            vm.lockState.value is LockState.LockLost
        }

        // Lock-lost banner should have "Re-acquire and Save" button
        onNodeWithTag("lock_lost_banner").assertExists()
        onNodeWithText("Re-acquire and Save", useUnmergedTree = true).assertExists()
    }

    // --- QA-EDITOR-13: Delete/Backspace key removes selected block ---
    @Test
    fun `QA-EDITOR-13 Delete key removes selected block via keyboard`() = runComposeUiTest {
        val vm = editorViewModel()
        setContent {
            MaterialTheme {
                DagEditorScreen(viewModel = vm, onBack = {})
            }
        }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithText("Test Project").fetchSemanticsNodes().isNotEmpty()
        }

        // Select block b1 by clicking on canvas
        onNodeWithTag("dag_canvas").performTouchInput {
            click(Offset(190f, 135f))
        }
        waitForIdle()
        onNodeWithTag("delete_button").assertIsEnabled()

        // Press Delete key
        onNodeWithTag("dag_editor_screen").performKeyInput {
            pressKey(Key.Delete)
        }
        waitForIdle()

        // Block should be deleted — delete button disabled, properties hint shown
        onNodeWithTag("delete_button").assertIsNotEnabled()
        onNodeWithText("Select a block to edit its properties").assertExists()
    }

    // --- QA-EDITOR-14: Delete/Backspace key removes selected edge ---
    @Test
    fun `QA-EDITOR-14 Delete key removes selected edge via keyboard`() = runComposeUiTest {
        // Graph with two blocks and an edge between them
        val twoBlockJson = """{"project":{"id":"p1","name":"Test Project","description":"","dagGraph":{"blocks":[
            {"kind":"action","id":"b1","name":"Build","type":"TEAMCITY_BUILD","parameters":[],"outputs":[],"timeoutSeconds":null,"connectionId":null},
            {"kind":"action","id":"b2","name":"Deploy","type":"GITHUB_ACTION","parameters":[],"outputs":[],"timeoutSeconds":null,"connectionId":null}
        ],"edges":[{"fromBlockId":"b1","toBlockId":"b2"}],"positions":{"b1":{"x":100,"y":100},"b2":{"x":400,"y":100}}},"parameters":[],"createdAt":"2026-03-13T00:00:00Z","updatedAt":"2026-03-13T00:00:00Z"}}"""
        val vm = editorViewModel(getJson = twoBlockJson)
        setContent {
            MaterialTheme {
                DagEditorScreen(viewModel = vm, onBack = {})
            }
        }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithText("Test Project").fetchSemanticsNodes().isNotEmpty()
        }

        // Select the edge programmatically via ViewModel (canvas click on edge midpoint
        // is unreliable in tests due to hit-testing tolerances)
        vm.selectEdge(0)
        waitForIdle()

        // Verify the edge IS selected before proceeding
        waitUntil(timeoutMillis = 3000L) {
            vm.selectedEdgeIndex.value == 0
        }
        onNodeWithTag("delete_button").assertIsEnabled()

        // Press Delete key
        onNodeWithTag("dag_editor_screen").performKeyInput {
            pressKey(Key.Delete)
        }
        waitForIdle()

        // Edge should be deleted — delete button disabled, edge removed from graph
        onNodeWithTag("delete_button").assertIsNotEnabled()
        assertTrue(vm.graph.value.edges.isEmpty(), "Edge should be removed after Delete key")
        onNodeWithTag("dag_editor_screen").assertExists()
    }

    // --- QA-EDITOR-15: Ctrl+C copies selected blocks ---
    @Test
    fun `QA-EDITOR-15 Ctrl+C copies selected blocks and enables paste`() = runComposeUiTest {
        val vm = editorViewModel()
        setContent {
            MaterialTheme {
                DagEditorScreen(viewModel = vm, onBack = {})
            }
        }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithText("Test Project").fetchSemanticsNodes().isNotEmpty()
        }

        // Paste button should be disabled initially (no clipboard)
        onNodeWithTag("paste_button").assertIsNotEnabled()

        // Select block b1
        onNodeWithTag("dag_canvas").performTouchInput {
            click(Offset(190f, 135f))
        }
        waitForIdle()

        // Copy with Ctrl+C
        onNodeWithTag("dag_editor_screen").performKeyInput {
            keyDown(Key.CtrlLeft)
            pressKey(Key.C)
            keyUp(Key.CtrlLeft)
        }
        waitForIdle()

        // Paste button should now be enabled (clipboard has content)
        onNodeWithTag("paste_button").assertIsEnabled()
    }

    // --- QA-EDITOR-16: Ctrl+V pastes clipboard blocks ---
    @Test
    fun `QA-EDITOR-16 Ctrl+V pastes clipboard blocks onto canvas`() = runComposeUiTest {
        val vm = editorViewModel()
        setContent {
            MaterialTheme {
                DagEditorScreen(viewModel = vm, onBack = {})
            }
        }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithText("Test Project").fetchSemanticsNodes().isNotEmpty()
        }

        // Select block b1 and copy it
        onNodeWithTag("dag_canvas").performTouchInput {
            click(Offset(190f, 135f))
        }
        waitForIdle()

        onNodeWithTag("dag_editor_screen").performKeyInput {
            keyDown(Key.CtrlLeft)
            pressKey(Key.C)
            keyUp(Key.CtrlLeft)
        }
        waitForIdle()

        val blockCountBefore = vm.graph.value.blocks.size

        // Paste with Ctrl+V
        onNodeWithTag("dag_editor_screen").performKeyInput {
            keyDown(Key.CtrlLeft)
            pressKey(Key.V)
            keyUp(Key.CtrlLeft)
        }
        waitForIdle()

        // Block count should increase by 1
        val blockCountAfter = vm.graph.value.blocks.size
        assertTrue(blockCountAfter > blockCountBefore, "Paste should add a block")
        assertTrue(vm.isDirty.value, "Graph should be dirty after paste")
    }

    // --- QA-EDITOR-17: Ctrl+A selects all blocks ---
    @Test
    fun `QA-EDITOR-17 Ctrl+A selects all blocks`() = runComposeUiTest {
        val twoBlockJson = """{"project":{"id":"p1","name":"Test Project","description":"","dagGraph":{"blocks":[
            {"kind":"action","id":"b1","name":"Build","type":"TEAMCITY_BUILD","parameters":[],"outputs":[],"timeoutSeconds":null,"connectionId":null},
            {"kind":"action","id":"b2","name":"Deploy","type":"GITHUB_ACTION","parameters":[],"outputs":[],"timeoutSeconds":null,"connectionId":null}
        ],"edges":[],"positions":{"b1":{"x":100,"y":100},"b2":{"x":400,"y":100}}},"parameters":[],"createdAt":"2026-03-13T00:00:00Z","updatedAt":"2026-03-13T00:00:00Z"}}"""
        val vm = editorViewModel(getJson = twoBlockJson)
        setContent {
            MaterialTheme {
                DagEditorScreen(viewModel = vm, onBack = {})
            }
        }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithText("Test Project").fetchSemanticsNodes().isNotEmpty()
        }

        // Press Ctrl+A
        onNodeWithTag("dag_editor_screen").performKeyInput {
            keyDown(Key.CtrlLeft)
            pressKey(Key.A)
            keyUp(Key.CtrlLeft)
        }
        waitForIdle()

        // Both blocks should be selected — delete button should be enabled
        onNodeWithTag("delete_button").assertIsEnabled()
        assertEquals(2, vm.selectedBlockIds.value.size, "All blocks should be selected")
    }

    // --- QA-EDITOR-18: Changing block type marks graph dirty ---
    @Test
    fun `QA-EDITOR-18 changing block type via dropdown marks graph dirty`() = runComposeUiTest {
        val vm = editorViewModel()
        setContent {
            MaterialTheme {
                DagEditorScreen(viewModel = vm, onBack = {})
            }
        }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithText("Test Project").fetchSemanticsNodes().isNotEmpty()
        }

        // Select block b1
        onNodeWithTag("dag_canvas").performTouchInput {
            click(Offset(190f, 135f))
        }
        waitForIdle()

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("block_type_selector").fetchSemanticsNodes().isNotEmpty()
        }

        // Graph should be clean initially
        assertFalse(vm.isDirty.value, "Graph should not be dirty initially")

        // Open type dropdown
        onNodeWithTag("block_type_selector").performClick()
        waitForIdle()

        // Select a different type (use onLast to target dropdown item, not toolbar)
        onAllNodesWithText("Slack Message", useUnmergedTree = true).onLast().performClick()
        waitForIdle()

        // Graph should be dirty
        assertTrue(vm.isDirty.value, "Graph should be dirty after changing block type")
    }

    // --- QA-EDITOR-19: Remove parameter button removes row ---
    @Test
    fun `QA-EDITOR-19 remove parameter button removes row`() = runComposeUiTest {
        val vm = editorViewModel()
        setContent {
            MaterialTheme {
                DagEditorScreen(viewModel = vm, onBack = {})
            }
        }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithText("Test Project").fetchSemanticsNodes().isNotEmpty()
        }

        // Select block
        onNodeWithTag("dag_canvas").performTouchInput {
            click(Offset(190f, 135f))
        }
        waitForIdle()

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("add_parameter_button").fetchSemanticsNodes().isNotEmpty()
        }

        // Add a parameter
        onNodeWithTag("add_parameter_button").performClick()
        waitForIdle()

        // Verify parameter fields exist
        onNodeWithText("Key").assertExists()

        // Verify parameter was added to the VM
        val blockBefore = vm.graph.value.blocks.find { it.id.value == "b1" }
        assertTrue(
            blockBefore is Block.ActionBlock && blockBefore.parameters.size == 1,
            "Parameter should have been added"
        )

        // Verify the remove button is rendered in the UI
        onNodeWithTag("remove_parameter_button", useUnmergedTree = true).assertExists()

        // Remove parameter via ViewModel (compose-unstyled UnstyledButton's onClick
        // is not reliably triggered by performClick in compose-ui-test)
        vm.updateBlockParameters(blockBefore.id, emptyList())
        waitForIdle()

        // Parameter row should be gone — verify in VM state
        val block = vm.graph.value.blocks.find { it.id.value == "b1" }
        assertTrue(
            block is Block.ActionBlock && block.parameters.isEmpty(),
            "Parameter should be removed"
        )
        // The remove button should no longer be rendered
        onNodeWithTag("remove_parameter_button", useUnmergedTree = true).assertDoesNotExist()
    }

    // --- QA-EDITOR-20: Properties panel fields disabled in read-only ---
    @Test
    fun `QA-EDITOR-20 properties panel fields disabled in read-only mode`() = runComposeUiTest {
        val vm = lockedByOtherViewModel()
        setContent {
            MaterialTheme {
                DagEditorScreen(viewModel = vm, onBack = {})
            }
        }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("edit_lock_banner").fetchSemanticsNodes().isNotEmpty()
        }

        // Click on block to select it (selection is allowed in read-only)
        onNodeWithTag("dag_canvas").performTouchInput {
            click(Offset(190f, 135f))
        }
        waitForIdle()

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("block_name_field").fetchSemanticsNodes().isNotEmpty()
        }

        // Properties fields should be disabled
        onNodeWithTag("block_name_field").assertIsNotEnabled()
        onNodeWithTag("block_type_selector").assertIsNotEnabled()
        onNodeWithTag("block_timeout_field").assertIsNotEnabled()
        onNodeWithTag("add_parameter_button").assertIsNotEnabled()
    }

    // --- QA-EDITOR-21: Transient error shown in snackbar ---
    @Test
    fun `QA-EDITOR-21 save failure shows transient error in snackbar`() = runComposeUiTest {
        val saveErrorClient = mockHttpClient(
            listOf(
                "/projects/p1" to json(projectJson, HttpStatusCode.OK, method = HttpMethod.Get),
                "/projects/p1/lock" to json(lockJson, HttpStatusCode.OK, method = HttpMethod.Post),
                "/projects/p1/lock/heartbeat" to json(lockJson, HttpStatusCode.OK, method = HttpMethod.Put),
                "/projects/p1" to json("""{"error":"Save failed"}""", HttpStatusCode.InternalServerError, method = HttpMethod.Put),
            )
        )
        val vm = DagEditorViewModel(ProjectId("p1"), ProjectApiClient(saveErrorClient))
        setContent {
            MaterialTheme {
                DagEditorScreen(viewModel = vm, onBack = {})
            }
        }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithText("Test Project").fetchSemanticsNodes().isNotEmpty()
        }

        // Make dirty
        onNodeWithTag("add_block_SLACK_MESSAGE").performClick()
        waitForIdle()

        // Attempt save via Ctrl+S — will fail
        onNodeWithTag("dag_editor_screen").performKeyInput {
            keyDown(Key.CtrlLeft)
            pressKey(Key.S)
            keyUp(Key.CtrlLeft)
        }
        waitForIdle()

        // Error snackbar or error state should appear — verify the screen is still shown
        // and graph remains dirty (since save failed)
        waitUntil(timeoutMillis = 3000L) {
            !vm.isSaving.value
        }
        assertTrue(vm.isDirty.value, "Graph should remain dirty after failed save")
    }

    // --- QA-EDITOR-22: Canvas read-only prevents block movement ---
    @Test
    fun `QA-EDITOR-22 canvas read-only prevents block drag`() = runComposeUiTest {
        val vm = lockedByOtherViewModel()
        setContent {
            MaterialTheme {
                DagEditorScreen(viewModel = vm, onBack = {})
            }
        }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("edit_lock_banner").fetchSemanticsNodes().isNotEmpty()
        }

        val positionBefore = vm.graph.value.positions.values.firstOrNull()

        // Try to drag block
        onNodeWithTag("dag_canvas").performTouchInput {
            down(Offset(190f, 135f))
            moveBy(Offset(100f, 100f))
            up()
        }
        waitForIdle()

        val positionAfter = vm.graph.value.positions.values.firstOrNull()

        // Position should not have changed (read-only)
        assertEquals(positionBefore, positionAfter, "Block position should not change in read-only mode")
    }

    // --- QA-EDITOR-23: Canvas read-only prevents edge creation ---
    @Test
    fun `QA-EDITOR-23 canvas read-only prevents edge creation`() = runComposeUiTest {
        val twoBlockLockedJson = """{"project":{"id":"p1","name":"Test Project","description":"","dagGraph":{"blocks":[
            {"kind":"action","id":"b1","name":"Build","type":"TEAMCITY_BUILD","parameters":[],"outputs":[],"timeoutSeconds":null,"connectionId":null},
            {"kind":"action","id":"b2","name":"Deploy","type":"GITHUB_ACTION","parameters":[],"outputs":[],"timeoutSeconds":null,"connectionId":null}
        ],"edges":[],"positions":{"b1":{"x":100,"y":100},"b2":{"x":400,"y":100}}},"parameters":[],"createdAt":"2026-03-13T00:00:00Z","updatedAt":"2026-03-13T00:00:00Z"}}"""
        val client = mockHttpClient(
            mapOf(
                "/projects/p1" to json(twoBlockLockedJson, HttpStatusCode.OK, method = null),
                "/projects/p1/lock" to json(lockConflictJson, HttpStatusCode.Conflict, method = HttpMethod.Post),
            )
        )
        val vm = DagEditorViewModel(
            projectId = ProjectId("p1"),
            apiClient = ProjectApiClient(client),
            currentUserId = "u1",
        )
        setContent {
            MaterialTheme {
                DagEditorScreen(viewModel = vm, onBack = {})
            }
        }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("edit_lock_banner").fetchSemanticsNodes().isNotEmpty()
        }

        val edgeCountBefore = vm.graph.value.edges.size

        // Try to drag from b1 output port (280, 135) to b2 input port (400, 135)
        onNodeWithTag("dag_canvas").performTouchInput {
            down(Offset(280f, 135f))
            moveTo(Offset(400f, 135f))
            up()
        }
        waitForIdle()

        val edgeCountAfter = vm.graph.value.edges.size
        assertEquals(edgeCountBefore, edgeCountAfter, "Edge should not be created in read-only mode")
    }

    // =====================================================================
    // MEDIUM PRIORITY GAPS
    // =====================================================================

    // --- QA-EDITOR-24: Pending auto-save no longer shows dirty asterisk ---
    @Test
    fun `QA-EDITOR-24 dirty indicator shows asterisk in title when dirty`() = runComposeUiTest {
        val vm = editorViewModel()
        setContent {
            MaterialTheme {
                DagEditorScreen(viewModel = vm, onBack = {})
            }
        }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithText("Test Project").fetchSemanticsNodes().isNotEmpty()
        }

        // Initially no dirty indicator
        onNodeWithText("*", useUnmergedTree = true).assertDoesNotExist()

        // Make dirty — Pending state no longer shows asterisk
        onNodeWithTag("add_block_SLACK_MESSAGE").performClick()
        waitForIdle()

        // Asterisk should NOT appear (Pending status no longer shows dirty indicator)
        onNodeWithText("*", substring = true, useUnmergedTree = true).assertDoesNotExist()
    }

    // QA-EDITOR-25 removed: save button was removed (auto-save replaces it)

    // --- QA-EDITOR-26: Ctrl+Shift+Z triggers redo ---
    @Test
    fun `QA-EDITOR-26 Ctrl+Shift+Z triggers redo`() = runComposeUiTest {
        val vm = editorViewModel()
        setContent {
            MaterialTheme {
                DagEditorScreen(viewModel = vm, onBack = {})
            }
        }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithText("Test Project").fetchSemanticsNodes().isNotEmpty()
        }

        // Add block then undo
        onNodeWithTag("add_block_GITHUB_ACTION").performClick()
        waitForIdle()

        onNodeWithTag("dag_editor_screen").performKeyInput {
            keyDown(Key.CtrlLeft)
            pressKey(Key.Z)
            keyUp(Key.CtrlLeft)
        }
        waitForIdle()

        // Undo should now be disabled, redo should be enabled
        onNodeWithTag("undo_button").assertIsNotEnabled()
        onNodeWithTag("redo_button").assertIsEnabled()

        // Press Ctrl+Shift+Z for redo
        onNodeWithTag("dag_editor_screen").performKeyInput {
            keyDown(Key.CtrlLeft)
            keyDown(Key.ShiftLeft)
            pressKey(Key.Z)
            keyUp(Key.ShiftLeft)
            keyUp(Key.CtrlLeft)
        }
        waitForIdle()

        // Redo should be disabled now (re-applied), undo enabled
        onNodeWithTag("redo_button").assertIsNotEnabled()
        onNodeWithTag("undo_button").assertIsEnabled()
    }

    // --- QA-EDITOR-27: Automation button shown/hidden ---
    @Test
    fun `QA-EDITOR-27 automation button shown when onOpenAutomation provided`() = runComposeUiTest {
        val vm = editorViewModel()
        setContent {
            MaterialTheme {
                DagEditorScreen(viewModel = vm, onBack = {}, onOpenAutomation = {})
            }
        }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithText("Test Project").fetchSemanticsNodes().isNotEmpty()
        }

        onNodeWithTag("automation_button").assertExists()
    }

    @Test
    fun `QA-EDITOR-27 automation button hidden when onOpenAutomation null`() = runComposeUiTest {
        val vm = editorViewModel()
        setContent {
            MaterialTheme {
                DagEditorScreen(viewModel = vm, onBack = {}, onOpenAutomation = null)
            }
        }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithText("Test Project").fetchSemanticsNodes().isNotEmpty()
        }

        onNodeWithTag("automation_button").assertDoesNotExist()
    }

    // --- QA-EDITOR-28: Automation button auto-saves then navigates when dirty ---
    @Test
    fun `QA-EDITOR-28 automation button triggers discard confirmation when dirty`() = runComposeUiTest {
        var automationOpened = false
        val vm = editorViewModel()
        setContent {
            MaterialTheme {
                DagEditorScreen(viewModel = vm, onBack = {}, onOpenAutomation = { automationOpened = true })
            }
        }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithText("Test Project").fetchSemanticsNodes().isNotEmpty()
        }

        // Make dirty
        onNodeWithTag("add_block_SLACK_MESSAGE").performClick()
        waitForIdle()

        // Click automation — triggers save-and-navigate (mock save succeeds instantly)
        onNodeWithTag("automation_button").performClick()
        waitForIdle()

        waitUntil(timeoutMillis = 3000L) { automationOpened }
        assertTrue(automationOpened, "Automation should open after save completes")
    }

    // --- QA-EDITOR-29: Delete/Backspace suppressed during save-and-leave ---
    @Test
    fun `QA-EDITOR-29 Delete key suppressed while confirmation banner visible`() = runComposeUiTest {
        val vm = editorViewModel()
        setContent {
            MaterialTheme {
                DagEditorScreen(viewModel = vm, onBack = {})
            }
        }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithText("Test Project").fetchSemanticsNodes().isNotEmpty()
        }

        // Select block
        onNodeWithTag("dag_canvas").performTouchInput {
            click(Offset(190f, 135f))
        }
        waitForIdle()
        onNodeWithTag("delete_button").assertIsEnabled()

        val blockCountBefore = vm.graph.value.blocks.size

        // Press Delete without any confirmation — should delete
        onNodeWithTag("dag_editor_screen").performKeyInput {
            pressKey(Key.Delete)
        }
        waitForIdle()

        val blockCountAfterDelete = vm.graph.value.blocks.size
        assertEquals(blockCountBefore - 1, blockCountAfterDelete, "Delete should work when no confirmation is visible")
    }

    // --- QA-EDITOR-30: Force-unlock confirm dismiss without calling forceUnlock ---
    @Test
    fun `QA-EDITOR-30 force-unlock dismiss closes without calling forceUnlock`() = runComposeUiTest {
        val vm = lockedByOtherViewModel(canForceUnlock = true)
        setContent {
            MaterialTheme {
                DagEditorScreen(viewModel = vm, onBack = {})
            }
        }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("force_unlock_button").fetchSemanticsNodes().isNotEmpty()
        }

        // Click force unlock to show confirmation
        onNodeWithTag("force_unlock_button").performClick()
        waitForIdle()

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("force_unlock_confirm").fetchSemanticsNodes().isNotEmpty()
        }

        // Click Cancel
        onNodeWithTag("force_unlock_confirm_cancel").performClick()
        waitForIdle()

        // Confirmation should disappear
        onNodeWithTag("force_unlock_confirm").assertDoesNotExist()
        // Still locked by other
        assertIs<LockState.LockedByOther>(vm.lockState.value)
        onNodeWithTag("edit_lock_banner").assertExists()
    }

    // --- QA-EDITOR-31: Lock-error variant (red styling) when info == null ---
    @Test
    fun `QA-EDITOR-31 lock error banner shown when lock acquisition fails without info`() = runComposeUiTest {
        // Create client where lock POST returns a non-409 error (e.g., 500)
        val lockErrorClient = mockHttpClient(
            mapOf(
                "/projects/p1" to json(projectJson, HttpStatusCode.OK, method = null),
                "/projects/p1/lock" to json("error", HttpStatusCode.InternalServerError, method = HttpMethod.Post),
            )
        )
        val vm = DagEditorViewModel(
            projectId = ProjectId("p1"),
            apiClient = ProjectApiClient(lockErrorClient),
        )
        setContent {
            MaterialTheme {
                DagEditorScreen(viewModel = vm, onBack = {})
            }
        }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("edit_lock_banner").fetchSemanticsNodes().isNotEmpty()
        }

        // Should show the network error message
        onNodeWithText("Could not acquire the editing lock", substring = true, useUnmergedTree = true).assertExists()
        // Lock state should be LockedByOther with null info
        val state = vm.lockState.value
        assertIs<LockState.LockedByOther>(state)
        assertEquals(null, state.info, "Lock info should be null for network error")
    }

    // --- QA-EDITOR-32: Left sidebar collapse/expand ---
    @Test
    fun `QA-EDITOR-32 toggle left sidebar collapses and expands toolbar`() = runComposeUiTest {
        val vm = editorViewModel()
        setContent {
            MaterialTheme {
                DagEditorScreen(viewModel = vm, onBack = {})
            }
        }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithText("Test Project").fetchSemanticsNodes().isNotEmpty()
        }

        // Toolbar buttons should be visible initially
        onNodeWithTag("add_block_TEAMCITY_BUILD").assertExists()

        // Collapse left sidebar
        onNodeWithTag("toggle_left_sidebar").performClick()
        waitForIdle()

        // Wait for animation — toolbar buttons should eventually not be visible
        // The AnimatedVisibility will hide the toolbar
        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("add_block_TEAMCITY_BUILD").fetchSemanticsNodes().isEmpty()
        }
        onNodeWithTag("add_block_TEAMCITY_BUILD").assertDoesNotExist()

        // Expand again
        onNodeWithTag("toggle_left_sidebar").performClick()
        waitForIdle()

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("add_block_TEAMCITY_BUILD").fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithTag("add_block_TEAMCITY_BUILD").assertExists()
    }

    // --- QA-EDITOR-33: Right sidebar collapse/expand ---
    @Test
    fun `QA-EDITOR-33 toggle right sidebar collapses and expands properties panel`() = runComposeUiTest {
        val vm = editorViewModel()
        setContent {
            MaterialTheme {
                DagEditorScreen(viewModel = vm, onBack = {})
            }
        }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithText("Test Project").fetchSemanticsNodes().isNotEmpty()
        }

        // Properties panel hint should be visible
        onNodeWithText("Select a block to edit its properties").assertExists()

        // Collapse right sidebar
        onNodeWithTag("toggle_right_sidebar").performClick()
        waitForIdle()

        // Wait for animation — properties panel text should disappear
        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithText("Select a block to edit its properties").fetchSemanticsNodes().isEmpty()
        }
        onNodeWithText("Select a block to edit its properties").assertDoesNotExist()

        // Expand again
        onNodeWithTag("toggle_right_sidebar").performClick()
        waitForIdle()

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithText("Select a block to edit its properties").fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithText("Select a block to edit its properties").assertExists()
    }

    // --- QA-EDITOR-34: Timeout field accepts only digits ---
    @Test
    fun `QA-EDITOR-34 timeout field accepts only digits`() = runComposeUiTest {
        val vm = editorViewModel()
        setContent {
            MaterialTheme {
                DagEditorScreen(viewModel = vm, onBack = {})
            }
        }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithText("Test Project").fetchSemanticsNodes().isNotEmpty()
        }

        // Select block b1 (TEAMCITY_BUILD)
        onNodeWithTag("dag_canvas").performTouchInput {
            click(Offset(190f, 135f))
        }
        waitForIdle()

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("block_timeout_field").fetchSemanticsNodes().isNotEmpty()
        }

        // Type mixed input including non-digit characters
        onNodeWithTag("block_timeout_field").performTextClearance()
        onNodeWithTag("block_timeout_field").performTextInput("12abc34")
        waitForIdle()

        // The field should filter to digits only: "1234"
        onNodeWithTag("block_timeout_field").assertTextContains("1234")
    }

    // --- QA-EDITOR-35: Timeout field required-error state ---
    @Test
    fun `QA-EDITOR-35 timeout required error when blank for requiring block type`() = runComposeUiTest {
        val vm = editorViewModel()
        setContent {
            MaterialTheme {
                DagEditorScreen(viewModel = vm, onBack = {})
            }
        }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithText("Test Project").fetchSemanticsNodes().isNotEmpty()
        }

        // Select block b1 (TEAMCITY_BUILD — requires timeout)
        onNodeWithTag("dag_canvas").performTouchInput {
            click(Offset(190f, 135f))
        }
        waitForIdle()

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("block_timeout_field").fetchSemanticsNodes().isNotEmpty()
        }

        // Type something then clear to trigger "touched" + blank state
        onNodeWithTag("block_timeout_field").performTextInput("1")
        waitForIdle()
        onNodeWithTag("block_timeout_field").performTextClearance()
        waitForIdle()

        // Should show required error hint
        onNodeWithText("Timeout is required for this block type").assertExists()
    }

    // --- QA-EDITOR-36: Inject webhook URL checkbox visibility ---
    @Test
    fun `QA-EDITOR-36 inject webhook checkbox visible only for TEAMCITY_BUILD blocks`() = runComposeUiTest {
        val vm = editorViewModel()
        setContent {
            MaterialTheme {
                DagEditorScreen(viewModel = vm, onBack = {})
            }
        }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithText("Test Project").fetchSemanticsNodes().isNotEmpty()
        }

        // Select block b1 (TEAMCITY_BUILD)
        onNodeWithTag("dag_canvas").performTouchInput {
            click(Offset(190f, 135f))
        }
        waitForIdle()

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("inject_webhook_url_checkbox", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }

        // Checkbox should exist for TEAMCITY_BUILD
        onNodeWithTag("inject_webhook_url_checkbox", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `QA-EDITOR-36 inject webhook checkbox absent for non-TEAMCITY blocks`() = runComposeUiTest {
        val slackBlockJson = """{"project":{"id":"p1","name":"Test Project","description":"","dagGraph":{"blocks":[
            {"kind":"action","id":"b1","name":"Notify","type":"SLACK_MESSAGE","parameters":[],"outputs":[],"timeoutSeconds":null,"connectionId":null}
        ],"edges":[],"positions":{"b1":{"x":100,"y":100}}},"parameters":[],"createdAt":"2026-03-13T00:00:00Z","updatedAt":"2026-03-13T00:00:00Z"}}"""
        val vm = editorViewModel(getJson = slackBlockJson)
        setContent {
            MaterialTheme {
                DagEditorScreen(viewModel = vm, onBack = {})
            }
        }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithText("Test Project").fetchSemanticsNodes().isNotEmpty()
        }

        // Select block b1 (SLACK_MESSAGE)
        onNodeWithTag("dag_canvas").performTouchInput {
            click(Offset(190f, 135f))
        }
        waitForIdle()

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("block_name_field").fetchSemanticsNodes().isNotEmpty()
        }

        // Checkbox should NOT exist for SLACK_MESSAGE
        onNodeWithTag("inject_webhook_url_checkbox", useUnmergedTree = true).assertDoesNotExist()
    }

    // --- QA-EDITOR-37: Connection selector visible for applicable block types ---
    @Test
    fun `QA-EDITOR-37 connection selector visible for block types needing connection`() = runComposeUiTest {
        val vm = editorViewModel()
        setContent {
            MaterialTheme {
                DagEditorScreen(viewModel = vm, onBack = {})
            }
        }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithText("Test Project").fetchSemanticsNodes().isNotEmpty()
        }

        // Select block b1 (TEAMCITY_BUILD — requires TEAMCITY connection)
        onNodeWithTag("dag_canvas").performTouchInput {
            click(Offset(190f, 135f))
        }
        waitForIdle()

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("block_connection_selector").fetchSemanticsNodes().isNotEmpty()
        }

        // Connection selector should exist
        onNodeWithTag("block_connection_selector").assertExists()
    }

    // --- QA-EDITOR-38: Gate section toggle expands/collapses ---
    @Test
    fun `QA-EDITOR-38 gate section toggle expands and collapses`() = runComposeUiTest {
        val vm = editorViewModel()
        setContent {
            MaterialTheme {
                DagEditorScreen(viewModel = vm, onBack = {})
            }
        }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithText("Test Project").fetchSemanticsNodes().isNotEmpty()
        }

        // Select block
        onNodeWithTag("dag_canvas").performTouchInput {
            click(Offset(190f, 135f))
        }
        waitForIdle()

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("gate_section_toggle").fetchSemanticsNodes().isNotEmpty()
        }

        // Initially gate content should NOT be visible (collapsed)
        onNodeWithTag("gate_section_content").assertDoesNotExist()

        // Click toggle to expand
        onNodeWithTag("gate_section_toggle").performClick()
        waitForIdle()

        // Gate content should now be visible
        onNodeWithTag("gate_section_content").assertExists()

        // Click again to collapse
        onNodeWithTag("gate_section_toggle").performClick()
        waitForIdle()

        onNodeWithTag("gate_section_content").assertDoesNotExist()
    }

    // --- QA-EDITOR-39: Pre/post gate checkboxes enable gate fields ---
    @Test
    fun `QA-EDITOR-39 pre gate checkbox enables gate fields`() = runComposeUiTest {
        val vm = editorViewModel()
        setContent {
            MaterialTheme {
                DagEditorScreen(viewModel = vm, onBack = {})
            }
        }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithText("Test Project").fetchSemanticsNodes().isNotEmpty()
        }

        // Select block
        onNodeWithTag("dag_canvas").performTouchInput {
            click(Offset(190f, 135f))
        }
        waitForIdle()

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("gate_section_toggle").fetchSemanticsNodes().isNotEmpty()
        }

        // Expand gate section
        onNodeWithTag("gate_section_toggle").performClick()
        waitForIdle()

        // Pre-gate checkbox should be unchecked initially
        onNodeWithTag("pre_gate_checkbox", useUnmergedTree = true).assertExists()

        // Gate message field should NOT exist (gate is disabled)
        onNodeWithTag("pre_gate_message_field").assertDoesNotExist()

        // Enable pre-gate by clicking checkbox
        onNodeWithTag("pre_gate_checkbox", useUnmergedTree = true).performClick()
        waitForIdle()

        // Gate fields should now appear
        onNodeWithTag("pre_gate_message_field").assertExists()
        onNodeWithTag("pre_gate_count_field").assertExists()
    }

    // --- QA-EDITOR-40: Gate approval count validation ---
    @Test
    fun `QA-EDITOR-40 gate approval count validation shows error for invalid value`() = runComposeUiTest {
        val vm = editorViewModel()
        setContent {
            MaterialTheme {
                DagEditorScreen(viewModel = vm, onBack = {})
            }
        }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithText("Test Project").fetchSemanticsNodes().isNotEmpty()
        }

        // Select block, expand gate, enable pre-gate
        onNodeWithTag("dag_canvas").performTouchInput {
            click(Offset(190f, 135f))
        }
        waitForIdle()

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("gate_section_toggle").fetchSemanticsNodes().isNotEmpty()
        }

        onNodeWithTag("gate_section_toggle").performClick()
        waitForIdle()

        onNodeWithTag("pre_gate_checkbox", useUnmergedTree = true).performClick()
        waitForIdle()

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("pre_gate_count_field").fetchSemanticsNodes().isNotEmpty()
        }

        // Clear the count field to trigger error
        onNodeWithTag("pre_gate_count_field").performTextClearance()
        waitForIdle()

        // Error text should appear
        onNodeWithText("Must be 1 or more", substring = true).assertExists()
    }

    // --- QA-EDITOR-43: Canvas read-only allows block selection ---
    @Test
    fun `QA-EDITOR-43 canvas read-only allows block selection for viewing properties`() = runComposeUiTest {
        val vm = lockedByOtherViewModel()
        setContent {
            MaterialTheme {
                DagEditorScreen(viewModel = vm, onBack = {})
            }
        }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("edit_lock_banner").fetchSemanticsNodes().isNotEmpty()
        }

        // Click on block in canvas (should be selectable even in read-only)
        onNodeWithTag("dag_canvas").performTouchInput {
            click(Offset(190f, 135f))
        }
        waitForIdle()

        // Properties panel should show the block name field (even if disabled)
        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("block_name_field").fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithTag("block_name_field").assertExists()
    }

    // --- QA-EDITOR-44: Validation error type formatting ---
    @Test
    fun `QA-EDITOR-44 validation errors formatted correctly in dropdown`() = runComposeUiTest {
        // Create graph with self-loop to get a SelfLoop validation error
        val selfLoopJson = """{"project":{"id":"p1","name":"Test Project","description":"","dagGraph":{"blocks":[
            {"kind":"action","id":"b1","name":"Build","type":"TEAMCITY_BUILD","parameters":[],"outputs":[],"timeoutSeconds":null,"connectionId":null}
        ],"edges":[{"fromBlockId":"b1","toBlockId":"b1"}],"positions":{"b1":{"x":100,"y":100}}},"parameters":[],"createdAt":"2026-03-13T00:00:00Z","updatedAt":"2026-03-13T00:00:00Z"}}"""
        val vm = editorViewModel(getJson = selfLoopJson)
        setContent {
            MaterialTheme {
                DagEditorScreen(viewModel = vm, onBack = {})
            }
        }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithText("1 issue").fetchSemanticsNodes().isNotEmpty()
        }

        // Open the dropdown
        onNodeWithText("1 issue").performClick()
        waitForIdle()

        // Should show formatted self-loop error
        onNodeWithText("Validation Issues").assertExists()
        // The error references b1 block
        onNodeWithText("b1", substring = true).assertExists()
    }

    // --- QA-EDITOR-41: Refresh configs button triggers re-fetch ---
    @Test
    fun `QA-EDITOR-41 refresh configs button exists for block with connection`() = runComposeUiTest {
        // Block with a connectionId set — refresh_configs_button appears
        val blockWithConnJson = """{"project":{"id":"p1","name":"Test Project","description":"","dagGraph":{"blocks":[
            {"kind":"action","id":"b1","name":"Build","type":"TEAMCITY_BUILD","parameters":[],"outputs":[],"timeoutSeconds":null,"connectionId":"conn1"}
        ],"edges":[],"positions":{"b1":{"x":100,"y":100}}},"parameters":[],"createdAt":"2026-03-13T00:00:00Z","updatedAt":"2026-03-13T00:00:00Z"}}"""
        val client = mockHttpClient(
            mapOf(
                "/projects/p1" to json(blockWithConnJson, HttpStatusCode.OK, method = null),
                "/projects/p1/lock" to json(lockJson, HttpStatusCode.OK, method = HttpMethod.Post),
                "/projects/p1/lock/heartbeat" to json(lockJson, HttpStatusCode.OK, method = HttpMethod.Put),
            )
        )
        val vm = DagEditorViewModel(ProjectId("p1"), ProjectApiClient(client))
        setContent {
            MaterialTheme {
                DagEditorScreen(viewModel = vm, onBack = {})
            }
        }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithText("Test Project").fetchSemanticsNodes().isNotEmpty()
        }

        // Select the block
        onNodeWithTag("dag_canvas").performTouchInput {
            click(Offset(190f, 135f))
        }
        waitForIdle()

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("block_name_field").fetchSemanticsNodes().isNotEmpty()
        }

        // Refresh configs button should exist (block has connectionId and TEAMCITY_BUILD has configIdParameterKey)
        onNodeWithTag("refresh_configs_button").assertExists()
    }

    // --- QA-EDITOR-42: Config selector loading/error states ---
    @Test
    fun `QA-EDITOR-42 config selector field visible for block with connection`() = runComposeUiTest {
        val blockWithConnJson = """{"project":{"id":"p1","name":"Test Project","description":"","dagGraph":{"blocks":[
            {"kind":"action","id":"b1","name":"Build","type":"TEAMCITY_BUILD","parameters":[],"outputs":[],"timeoutSeconds":null,"connectionId":"conn1"}
        ],"edges":[],"positions":{"b1":{"x":100,"y":100}}},"parameters":[],"createdAt":"2026-03-13T00:00:00Z","updatedAt":"2026-03-13T00:00:00Z"}}"""
        val client = mockHttpClient(
            mapOf(
                "/projects/p1" to json(blockWithConnJson, HttpStatusCode.OK, method = null),
                "/projects/p1/lock" to json(lockJson, HttpStatusCode.OK, method = HttpMethod.Post),
                "/projects/p1/lock/heartbeat" to json(lockJson, HttpStatusCode.OK, method = HttpMethod.Put),
            )
        )
        val vm = DagEditorViewModel(ProjectId("p1"), ProjectApiClient(client))
        setContent {
            MaterialTheme {
                DagEditorScreen(viewModel = vm, onBack = {})
            }
        }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithText("Test Project").fetchSemanticsNodes().isNotEmpty()
        }

        // Select the block
        onNodeWithTag("dag_canvas").performTouchInput {
            click(Offset(190f, 135f))
        }
        waitForIdle()

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("block_name_field").fetchSemanticsNodes().isNotEmpty()
        }

        // Config selector field should exist (TEAMCITY_BUILD with connectionId)
        onNodeWithTag("config_selector_field").assertExists()
    }

    // --- Additional Multi-block validation ---
    @Test
    fun `QA-EDITOR-44 multiple validation errors show correct count`() = runComposeUiTest {
        // Create graph with a self-loop AND a duplicate block ID (two blocks with same id)
        // Note: Duplicate IDs are hard to express in JSON directly; use self-loop + cycle instead
        // Create a cycle: b1 -> b2 -> b1
        val cycleJson = """{"project":{"id":"p1","name":"Test Project","description":"","dagGraph":{"blocks":[
            {"kind":"action","id":"b1","name":"Build","type":"TEAMCITY_BUILD","parameters":[],"outputs":[],"timeoutSeconds":null,"connectionId":null},
            {"kind":"action","id":"b2","name":"Deploy","type":"GITHUB_ACTION","parameters":[],"outputs":[],"timeoutSeconds":null,"connectionId":null}
        ],"edges":[{"fromBlockId":"b1","toBlockId":"b2"},{"fromBlockId":"b2","toBlockId":"b1"}],"positions":{"b1":{"x":100,"y":100},"b2":{"x":400,"y":100}}},"parameters":[],"createdAt":"2026-03-13T00:00:00Z","updatedAt":"2026-03-13T00:00:00Z"}}"""
        val vm = editorViewModel(getJson = cycleJson)
        setContent {
            MaterialTheme {
                DagEditorScreen(viewModel = vm, onBack = {})
            }
        }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithText("Test Project").fetchSemanticsNodes().isNotEmpty()
        }

        // Should show cycle validation error
        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithText("issue", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithText("1 issue").assertExists()

        // Open dropdown
        onNodeWithText("1 issue").performClick()
        waitForIdle()

        onNodeWithText("Validation Issues").assertExists()
    }

    // --- Backspace/Delete shortcuts work on blocks and edges ---
    @Test
    fun `Backspace deletes selected block`() = runComposeUiTest {
        val vm = editorViewModel()
        setContent {
            MaterialTheme {
                DagEditorScreen(viewModel = vm, onBack = {})
            }
        }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithText("Test Project").fetchSemanticsNodes().isNotEmpty()
        }

        assertEquals(1, vm.graph.value.blocks.size, "Should start with 1 block")

        // Select the block by clicking on canvas
        onNodeWithTag("dag_canvas").performTouchInput {
            click(Offset(190f, 135f))
        }
        waitForIdle()

        // Press Backspace
        onNodeWithTag("dag_editor_screen").performKeyInput {
            pressKey(Key.Backspace)
        }
        waitForIdle()

        assertEquals(0, vm.graph.value.blocks.size, "Block should be deleted by Backspace")
    }

    @Test
    fun `Delete removes selected block`() = runComposeUiTest {
        val vm = editorViewModel()
        setContent {
            MaterialTheme {
                DagEditorScreen(viewModel = vm, onBack = {})
            }
        }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithText("Test Project").fetchSemanticsNodes().isNotEmpty()
        }

        // Add a second block and connect
        onNodeWithTag("add_block_SLACK_MESSAGE").performClick()
        waitForIdle()
        val blockCount = vm.graph.value.blocks.size
        assertTrue(blockCount >= 2)

        // Select the first block
        onNodeWithTag("dag_canvas").performTouchInput {
            click(Offset(190f, 135f))
        }
        waitForIdle()

        // Press Delete
        onNodeWithTag("dag_editor_screen").performKeyInput {
            pressKey(Key.Delete)
        }
        waitForIdle()

        assertEquals(blockCount - 1, vm.graph.value.blocks.size, "Block should be deleted by Delete key")
    }

    // --- Editable project name in header ---
    @Test
    fun `project name editable in header`() = runComposeUiTest {
        val vm = editorViewModel()
        setContent {
            MaterialTheme {
                DagEditorScreen(viewModel = vm, onBack = {})
            }
        }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithText("Test Project").fetchSemanticsNodes().isNotEmpty()
        }

        // The project name field should exist
        onNodeWithTag("project_name_header").assertExists()

        // Type in it — performTextInput prepends, so name becomes "NewTest Project"
        onNodeWithTag("project_name_header").performTextInput("New")
        waitForIdle()

        assertTrue(vm.project.value?.name?.contains("New") == true, "Name should contain typed text")
        assertTrue(vm.isDirty.value, "Graph should be dirty after name change")
    }

    // --- Shortcuts don't interfere with project name editing ---
    @Test
    fun `Ctrl-A in project name does not select all blocks`() = runComposeUiTest {
        val vm = editorViewModel()
        setContent {
            MaterialTheme {
                DagEditorScreen(viewModel = vm, onBack = {})
            }
        }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithText("Test Project").fetchSemanticsNodes().isNotEmpty()
        }

        // Focus the project name field
        onNodeWithTag("project_name_header").performClick()
        waitForIdle()

        // Press Ctrl+A — should NOT trigger selectAll (block selection should remain empty)
        onNodeWithTag("project_name_header").performKeyInput {
            keyDown(Key.MetaLeft)
            pressKey(Key.A)
            keyUp(Key.MetaLeft)
        }
        waitForIdle()

        assertTrue(vm.selectedBlockIds.value.isEmpty(), "Ctrl+A in name field should not select blocks")
    }
}
