package com.github.mr3zee

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import com.github.mr3zee.api.AuthApiClient
import com.github.mr3zee.auth.AuthViewModel
import com.github.mr3zee.auth.LoginScreen
import io.ktor.http.*
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class LoginScreenTest {

    private fun loginClient() = mockHttpClient(
        mapOf("/auth/login" to json("""{"username":"admin"}"""))
    )

    private fun failingLoginClient() = mockHttpClient(
        mapOf("/auth/login" to json("""{"error":"Invalid credentials","code":"INVALID_CREDENTIALS"}""", HttpStatusCode.Unauthorized))
    )

    @Test
    fun `login screen renders with all elements`() = runComposeUiTest {
        val viewModel = AuthViewModel(AuthApiClient(loginClient()))
        setContent { MaterialTheme { LoginScreen(viewModel = viewModel) } }

        onNodeWithTag("login_screen").assertExists()
        onNodeWithTag("login_username").assertExists()
        onNodeWithTag("login_password").assertExists()
        onNodeWithTag("login_button").assertExists()
        onNodeWithText("Release Wizard").assertExists()
        onNodeWithText("Sign in to continue").assertExists()
    }

    @Test
    fun `password visibility toggle exists`() = runComposeUiTest {
        val viewModel = AuthViewModel(AuthApiClient(loginClient()))
        setContent { MaterialTheme { LoginScreen(viewModel = viewModel) } }

        onNodeWithTag("login_password_toggle_visibility", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `password visibility toggle changes content description`() = runComposeUiTest {
        val viewModel = AuthViewModel(AuthApiClient(loginClient()))
        setContent { MaterialTheme { LoginScreen(viewModel = viewModel) } }

        // Initially should show "Show password" (password is hidden)
        onNodeWithContentDescription("Show password", useUnmergedTree = true).assertExists()
        onNodeWithContentDescription("Hide password", useUnmergedTree = true).assertDoesNotExist()

        // Click toggle
        onNodeWithTag("login_password_toggle_visibility", useUnmergedTree = true).performClick()
        waitForIdle()

        // Now should show "Hide password" (password is visible)
        onNodeWithContentDescription("Hide password", useUnmergedTree = true).assertExists()
        onNodeWithContentDescription("Show password", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun `sign in button disabled when fields empty`() = runComposeUiTest {
        val viewModel = AuthViewModel(AuthApiClient(loginClient()))
        setContent { MaterialTheme { LoginScreen(viewModel = viewModel) } }

        onNodeWithTag("login_button").assertIsNotEnabled()
    }

    @Test
    fun `sign in button enables when both fields filled`() = runComposeUiTest {
        val viewModel = AuthViewModel(AuthApiClient(loginClient()))
        setContent { MaterialTheme { LoginScreen(viewModel = viewModel) } }

        onNodeWithTag("login_username").performTextInput("admin")
        onNodeWithTag("login_password").performTextInput("pass")
        onNodeWithTag("login_button").assertIsEnabled()
    }

    @Test
    fun `sign in button disabled with only username`() = runComposeUiTest {
        val viewModel = AuthViewModel(AuthApiClient(loginClient()))
        setContent { MaterialTheme { LoginScreen(viewModel = viewModel) } }

        onNodeWithTag("login_username").performTextInput("admin")
        onNodeWithTag("login_button").assertIsNotEnabled()
    }

    @Test
    fun `login shows error on failure`() = runComposeUiTest {
        val viewModel = AuthViewModel(AuthApiClient(failingLoginClient()))
        setContent { MaterialTheme { LoginScreen(viewModel = viewModel) } }

        onNodeWithTag("login_username").performTextInput("admin")
        onNodeWithTag("login_password").performTextInput("wrong")
        onNodeWithTag("login_button").performClick()

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithText("Invalid credentials").fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithTag("login_error").assertExists()
    }

    @Test
    fun `sign in button disabled with only password`() = runComposeUiTest {
        val viewModel = AuthViewModel(AuthApiClient(loginClient()))
        setContent { MaterialTheme { LoginScreen(viewModel = viewModel) } }

        onNodeWithTag("login_password").performTextInput("pass")
        onNodeWithTag("login_button").assertIsNotEnabled()
    }

    @Test
    fun `error clears when user types in username`() = runComposeUiTest {
        val viewModel = AuthViewModel(AuthApiClient(failingLoginClient()))
        setContent { MaterialTheme { LoginScreen(viewModel = viewModel) } }

        onNodeWithTag("login_username").performTextInput("admin")
        onNodeWithTag("login_password").performTextInput("wrong")
        onNodeWithTag("login_button").performClick()

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithText("Invalid credentials").fetchSemanticsNodes().isNotEmpty()
        }

        // Typing in username should dismiss error
        onNodeWithTag("login_username").performTextInput("x")
        waitForIdle()
        onNodeWithTag("login_error").assertDoesNotExist()
    }

    @Test
    fun `error clears when user types in password`() = runComposeUiTest {
        val viewModel = AuthViewModel(AuthApiClient(failingLoginClient()))
        setContent { MaterialTheme { LoginScreen(viewModel = viewModel) } }

        onNodeWithTag("login_username").performTextInput("admin")
        onNodeWithTag("login_password").performTextInput("wrong")
        onNodeWithTag("login_button").performClick()

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithText("Invalid credentials").fetchSemanticsNodes().isNotEmpty()
        }

        // Typing in password should dismiss error
        onNodeWithTag("login_password").performTextInput("x")
        waitForIdle()
        onNodeWithTag("login_error").assertDoesNotExist()
    }

    @Test
    fun `error clears when toggling auth mode`() = runComposeUiTest {
        val viewModel = AuthViewModel(AuthApiClient(failingLoginClient()))
        setContent { MaterialTheme { LoginScreen(viewModel = viewModel) } }

        onNodeWithTag("login_username").performTextInput("admin")
        onNodeWithTag("login_password").performTextInput("wrong")
        onNodeWithTag("login_button").performClick()

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithText("Invalid credentials").fetchSemanticsNodes().isNotEmpty()
        }

        // Toggling auth mode should dismiss error
        onNodeWithTag("toggle_auth_mode").performClick()
        waitForIdle()
        onNodeWithTag("login_error").assertDoesNotExist()
    }

    @Test
    fun `register shows server validation error`() = runComposeUiTest {
        val client = mockHttpClient(
            mapOf(
                "/auth/register" to json(
                    """{"error":"Username already taken","code":"USERNAME_TAKEN"}""",
                    HttpStatusCode.Conflict,
                ),
            )
        )
        val viewModel = AuthViewModel(AuthApiClient(client))
        setContent { MaterialTheme { LoginScreen(viewModel = viewModel) } }

        // Switch to register mode
        onNodeWithTag("toggle_auth_mode").performClick()
        waitForIdle()

        onNodeWithTag("login_username").performTextInput("taken_user")
        onNodeWithTag("login_password").performTextInput("password123")
        onNodeWithTag("register_button").performClick()

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithText("Username already taken").fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithText("Username already taken").assertExists()
    }
}
