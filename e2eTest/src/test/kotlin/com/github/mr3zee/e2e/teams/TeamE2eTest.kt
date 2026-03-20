package com.github.mr3zee.e2e.teams

import androidx.compose.ui.test.*
import com.github.mr3zee.App
import com.github.mr3zee.e2e.E2eTestBase
import com.github.mr3zee.login
import kotlinx.coroutines.runBlocking
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class TeamE2eTest : E2eTestBase() {

    @Test
    fun `create team through UI and verify it appears`() = runComposeUiTest {
        // Register and log in a user (no teams yet)
        runBlocking { directClient.login("team-user", "TestPass123") }

        setContent { App() }

        // App navigates to team list (no teams)
        waitUntil(timeoutMillis = 10_000L) {
            onAllNodesWithTag("login_screen").fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithTag("login_username").performTextInput("team-user")
        onNodeWithTag("login_password").performTextInput("TestPass123")
        waitForIdle()
        onNodeWithTag("login_button").performClick()

        waitUntil(timeoutMillis = 10_000L) {
            onAllNodesWithTag("team_list_screen").fetchSemanticsNodes().isNotEmpty()
        }

        // Click create team FAB
        onNodeWithTag("create_team_fab").performClick()
        waitForIdle()

        // Fill in the inline team creation form
        waitUntil(timeoutMillis = 5_000L) {
            onAllNodesWithTag("team_name_input").fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithTag("team_name_input").performTextInput("E2E Test Team")
        waitForIdle()

        // Submit creation
        onNodeWithTag("create_team_confirm").performClick()

        // Team should appear in the list
        waitUntil(timeoutMillis = 10_000L) {
            onAllNodesWithText("E2E Test Team").fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithText("E2E Test Team").assertExists()
    }
}
