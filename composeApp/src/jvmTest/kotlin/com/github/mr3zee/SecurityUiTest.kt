package com.github.mr3zee

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import com.github.mr3zee.api.AuthApiClient
import com.github.mr3zee.api.ProjectApiClient
import com.github.mr3zee.api.ReleaseApiClient
import com.github.mr3zee.auth.AuthViewModel
import com.github.mr3zee.auth.LoginScreen
import com.github.mr3zee.model.TeamId
import com.github.mr3zee.projects.ProjectListScreen
import com.github.mr3zee.projects.ProjectListViewModel
import com.github.mr3zee.releases.ReleaseListScreen
import com.github.mr3zee.releases.ReleaseListViewModel
import io.ktor.http.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class SecurityUiTest {

    private val now = "2026-03-13T00:00:00Z"
    private val pollingDisabled = 999_999_999L

    // ---- Issue #18: Archive/delete hidden for non-leads on release list ----

    @Test
    fun `release list hides archive and delete menu for non-lead users`() = runComposeUiTest {
        val releases = """[
            {"id":"r1","projectTemplateId":"p1","status":"SUCCEEDED","dagSnapshot":{"blocks":[],"edges":[],"positions":{}},"parameters":[],"startedAt":"$now","finishedAt":"$now"}
        ]"""
        val client = mockHttpClient(mapOf(
            "/releases" to json("""{"releases":$releases}"""),
            "/projects" to json("""{"projects":[]}"""),
        ))
        val vm = ReleaseListViewModel(ReleaseApiClient(client), ProjectApiClient(client), MutableStateFlow(TeamId("test-team")), pollingIntervalMs = pollingDisabled)

        setContent {
            MaterialTheme {
                ReleaseListScreen(viewModel = vm, onViewRelease = {}, onBack = {}, isTeamLead = false)
            }
        }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("release_item_r1", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }

        // The MoreVert menu button should not exist for non-lead users
        onNodeWithTag("release_menu_r1", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun `release list shows archive and delete menu for lead users`() = runComposeUiTest {
        val releases = """[
            {"id":"r1","projectTemplateId":"p1","status":"SUCCEEDED","dagSnapshot":{"blocks":[],"edges":[],"positions":{}},"parameters":[],"startedAt":"$now","finishedAt":"$now"}
        ]"""
        val client = mockHttpClient(mapOf(
            "/releases" to json("""{"releases":$releases}"""),
            "/projects" to json("""{"projects":[]}"""),
        ))
        val vm = ReleaseListViewModel(ReleaseApiClient(client), ProjectApiClient(client), MutableStateFlow(TeamId("test-team")), pollingIntervalMs = pollingDisabled)

        setContent {
            MaterialTheme {
                ReleaseListScreen(viewModel = vm, onViewRelease = {}, onBack = {}, isTeamLead = true)
            }
        }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("release_item_r1", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }

        // The MoreVert menu button should exist for lead users on terminal releases
        onNodeWithTag("release_menu_r1", useUnmergedTree = true).assertExists()
    }

    // ---- Issue #18: Delete hidden for non-leads on project list ----

    private val projectsJson = """{"projects":[
        {"id":"p1","name":"My Pipeline","description":"A test pipeline","dagGraph":{"blocks":[
            {"kind":"action","id":"b1","name":"Build","type":"TEAMCITY_BUILD","parameters":[],"outputs":[],"timeoutSeconds":null,"connectionId":null}
        ],"edges":[],"positions":{}},"parameters":[],"createdAt":"$now","updatedAt":"$now"}
    ]}"""

    @Test
    fun `project list hides delete menu for non-lead users`() = runComposeUiTest {
        val client = mockHttpClient(mapOf("/projects" to json(projectsJson)))
        val vm = ProjectListViewModel(ProjectApiClient(client), MutableStateFlow(TeamId("test-team")))

        setContent {
            MaterialTheme {
                ProjectListScreen(viewModel = vm, onEditProject = {}, isTeamLead = false)
            }
        }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithText("My Pipeline").fetchSemanticsNodes().isNotEmpty()
        }

        // The overflow menu button should not exist for non-lead users
        onNodeWithTag("project_menu_p1", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun `project list shows delete menu for lead users`() = runComposeUiTest {
        val client = mockHttpClient(mapOf("/projects" to json(projectsJson)))
        val vm = ProjectListViewModel(ProjectApiClient(client), MutableStateFlow(TeamId("test-team")))

        setContent {
            MaterialTheme {
                ProjectListScreen(viewModel = vm, onEditProject = {}, isTeamLead = true)
            }
        }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithText("My Pipeline").fetchSemanticsNodes().isNotEmpty()
        }

        // The overflow menu button should exist for lead users
        onNodeWithTag("project_menu_p1", useUnmergedTree = true).assertExists()
    }

    // ---- Issue #23: Password cleared after login submission ----

    @Test
    fun `login form clears password after submission`() = runComposeUiTest {
        val viewModel = AuthViewModel(AuthApiClient(mockHttpClient(
            mapOf("/auth/login" to json("""{"username":"admin"}"""))
        )))

        setContent { MaterialTheme { LoginScreen(viewModel = viewModel) } }

        onNodeWithTag("login_username").performTextInput("admin")
        onNodeWithTag("login_password").performTextInput("secret123")
        onNodeWithTag("login_button").assertIsEnabled()

        onNodeWithTag("login_button").performClick()
        waitForIdle()

        // After submission the password field should be cleared,
        // which means the sign-in button should be disabled (password is empty)
        onNodeWithTag("login_button").assertIsNotEnabled()
    }

    @Test
    fun `register form clears passwords after submission`() = runComposeUiTest {
        val viewModel = AuthViewModel(AuthApiClient(mockHttpClient(
            mapOf("/auth/register" to json("""{"username":"newuser"}"""))
        )))

        setContent { MaterialTheme { LoginScreen(viewModel = viewModel) } }

        // Switch to register mode
        onNodeWithTag("toggle_auth_mode").performClick()
        waitForIdle()

        onNodeWithTag("login_username").performTextInput("newuser")
        onNodeWithTag("login_password").performTextInput("password123")
        onNodeWithTag("login_confirm_password").performTextInput("password123")

        onNodeWithTag("register_button").assertIsEnabled()
        onNodeWithTag("register_button").performClick()
        waitForIdle()

        // After submission both password fields should be cleared,
        // so the register button should be disabled
        onNodeWithTag("register_button").assertIsNotEnabled()
    }
}
