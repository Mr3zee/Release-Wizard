package com.github.mr3zee.e2e.projects

import androidx.compose.ui.test.*
import com.github.mr3zee.App
import com.github.mr3zee.createTestTeam
import com.github.mr3zee.e2e.E2eTestBase
import com.github.mr3zee.login
import kotlinx.coroutines.runBlocking
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class ProjectE2eTest : E2eTestBase() {

    @Test
    fun `navigate to project list via sidebar`() = runComposeUiTest {
        runBlocking {
            directClient.login("proj-user", "TestPass123")
            directClient.createTestTeam("Proj Team")
        }

        setContent { App() }

        // Login
        waitUntil(timeoutMillis = 10_000L) {
            onAllNodesWithTag("login_screen").fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithTag("login_username").performTextInput("proj-user")
        onNodeWithTag("login_password").performTextInput("TestPass123")
        waitForIdle()
        onNodeWithTag("login_button").performClick()

        // Wait for initial navigation (team list since login response has no teams)
        waitUntil(timeoutMillis = 10_000L) {
            onAllNodesWithTag("team_list_screen").fetchSemanticsNodes().isNotEmpty() ||
                onAllNodesWithTag("project_list_screen").fetchSemanticsNodes().isNotEmpty()
        }

        // If already on project list, we're done
        if (onAllNodesWithTag("project_list_screen").fetchSemanticsNodes().isNotEmpty()) {
            onNodeWithTag("project_list_screen").assertExists()
            return@runComposeUiTest
        }

        // Navigate to projects via sidebar
        onNodeWithTag("sidebar_nav_projects", useUnmergedTree = true).performClick()

        // Project list should appear (even if empty — the screen itself should render)
        waitUntil(timeoutMillis = 10_000L) {
            onAllNodesWithTag("project_list_screen").fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithTag("project_list_screen").assertExists()
    }
}
