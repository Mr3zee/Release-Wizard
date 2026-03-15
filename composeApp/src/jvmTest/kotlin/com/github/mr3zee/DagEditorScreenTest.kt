package com.github.mr3zee

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.*
import com.github.mr3zee.api.ProjectApiClient
import com.github.mr3zee.editor.DagEditorScreen
import com.github.mr3zee.editor.DagEditorViewModel
import com.github.mr3zee.editor.LockState
import com.github.mr3zee.model.BlockType
import com.github.mr3zee.model.ProjectId
import io.ktor.http.*
import kotlin.test.Test
import kotlin.test.assertIs

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
        mapOf(
            "/projects/p1" to json(getJson, getStatus, method = null),
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

        // After adding a block, save button should be enabled (dirty)
        onNodeWithTag("save_button").assertIsEnabled()
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

        // After adding a container, the graph is dirty so save should be enabled
        onNodeWithTag("save_button").assertIsEnabled()
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

    // 9. Save button disabled when not dirty
    @Test
    fun `save button disabled when not dirty`() = runComposeUiTest {
        val vm = editorViewModel()
        setContent {
            MaterialTheme {
                DagEditorScreen(viewModel = vm, onBack = {})
            }
        }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithText("Test Project").fetchSemanticsNodes().isNotEmpty()
        }

        onNodeWithTag("save_button").assertIsNotEnabled()
    }

    // 10. Save button enables after change
    @Test
    fun `save button enables after change`() = runComposeUiTest {
        val vm = editorViewModel()
        setContent {
            MaterialTheme {
                DagEditorScreen(viewModel = vm, onBack = {})
            }
        }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithText("Test Project").fetchSemanticsNodes().isNotEmpty()
        }

        // Initially not dirty
        onNodeWithTag("save_button").assertIsNotEnabled()

        // Add a block to make the graph dirty
        onNodeWithTag("add_block_SLACK_MESSAGE").performClick()
        waitForIdle()

        onNodeWithTag("save_button").assertIsEnabled()
    }

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

        // Verify all 6 block type buttons exist
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
        // Save should be enabled
        onNodeWithTag("save_button").assertIsEnabled()
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
        onNodeWithTag("save_button").assertIsEnabled()
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
        onNodeWithText("This project is being edited by otheruser", substring = true).assertExists()
    }

    @Test
    fun `save button disabled in read-only mode`() = runComposeUiTest {
        val vm = lockedByOtherViewModel()
        setContent {
            MaterialTheme {
                DagEditorScreen(viewModel = vm, onBack = {})
            }
        }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("edit_lock_banner").fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithTag("save_button").assertIsNotEnabled()
    }

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
        onNodeWithText("Force unlock will end otheruser's editing session", substring = true).assertExists()
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
        onNodeWithText("You are editing this project in another session", substring = true).assertExists()
        onNodeWithText("being edited by", substring = true).assertDoesNotExist()
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
        onNodeWithText("being edited by otheruser", substring = true).assertExists()
        onNodeWithText("another session", substring = true).assertDoesNotExist()
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
        onNodeWithText("You are editing this project in another session", substring = true).assertExists()
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
        onNodeWithTag("save_button").assertIsNotEnabled()
        onNodeWithTag("add_block_TEAMCITY_BUILD").assertIsNotEnabled()
        onNodeWithTag("add_container").assertIsNotEnabled()
    }
}
