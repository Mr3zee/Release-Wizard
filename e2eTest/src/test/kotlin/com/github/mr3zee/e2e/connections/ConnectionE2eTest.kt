package com.github.mr3zee.e2e.connections

import androidx.compose.ui.test.*
import com.github.mr3zee.e2e.E2eTestBase
import com.github.mr3zee.login
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class ConnectionE2eTest : E2eTestBase() {

    @Test
    fun `navigate to connections screen`() = runComposeUiTest {
        directClient.login("conn-nav-user", "TestPass123")

        loginAndCreateTeamViaUi("conn-nav-user", "TestPass123", "Conn Team")
        navigateToSection("sidebar_nav_connections", "connection_list_screen")

        onNodeWithTag("connection_list_screen").assertExists()
    }

    @Test
    fun `create slack connection through UI`() = runComposeUiTest {
        directClient.login("conn-create-user", "TestPass123")

        loginAndCreateTeamViaUi("conn-create-user", "TestPass123", "Conn Create Team")
        navigateToSection("sidebar_nav_connections", "connection_list_screen")

        onNodeWithTag("create_connection_fab").performClick()

        waitUntil(timeoutMillis = 10_000L) {
            onAllNodesWithTag("connection_form_screen").fetchSemanticsNodes().isNotEmpty()
        }

        onNodeWithTag("connection_name_field").performTextInput("E2E Slack")
        waitForIdle()

        // Select Slack type
        onNodeWithTag("connection_type_selector").performClick()
        waitForIdle()
        waitUntil(timeoutMillis = 5_000L) {
            onAllNodesWithText("Slack").fetchSemanticsNodes().isNotEmpty()
        }
        onAllNodesWithText("Slack").onFirst().performClick()
        waitForIdle()

        // Fill webhook URL
        waitUntil(timeoutMillis = 5_000L) {
            onAllNodesWithTag("slack_webhook_url").fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithTag("slack_webhook_url").performTextInput("https://hooks.slack.com/services/T00/B00/xxx")
        waitForIdle()

        onNodeWithTag("save_connection_button").performClick()

        // Should navigate back to list with the new connection
        waitUntil(timeoutMillis = 10_000L) {
            onAllNodesWithTag("connection_list_screen").fetchSemanticsNodes().isNotEmpty()
        }
        waitUntil(timeoutMillis = 10_000L) {
            onAllNodesWithText("E2E Slack").fetchSemanticsNodes().isNotEmpty()
        }
    }
}
