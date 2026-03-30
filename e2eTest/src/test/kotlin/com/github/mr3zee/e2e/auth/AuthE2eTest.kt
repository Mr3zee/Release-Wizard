package com.github.mr3zee.e2e.auth

import androidx.compose.ui.test.*
import com.github.mr3zee.App
import com.github.mr3zee.e2e.E2eTestBase
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
    fun `register new user navigates to pending approval`() = runComposeUiTest {
        setContent { App() }

        waitUntil(timeoutMillis = 10_000L) {
            onAllNodesWithTag("login_screen").fetchSemanticsNodes().isNotEmpty()
        }

        onNodeWithTag("toggle_auth_mode").performClick()
        waitForIdle()

        onNodeWithTag("login_username").performTextInput("e2e-reg-user")
        onNodeWithTag("login_password").performTextInput("TestPass123")
        onNodeWithTag("login_confirm_password").performTextInput("TestPass123")
        waitForIdle()

        onNodeWithTag("register_button").performClick()

        // Non-first users require admin approval
        waitUntil(timeoutMillis = 10_000L) {
            onAllNodesWithTag("pending_approval_screen").fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithTag("pending_approval_screen").assertExists()
    }

    @Test
    fun `login with existing user navigates past login screen`() = runComposeUiTest {
        loginAndApprove("login-user", "TestPass123")

        loginViaUi("login-user", "TestPass123")

        onNodeWithTag("login_screen").assertDoesNotExist()
    }

    @Test
    fun `logout returns to login screen`() = runComposeUiTest {
        loginAndApprove("logout-user", "TestPass123")

        loginViaUi("logout-user", "TestPass123")

        // Two-click sign out confirmation
        onNodeWithTag("sidebar_sign_out", useUnmergedTree = true).performClick()
        waitForIdle()
        onNodeWithTag("sidebar_sign_out", useUnmergedTree = true).performClick()

        waitUntil(timeoutMillis = 10_000L) {
            onAllNodesWithTag("login_screen").fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithTag("login_screen").assertExists()
    }
}
