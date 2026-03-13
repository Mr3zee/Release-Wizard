package com.github.mr3zee

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import com.github.mr3zee.api.ProjectApiClient
import com.github.mr3zee.editor.DagEditorScreen
import com.github.mr3zee.editor.DagEditorViewModel
import com.github.mr3zee.model.BlockType
import com.github.mr3zee.model.ProjectId
import io.ktor.http.*
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class DagEditorScreenTest {

    private val projectJson = """{"project":{"id":"p1","name":"Test Project","description":"","dagGraph":{"blocks":[
        {"kind":"action","id":"b1","name":"Build","type":"TEAMCITY_BUILD","parameters":[],"outputs":[],"timeoutSeconds":null,"connectionId":null}
    ],"edges":[],"positions":{"b1":{"x":100,"y":100}}},"parameters":[],"createdAt":"2026-03-13T00:00:00Z","updatedAt":"2026-03-13T00:00:00Z"}}"""

    private val emptyProjectJson = """{"project":{"id":"p1","name":"Test Project","description":"","dagGraph":{"blocks":[],"edges":[],"positions":{}},"parameters":[],"createdAt":"2026-03-13T00:00:00Z","updatedAt":"2026-03-13T00:00:00Z"}}"""

    private fun editorClient(
        getJson: String = projectJson,
        getStatus: HttpStatusCode = HttpStatusCode.OK,
    ) = mockHttpClient(
        mapOf(
            "/projects/p1" to json(getJson, getStatus, method = null),
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
}
