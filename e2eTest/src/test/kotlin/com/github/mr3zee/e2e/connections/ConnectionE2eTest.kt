package com.github.mr3zee.e2e.connections

import androidx.compose.ui.test.*
import com.github.mr3zee.App
import com.github.mr3zee.createTestTeam
import com.github.mr3zee.e2e.E2eTestBase
import com.github.mr3zee.login
import kotlinx.coroutines.runBlocking
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class ConnectionE2eTest : E2eTestBase() {

    @Test
    fun `navigate to connections screen`() = runComposeUiTest {
        runBlocking {
            directClient.login("conn-user", "TestPass123")
            directClient.createTestTeam("Conn Team")
        }

        setContent { App() }

        // Login
        waitUntil(timeoutMillis = 10_000L) {
            onAllNodesWithTag("login_screen").fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithTag("login_username").performTextInput("conn-user")
        onNodeWithTag("login_password").performTextInput("TestPass123")
        waitForIdle()
        onNodeWithTag("login_button").performClick()

        // Wait for initial navigation
        waitUntil(timeoutMillis = 10_000L) {
            onAllNodesWithTag("project_list_screen").fetchSemanticsNodes().isNotEmpty() ||
                onAllNodesWithTag("team_list_screen").fetchSemanticsNodes().isNotEmpty()
        }

        // Navigate to connections via sidebar
        onNodeWithTag("sidebar_nav_connections", useUnmergedTree = true).performClick()

        waitUntil(timeoutMillis = 10_000L) {
            onAllNodesWithText("Connections").fetchSemanticsNodes().isNotEmpty()
        }
    }
}
