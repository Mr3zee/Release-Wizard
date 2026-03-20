package com.github.mr3zee.e2e.releases

import androidx.compose.ui.test.*
import com.github.mr3zee.e2e.E2eTestBase
import com.github.mr3zee.login
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class ReleaseE2eTest : E2eTestBase() {

    @Test
    fun `navigate to release list`() = runComposeUiTest {
        directClient.login("rel-nav-user", "TestPass123")

        loginAndCreateTeamViaUi("rel-nav-user", "TestPass123", "Release Team")
        navigateToSection("sidebar_nav_releases", "release_list_screen")

        onNodeWithTag("release_list_screen").assertExists()
    }

    @org.junit.Ignore("DAG editor back navigation + sidebar not visible on non-top-level screens — needs rework")
    @Test
    fun `start release from UI`() = runComposeUiTest {
        directClient.login("rel-start-user", "TestPass123")

        loginAndCreateTeamViaUi("rel-start-user", "TestPass123", "Start Release Team")

        // Create a project first (through UI so it has proper team association)
        onNodeWithTag("create_project_fab").performClick()
        waitForIdle()
        waitUntil(timeoutMillis = 5_000L) {
            onAllNodesWithTag("project_name_input").fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithTag("project_name_input").performTextInput("Release Source Project")
        waitForIdle()
        onNodeWithTag("create_project_confirm").performClick()

        // Wait for DAG editor
        waitUntil(timeoutMillis = 10_000L) {
            onAllNodesWithTag("dag_editor_screen").fetchSemanticsNodes().isNotEmpty()
        }

        // DAG editor is not a top-level screen — sidebar is hidden.
        // Go back to project list first, then navigate to releases.
        onNodeWithTag("back_button", useUnmergedTree = true).performClick()
        waitUntil(timeoutMillis = 10_000L) {
            onAllNodesWithTag("project_list_screen").fetchSemanticsNodes().isNotEmpty()
        }
        navigateToSection("sidebar_nav_releases", "release_list_screen")

        // Click start release
        onNodeWithTag("start_release_fab").performClick()
        waitForIdle()

        waitUntil(timeoutMillis = 5_000L) {
            onAllNodesWithTag("start_release_form").fetchSemanticsNodes().isNotEmpty()
        }

        // Select the project
        onNodeWithTag("project_dropdown").performClick()
        waitForIdle()
        waitUntil(timeoutMillis = 5_000L) {
            onAllNodesWithText("Release Source Project").fetchSemanticsNodes().isNotEmpty()
        }
        onAllNodesWithText("Release Source Project").onFirst().performClick()
        waitForIdle()

        // Start the release
        onNodeWithTag("start_release_confirm").performClick()

        // Should navigate to release detail
        waitUntil(timeoutMillis = 15_000L) {
            onAllNodesWithTag("release_detail_screen").fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithTag("release_detail_screen").assertExists()
    }
}
