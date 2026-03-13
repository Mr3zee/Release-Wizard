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
        val client = mockHttpClient(
            mapOf("/auth/login" to json("""{"error":"bad"}""", HttpStatusCode.Unauthorized))
        )
        val viewModel = AuthViewModel(AuthApiClient(client))
        setContent { MaterialTheme { LoginScreen(viewModel = viewModel) } }

        onNodeWithTag("login_username").performTextInput("admin")
        onNodeWithTag("login_password").performTextInput("wrong")
        onNodeWithTag("login_button").performClick()

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithText("Invalid credentials").fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithText("Invalid credentials").assertExists()
    }
}
