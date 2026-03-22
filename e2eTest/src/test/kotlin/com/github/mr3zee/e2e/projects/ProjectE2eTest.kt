package com.github.mr3zee.e2e.projects

import androidx.compose.ui.test.*
import com.github.mr3zee.e2e.E2eTestBase
import com.github.mr3zee.login
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class ProjectE2eTest : E2eTestBase() {

    @Test
    fun `create project through UI`() = runComposeUiTest {
        directClient.login("proj-create-user", "TestPass123")

        loginAndCreateTeamViaUi("proj-create-user", "TestPass123", "Proj Team")

        // Now on project_list_screen with active team
        onNodeWithTag("create_project_fab").performClick()
        waitForIdle()

        waitUntil(timeoutMillis = 5_000L) {
            onAllNodesWithTag("project_name_input").fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithTag("project_name_input").performTextInput("E2E Created Project")
        waitForIdle()

        onNodeWithTag("create_project_confirm").performClick()

        // Project creation navigates to DAG editor
        waitUntil(timeoutMillis = 10_000L) {
            onAllNodesWithTag("dag_editor_screen").fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithTag("dag_editor_screen").assertExists()
    }

    @Test
    fun `open project in DAG editor`() = runComposeUiTest {
        directClient.login("proj-editor-user", "TestPass123")

        loginAndCreateTeamViaUi("proj-editor-user", "TestPass123", "Editor Team")

        // Create a project first
        onNodeWithTag("create_project_fab").performClick()
        waitForIdle()
        waitUntil(timeoutMillis = 5_000L) {
            onAllNodesWithTag("project_name_input").fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithTag("project_name_input").performTextInput("Editor Project")
        waitForIdle()
        onNodeWithTag("create_project_confirm").performClick()

        // Should navigate to DAG editor
        waitUntil(timeoutMillis = 10_000L) {
            onAllNodesWithTag("dag_editor_screen").fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithTag("dag_canvas").assertExists()
        onNodeWithTag("undo_button", useUnmergedTree = true).assertExists()
    }
}
