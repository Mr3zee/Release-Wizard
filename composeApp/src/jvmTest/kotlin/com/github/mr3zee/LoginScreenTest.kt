package com.github.mr3zee

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.*
import com.github.mr3zee.api.AuthApiClient
import com.github.mr3zee.auth.AuthViewModel
import com.github.mr3zee.auth.LoginScreen
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.cookies.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.CompletableDeferred
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
        onNodeWithText("Sign in to your account").assertExists()
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
            onAllNodesWithText("Incorrect username or password", substring = true).fetchSemanticsNodes().isNotEmpty()
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
            onAllNodesWithText("Incorrect username or password", substring = true).fetchSemanticsNodes().isNotEmpty()
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
            onAllNodesWithText("Incorrect username or password", substring = true).fetchSemanticsNodes().isNotEmpty()
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
            onAllNodesWithText("Incorrect username or password", substring = true).fetchSemanticsNodes().isNotEmpty()
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
                    """{"error":"Registration failed","code":"REGISTRATION_FAILED"}""",
                    HttpStatusCode.BadRequest,
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
        onNodeWithTag("login_confirm_password").performTextInput("password123")
        onNodeWithTag("register_button").performClick()

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithText("Registration failed", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithText("Registration failed", substring = true).assertExists()
    }

    // --- Helper: creates a client whose /auth/login response never completes (hangs) ---
    private fun hangingLoginClient(): HttpClient {
        val neverComplete = CompletableDeferred<Unit>()
        return HttpClient(MockEngine { request ->
            // Block forever so the ViewModel stays in loading state
            neverComplete.await()
            respond(
                content = """{"username":"admin"}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }) {
            install(ContentNegotiation) { json(AppJson) }
            install(HttpCookies)
            expectSuccess = true
        }
    }

    private fun registerClient() = mockHttpClient(
        mapOf("/auth/register" to json("""{"username":"newuser"}"""))
    )

    // ==================================================================================
    // QA-LOGIN-1: Register button disabled when confirmPassword empty
    // ==================================================================================
    @Test
    fun `register button disabled when confirm password is empty`() = runComposeUiTest {
        val viewModel = AuthViewModel(AuthApiClient(registerClient()))
        setContent { MaterialTheme { LoginScreen(viewModel = viewModel) } }

        // Switch to register mode
        onNodeWithTag("toggle_auth_mode").performClick()
        waitForIdle()

        // Fill username and password but leave confirm password empty
        onNodeWithTag("login_username").performTextInput("newuser")
        onNodeWithTag("login_password").performTextInput("password123")
        waitForIdle()

        // Register button should be disabled because confirmPassword is empty
        onNodeWithTag("register_button").assertIsNotEnabled()
    }

    // ==================================================================================
    // QA-LOGIN-2: Register button disabled when passwords do not match
    // ==================================================================================
    @Test
    fun `register button disabled when passwords do not match`() = runComposeUiTest {
        val viewModel = AuthViewModel(AuthApiClient(registerClient()))
        setContent { MaterialTheme { LoginScreen(viewModel = viewModel) } }

        // Switch to register mode
        onNodeWithTag("toggle_auth_mode").performClick()
        waitForIdle()

        // Fill all fields but with mismatching passwords
        onNodeWithTag("login_username").performTextInput("newuser")
        onNodeWithTag("login_password").performTextInput("password123")
        onNodeWithTag("login_confirm_password").performTextInput("different456")
        waitForIdle()

        // Register button should be disabled because passwords don't match
        onNodeWithTag("register_button").assertIsNotEnabled()
    }

    // ==================================================================================
    // QA-LOGIN-3: Register button enabled only when all fields match
    // ==================================================================================
    @Test
    fun `register button enabled when all fields filled and passwords match`() = runComposeUiTest {
        val viewModel = AuthViewModel(AuthApiClient(registerClient()))
        setContent { MaterialTheme { LoginScreen(viewModel = viewModel) } }

        // Switch to register mode
        onNodeWithTag("toggle_auth_mode").performClick()
        waitForIdle()

        // Fill all fields with matching passwords
        onNodeWithTag("login_username").performTextInput("newuser")
        onNodeWithTag("login_password").performTextInput("password123")
        onNodeWithTag("login_confirm_password").performTextInput("password123")
        waitForIdle()

        // Register button should now be enabled
        onNodeWithTag("register_button").assertIsEnabled()
    }

    // ==================================================================================
    // QA-LOGIN-4: Confirm password mismatch inline error
    // ==================================================================================
    @Test
    fun `confirm password mismatch shows inline error text`() = runComposeUiTest {
        val viewModel = AuthViewModel(AuthApiClient(registerClient()))
        setContent { MaterialTheme { LoginScreen(viewModel = viewModel) } }

        // Switch to register mode
        onNodeWithTag("toggle_auth_mode").performClick()
        waitForIdle()

        // Fill password and then a different confirm password
        onNodeWithTag("login_password").performTextInput("password123")
        onNodeWithTag("login_confirm_password").performTextInput("mismatch")
        waitForIdle()

        // "Passwords do not match" inline error should appear
        onNodeWithText("Passwords do not match").assertExists()
    }

    @Test
    fun `confirm password mismatch error disappears when passwords match`() = runComposeUiTest {
        val viewModel = AuthViewModel(AuthApiClient(registerClient()))
        setContent { MaterialTheme { LoginScreen(viewModel = viewModel) } }

        // Switch to register mode
        onNodeWithTag("toggle_auth_mode").performClick()
        waitForIdle()

        // Create a mismatch first
        onNodeWithTag("login_password").performTextInput("abc")
        onNodeWithTag("login_confirm_password").performTextInput("xyz")
        waitForIdle()
        onNodeWithText("Passwords do not match").assertExists()

        // Clear confirm password and type matching value
        onNodeWithTag("login_confirm_password").performTextClearance()
        onNodeWithTag("login_confirm_password").performTextInput("abc")
        waitForIdle()

        // Mismatch error should be gone
        onNodeWithText("Passwords do not match").assertDoesNotExist()
    }

    // ==================================================================================
    // QA-LOGIN-5: Loading state disables submit button and shows spinner
    // ==================================================================================
    @Test
    fun `loading state disables login button and shows spinner`() = runComposeUiTest {
        val viewModel = AuthViewModel(AuthApiClient(hangingLoginClient()))
        setContent { MaterialTheme { LoginScreen(viewModel = viewModel) } }

        // Fill credentials
        onNodeWithTag("login_username").performTextInput("admin")
        onNodeWithTag("login_password").performTextInput("pass")
        waitForIdle()

        // Button should be enabled before clicking
        onNodeWithTag("login_button").assertIsEnabled()

        // Click to start login — the request will hang, keeping isLoading = true
        onNodeWithTag("login_button").performClick()

        // Wait for the loading state to kick in (button becomes disabled)
        waitUntil(timeoutMillis = 3000L) {
            onAllNodes(hasTestTag("login_button") and !isEnabled())
                .fetchSemanticsNodes().isNotEmpty()
        }

        // The button text "Sign in" should be replaced by the spinner,
        // so "Sign in" should not be visible while loading
        onNodeWithText("Sign in").assertDoesNotExist()
    }

    // ==================================================================================
    // QA-LOGIN-6: Register mode shows confirm password field
    // ==================================================================================
    @Test
    fun `switching to register mode shows confirm password field`() = runComposeUiTest {
        val viewModel = AuthViewModel(AuthApiClient(loginClient()))
        setContent { MaterialTheme { LoginScreen(viewModel = viewModel) } }

        // In login mode, confirm password should not be visible
        onNodeWithTag("login_confirm_password").assertDoesNotExist()

        // Switch to register mode
        onNodeWithTag("toggle_auth_mode").performClick()
        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("login_confirm_password").fetchSemanticsNodes().isNotEmpty()
        }

        // Now confirm password field should exist
        onNodeWithTag("login_confirm_password").assertExists()
    }

    // ==================================================================================
    // QA-LOGIN-7: Register mode subtitle changes to "Create an account"
    // ==================================================================================
    @Test
    fun `register mode shows create account subtitle`() = runComposeUiTest {
        val viewModel = AuthViewModel(AuthApiClient(loginClient()))
        setContent { MaterialTheme { LoginScreen(viewModel = viewModel) } }

        // Login mode shows "Sign in to your account"
        onNodeWithText("Sign in to your account").assertExists()
        onNodeWithText("Create an account").assertDoesNotExist()

        // Switch to register mode
        onNodeWithTag("toggle_auth_mode").performClick()
        waitForIdle()

        // Register mode shows "Create an account"
        onNodeWithText("Create an account").assertExists()
        onNodeWithText("Sign in to your account").assertDoesNotExist()
    }

    // ==================================================================================
    // QA-LOGIN-8: Register mode button text is "Create Account"
    // ==================================================================================
    @Test
    fun `register mode button text is Create Account`() = runComposeUiTest {
        val viewModel = AuthViewModel(AuthApiClient(registerClient()))
        setContent { MaterialTheme { LoginScreen(viewModel = viewModel) } }

        // Switch to register mode and fill all fields to make button enabled
        onNodeWithTag("toggle_auth_mode").performClick()
        waitForIdle()

        onNodeWithTag("login_username").performTextInput("user")
        onNodeWithTag("login_password").performTextInput("pass")
        onNodeWithTag("login_confirm_password").performTextInput("pass")
        waitForIdle()

        // Button should show "Create Account" text
        onNodeWithText("Create account").assertExists()
    }

    // ==================================================================================
    // QA-LOGIN-9: Confirm password visibility toggle
    // ==================================================================================
    @Test
    fun `confirm password visibility toggle works`() = runComposeUiTest {
        val viewModel = AuthViewModel(AuthApiClient(registerClient()))
        setContent { MaterialTheme { LoginScreen(viewModel = viewModel) } }

        // Switch to register mode
        onNodeWithTag("toggle_auth_mode").performClick()
        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("login_confirm_password_toggle_visibility", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }

        // Before clicking: confirm password toggle should have "Show password" content description
        onNode(
            hasAnyAncestor(hasTestTag("login_confirm_password_toggle_visibility")) and hasContentDescription("Show password"),
            useUnmergedTree = true,
        ).assertExists()

        // Click the confirm password visibility toggle
        onNodeWithTag("login_confirm_password_toggle_visibility", useUnmergedTree = true).performClick()
        waitForIdle()

        // After clicking: should now show "Hide password" content description
        onNode(
            hasAnyAncestor(hasTestTag("login_confirm_password_toggle_visibility")) and hasContentDescription("Hide password"),
            useUnmergedTree = true,
        ).assertExists()

        // Click again to toggle back
        onNodeWithTag("login_confirm_password_toggle_visibility", useUnmergedTree = true).performClick()
        waitForIdle()

        // Should be back to "Show password"
        onNode(
            hasAnyAncestor(hasTestTag("login_confirm_password_toggle_visibility")) and hasContentDescription("Show password"),
            useUnmergedTree = true,
        ).assertExists()
    }

    // ==================================================================================
    // QA-LOGIN-10: Password visibility resets when toggling auth mode
    // ==================================================================================
    @Test
    fun `password visibility resets when switching to register mode`() = runComposeUiTest {
        val viewModel = AuthViewModel(AuthApiClient(loginClient()))
        setContent { MaterialTheme { LoginScreen(viewModel = viewModel) } }

        // Show password in login mode
        onNodeWithTag("login_password_toggle_visibility", useUnmergedTree = true).performClick()
        waitForIdle()
        // Verify the password toggle now shows "Hide password" (using ancestor to disambiguate)
        onNode(
            hasAnyAncestor(hasTestTag("login_password_toggle_visibility")) and hasContentDescription("Hide password"),
            useUnmergedTree = true,
        ).assertExists()

        // Switch to register mode — password visibility should reset to hidden
        onNodeWithTag("toggle_auth_mode", useUnmergedTree = true).performClick()
        waitForIdle()

        // After switching, the password toggle should be back to "Show password"
        // Use ancestor to disambiguate from the confirm password toggle (both exist in register mode)
        onNode(
            hasAnyAncestor(hasTestTag("login_password_toggle_visibility")) and hasContentDescription("Show password"),
            useUnmergedTree = true,
        ).assertExists()
    }

    // ==================================================================================
    // QA-LOGIN-11: Toggle back to login hides confirm password field
    // ==================================================================================
    @Test
    fun `switching back to login mode hides confirm password field`() = runComposeUiTest {
        val viewModel = AuthViewModel(AuthApiClient(loginClient()))
        setContent { MaterialTheme { LoginScreen(viewModel = viewModel) } }

        // Switch to register mode
        onNodeWithTag("toggle_auth_mode").performClick()
        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("login_confirm_password").fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithTag("login_confirm_password").assertExists()

        // Switch back to login mode
        onNodeWithTag("toggle_auth_mode").performClick()
        waitForIdle()

        // Confirm password should disappear (animated, so use waitUntil)
        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("login_confirm_password").fetchSemanticsNodes().isEmpty()
        }
        onNodeWithTag("login_confirm_password").assertDoesNotExist()
    }

    // ==================================================================================
    // QA-LOGIN-12: Register button disabled with only username filled
    // ==================================================================================
    @Test
    fun `register button disabled with only username filled`() = runComposeUiTest {
        val viewModel = AuthViewModel(AuthApiClient(registerClient()))
        setContent { MaterialTheme { LoginScreen(viewModel = viewModel) } }

        // Switch to register mode
        onNodeWithTag("toggle_auth_mode").performClick()
        waitForIdle()

        // Fill only username
        onNodeWithTag("login_username").performTextInput("newuser")
        waitForIdle()

        onNodeWithTag("register_button").assertIsNotEnabled()
    }

    // ==================================================================================
    // QA-LOGIN-13: Keyboard Enter in password field (login mode) submits form
    // ==================================================================================
    @Test
    fun `enter key in password field triggers login`() = runComposeUiTest {
        val viewModel = AuthViewModel(AuthApiClient(failingLoginClient()))
        setContent { MaterialTheme { LoginScreen(viewModel = viewModel) } }

        // Fill credentials
        onNodeWithTag("login_username").performTextInput("admin")
        onNodeWithTag("login_password").performTextInput("wrong")
        waitForIdle()

        // Press Enter in the password field — should trigger login (which will fail)
        onNodeWithTag("login_password").performKeyInput {
            pressKey(Key.Enter)
        }

        // Wait for the error from the failing login
        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithText("Incorrect username or password", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithTag("login_error").assertExists()
    }

    @Test
    fun `enter key in confirm password field triggers register`() = runComposeUiTest {
        val client = mockHttpClient(
            mapOf(
                "/auth/register" to json(
                    """{"error":"Registration failed","code":"REGISTRATION_FAILED"}""",
                    HttpStatusCode.BadRequest,
                ),
            )
        )
        val viewModel = AuthViewModel(AuthApiClient(client))
        setContent { MaterialTheme { LoginScreen(viewModel = viewModel) } }

        // Switch to register mode
        onNodeWithTag("toggle_auth_mode").performClick()
        waitForIdle()

        // Fill all fields with matching passwords
        onNodeWithTag("login_username").performTextInput("taken_user")
        onNodeWithTag("login_password").performTextInput("password123")
        onNodeWithTag("login_confirm_password").performTextInput("password123")
        waitForIdle()

        // Press Enter in confirm password field — should trigger register
        onNodeWithTag("login_confirm_password").performKeyInput {
            pressKey(Key.Enter)
        }

        // Wait for the error from the failing register
        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithText("Registration failed", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithText("Registration failed", substring = true).assertExists()
    }

    @Test
    fun `enter key in username field moves focus to password field`() = runComposeUiTest {
        val viewModel = AuthViewModel(AuthApiClient(loginClient()))
        setContent { MaterialTheme { LoginScreen(viewModel = viewModel) } }

        // Type in username field
        onNodeWithTag("login_username").performTextInput("admin")
        waitForIdle()

        // Press Enter in username — should move focus to password
        onNodeWithTag("login_username").performKeyInput {
            pressKey(Key.Enter)
        }
        waitForIdle()

        // Now type in password field (which should be focused)
        // If focus moved correctly, this text will appear in the password field
        onNodeWithTag("login_password").assertIsFocused()
    }

    @Test
    fun `confirm password field clears when toggling back to login and returning`() = runComposeUiTest {
        val viewModel = AuthViewModel(AuthApiClient(loginClient()))
        setContent { MaterialTheme { LoginScreen(viewModel = viewModel) } }

        // Switch to register mode
        onNodeWithTag("toggle_auth_mode").performClick()
        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("login_confirm_password").fetchSemanticsNodes().isNotEmpty()
        }

        // Type a password that won't match (to create a visible mismatch error)
        onNodeWithTag("login_password").performTextInput("abc")
        onNodeWithTag("login_confirm_password").performTextInput("somepassword")
        waitForIdle()
        // Mismatch error should exist since "abc" != "somepassword"
        onNodeWithText("Passwords do not match").assertExists()

        // Switch back to login mode
        onNodeWithTag("toggle_auth_mode").performClick()
        waitForIdle()

        // Switch back to register mode again
        onNodeWithTag("toggle_auth_mode").performClick()
        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("login_confirm_password").fetchSemanticsNodes().isNotEmpty()
        }

        // Confirm password field was cleared by LaunchedEffect, so the mismatch error
        // should be gone (empty confirmPassword doesn't trigger the mismatch check)
        onNodeWithText("Passwords do not match").assertDoesNotExist()
    }

    @Test
    fun `register button disabled when password filled but username empty`() = runComposeUiTest {
        val viewModel = AuthViewModel(AuthApiClient(registerClient()))
        setContent { MaterialTheme { LoginScreen(viewModel = viewModel) } }

        // Switch to register mode
        onNodeWithTag("toggle_auth_mode").performClick()
        waitForIdle()

        // Fill only password and confirm password, leave username empty
        onNodeWithTag("login_password").performTextInput("password123")
        onNodeWithTag("login_confirm_password").performTextInput("password123")
        waitForIdle()

        onNodeWithTag("register_button").assertIsNotEnabled()
    }

    @Test
    fun `password requirements hint shown in register mode`() = runComposeUiTest {
        val viewModel = AuthViewModel(AuthApiClient(registerClient()))
        setContent { MaterialTheme { LoginScreen(viewModel = viewModel) } }

        // In login mode, password requirements should not be shown
        onNodeWithText("At least 12 characters", substring = true).assertDoesNotExist()

        // Switch to register mode
        onNodeWithTag("toggle_auth_mode").performClick()
        waitForIdle()

        // Password requirements hint should now be visible
        onNodeWithText("At least 12 characters", substring = true).assertExists()
    }

    @Test
    fun `toggle auth mode button text changes`() = runComposeUiTest {
        val viewModel = AuthViewModel(AuthApiClient(loginClient()))
        setContent { MaterialTheme { LoginScreen(viewModel = viewModel) } }

        // In login mode, toggle button should say "Don't have an account? Create account"
        onNodeWithText("Don't have an account? Create account").assertExists()

        // Switch to register mode
        onNodeWithTag("toggle_auth_mode").performClick()
        waitForIdle()

        // Now toggle button should say "Already have an account? Sign in"
        onNodeWithText("Already have an account? Sign in").assertExists()
        onNodeWithText("Don't have an account? Create account").assertDoesNotExist()
    }
}
