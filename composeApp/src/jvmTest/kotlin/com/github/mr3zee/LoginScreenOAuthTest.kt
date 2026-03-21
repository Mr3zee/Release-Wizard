package com.github.mr3zee

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import com.github.mr3zee.api.AuthApiClient
import com.github.mr3zee.auth.AuthViewModel
import com.github.mr3zee.auth.LoginScreen
import io.ktor.http.*
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class LoginScreenOAuthTest {

    private fun loginClient() = mockHttpClient(
        mapOf("/auth/login" to json("""{"username":"admin"}"""))
    )

    @Test
    fun `Google button visible when showGoogleLogin is true in login mode`() = runComposeUiTest {
        val viewModel = AuthViewModel(AuthApiClient(loginClient()))
        setContent {
            MaterialTheme {
                LoginScreen(viewModel = viewModel, showGoogleLogin = true)
            }
        }

        onNodeWithTag("login_google_button").assertExists()
        onNodeWithTag("login_divider_or").assertExists()
    }

    @Test
    fun `Google button hidden when showGoogleLogin is false`() = runComposeUiTest {
        val viewModel = AuthViewModel(AuthApiClient(loginClient()))
        setContent {
            MaterialTheme {
                LoginScreen(viewModel = viewModel, showGoogleLogin = false)
            }
        }

        onNodeWithTag("login_google_button").assertDoesNotExist()
        onNodeWithTag("login_divider_or").assertDoesNotExist()
    }

    @Test
    fun `Google button hidden in register mode`() = runComposeUiTest {
        val viewModel = AuthViewModel(AuthApiClient(loginClient()))
        setContent {
            MaterialTheme {
                LoginScreen(viewModel = viewModel, showGoogleLogin = true)
            }
        }

        // Initially in login mode — Google button should be visible
        onNodeWithTag("login_google_button").assertExists()

        // Switch to register mode
        onNodeWithTag("toggle_auth_mode").performClick()

        // Google button should be hidden in register mode
        onNodeWithTag("login_google_button").assertDoesNotExist()
        onNodeWithTag("login_divider_or").assertDoesNotExist()
    }

    @Test
    fun `Google button click calls onGoogleSignIn callback`() = runComposeUiTest {
        val viewModel = AuthViewModel(AuthApiClient(loginClient()))
        var called = false
        setContent {
            MaterialTheme {
                LoginScreen(
                    viewModel = viewModel,
                    showGoogleLogin = true,
                    onGoogleSignIn = { called = true },
                )
            }
        }

        onNodeWithTag("login_google_button").performClick()
        assertTrue(called, "onGoogleSignIn callback should be called")
    }

    @Test
    fun `divider appears between submit button and Google button`() = runComposeUiTest {
        val viewModel = AuthViewModel(AuthApiClient(loginClient()))
        setContent {
            MaterialTheme {
                LoginScreen(viewModel = viewModel, showGoogleLogin = true)
            }
        }

        // All three elements exist in order: submit, divider, google button
        onNodeWithTag("login_button").assertExists()
        onNodeWithTag("login_divider_or").assertExists()
        onNodeWithTag("login_google_button").assertExists()
    }
}
