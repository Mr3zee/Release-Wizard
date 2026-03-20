package com.github.mr3zee.e2e.auth

import androidx.compose.ui.test.*
import com.github.mr3zee.App
import com.github.mr3zee.createTestTeam
import com.github.mr3zee.e2e.E2eTestBase
import com.github.mr3zee.login
import kotlinx.coroutines.runBlocking
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class AuthE2eTest : E2eTestBase() {

    @Test
    fun `app shows login screen when no session exists`() = runComposeUiTest {
        setContent { App() }

        waitUntil(timeoutMillis = 10_000L) {
            onAllNodesWithTag("login_screen").fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithTag("login_screen").assertExists()
        onNodeWithTag("login_username").assertExists()
        onNodeWithTag("login_password").assertExists()
        onNodeWithTag("login_button").assertExists()
    }

    @Test
    fun `register new user navigates to team list`() = runComposeUiTest {
        setContent { App() }

        waitUntil(timeoutMillis = 10_000L) {
            onAllNodesWithTag("login_screen").fetchSemanticsNodes().isNotEmpty()
        }

        // Switch to register mode
        onNodeWithTag("toggle_auth_mode").performClick()
        waitForIdle()

        // Fill in registration form
        onNodeWithTag("login_username").performTextInput("e2e-reg-user")
        onNodeWithTag("login_password").performTextInput("TestPass123")
        onNodeWithTag("login_confirm_password").performTextInput("TestPass123")
        waitForIdle()

        // Submit registration
        onNodeWithTag("register_button").performClick()

        // First user has no teams → team list screen
        waitUntil(timeoutMillis = 10_000L) {
            onAllNodesWithTag("team_list_screen").fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithTag("team_list_screen").assertExists()
    }

    @Test
    fun `login with existing user navigates past login screen`() = runComposeUiTest {
        // Pre-create user and team via direct HTTP (separate session from App's client)
        runBlocking {
            directClient.login("login-user", "TestPass123")
            directClient.createTestTeam("Login Team")
        }

        setContent { App() }

        waitUntil(timeoutMillis = 10_000L) {
            onAllNodesWithTag("login_screen").fetchSemanticsNodes().isNotEmpty()
        }

        // Fill in login form
        onNodeWithTag("login_username").performTextInput("login-user")
        onNodeWithTag("login_password").performTextInput("TestPass123")
        waitForIdle()

        // Submit login
        onNodeWithTag("login_button").performClick()

        // After login, user navigates away from login screen
        // (to project list if teams auto-selected, or team list otherwise)
        waitUntil(timeoutMillis = 10_000L) {
            onAllNodesWithTag("project_list_screen").fetchSemanticsNodes().isNotEmpty() ||
                onAllNodesWithTag("team_list_screen").fetchSemanticsNodes().isNotEmpty()
        }
        // Verify login screen is gone
        onNodeWithTag("login_screen").assertDoesNotExist()
    }

    @Test
    fun `logout returns to login screen`() = runComposeUiTest {
        // Pre-create a user with a team
        runBlocking {
            directClient.login("logout-user", "TestPass123")
            directClient.createTestTeam("Logout Team")
        }
        //data setup done")

        setContent { App() }

        waitUntil(timeoutMillis = 10_000L) {
            onAllNodesWithTag("login_screen").fetchSemanticsNodes().isNotEmpty()
        }
        //login screen appeared")

        // Log in through UI
        onNodeWithTag("login_username").performTextInput("logout-user")
        onNodeWithTag("login_password").performTextInput("TestPass123")
        waitForIdle()
        onNodeWithTag("login_button").performClick()
        //login button clicked")

        // Wait for navigation past login screen
        waitUntil(timeoutMillis = 10_000L) {
            onAllNodesWithTag("project_list_screen").fetchSemanticsNodes().isNotEmpty() ||
                onAllNodesWithTag("team_list_screen").fetchSemanticsNodes().isNotEmpty()
        }
        //navigated past login")

        // Click sign out (two-click confirmation: first click shows confirm, second signs out)
        onNodeWithTag("sidebar_sign_out", useUnmergedTree = true).performClick()
        waitForIdle()
        onNodeWithTag("sidebar_sign_out", useUnmergedTree = true).performClick()
        //sign out confirmed")

        // Should return to login screen
        waitUntil(timeoutMillis = 10_000L) {
            onAllNodesWithTag("login_screen").fetchSemanticsNodes().isNotEmpty()
        }
        //back to login screen")
        onNodeWithTag("login_screen").assertExists()
    }
}
