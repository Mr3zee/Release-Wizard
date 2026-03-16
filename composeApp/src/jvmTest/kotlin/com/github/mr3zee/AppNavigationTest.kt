package com.github.mr3zee

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import com.github.mr3zee.api.AuthApiClient
import com.github.mr3zee.api.ConnectionApiClient
import com.github.mr3zee.api.ProjectApiClient
import com.github.mr3zee.api.ReleaseApiClient
import com.github.mr3zee.api.TeamApiClient
import com.github.mr3zee.auth.AuthViewModel
import com.github.mr3zee.auth.LoginScreen
import com.github.mr3zee.connections.ConnectionsViewModel
import com.github.mr3zee.model.TeamId
import com.github.mr3zee.navigation.AppNavigation
import com.github.mr3zee.navigation.NavigationController
import com.github.mr3zee.navigation.Screen
import com.github.mr3zee.projects.ProjectListViewModel
import com.github.mr3zee.releases.ReleaseListViewModel
import io.ktor.client.*
import io.ktor.http.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class AppNavigationTest {

    private fun appClient() = mockHttpClient(mapOf(
        "/auth/me" to json("""{"username":"admin"}"""),
        "/auth/login" to json("""{"username":"admin"}"""),
        "/auth/logout" to json("{}"),
        "/projects" to json("""{"projects":[
            {"id":"p1","name":"Pipeline A","description":"","dagGraph":{"blocks":[],"edges":[],"positions":{}},"parameters":[],"createdAt":"2026-03-13T00:00:00Z","updatedAt":"2026-03-13T00:00:00Z"}
        ]}"""),
        "/projects/p1" to json("""{"project":{"id":"p1","name":"Pipeline A","description":"","dagGraph":{"blocks":[],"edges":[],"positions":{}},"parameters":[],"createdAt":"2026-03-13T00:00:00Z","updatedAt":"2026-03-13T00:00:00Z"}}"""),
        "/connections" to json("""{"connections":[]}"""),
        "/releases" to json("""{"releases":[]}"""),
    ))

    @Composable
    private fun TestApp(httpClient: HttpClient) {
        val authApiClient = remember { AuthApiClient(httpClient) }
        val projectApiClient = remember { ProjectApiClient(httpClient) }
        val connectionApiClient = remember { ConnectionApiClient(httpClient) }
        val releaseApiClient = remember { ReleaseApiClient(httpClient) }
        val teamApiClient = remember { TeamApiClient(httpClient) }
        val authViewModel = remember { AuthViewModel(authApiClient) }
        val activeTeamId = remember { MutableStateFlow<TeamId?>(TeamId("test-team")) }
        val projectListViewModel = remember { ProjectListViewModel(projectApiClient, activeTeamId) }
        val connectionsViewModel = remember { ConnectionsViewModel(connectionApiClient, activeTeamId) }
        val releaseListViewModel = remember { ReleaseListViewModel(releaseApiClient, projectApiClient, activeTeamId) }
        val user by authViewModel.user.collectAsState()
        val isCheckingSession by authViewModel.isCheckingSession.collectAsState()

        LaunchedEffect(Unit) { authViewModel.checkSession() }

        val navController = remember { NavigationController() }
        val currentScreen = navController.currentScreen

        MaterialTheme {
            Surface {
                when {
                    isCheckingSession -> {}
                    user == null -> LoginScreen(viewModel = authViewModel)
                    else -> AppNavigation(
                        currentScreen = currentScreen,
                        onNavigate = { navController.navigate(it) },
                        onGoBack = { navController.goBack() },
                        projectListViewModel = projectListViewModel,
                        projectApiClient = projectApiClient,
                        releaseApiClient = releaseApiClient,
                        releaseListViewModel = releaseListViewModel,
                        connectionsViewModel = connectionsViewModel,
                        connectionApiClient = connectionApiClient,
                        teamApiClient = teamApiClient,
                        activeTeamId = activeTeamId,
                        userTeams = emptyList(),
                        onLogout = { authViewModel.logout(); navController.resetTo(Screen.ProjectList) },
                        onTeamChanged = {},
                        onRefreshUser = {},
                    )
                }
            }
        }
    }

    @Test
    fun `auto-login shows project list`() = runComposeUiTest {
        setContent { TestApp(appClient()) }

        waitUntil(timeoutMillis = 5000L) { onAllNodesWithTag("project_list_screen").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("project_list_screen").assertExists()
        waitUntil(timeoutMillis = 5000L) { onAllNodesWithText("Pipeline A", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun `navigate to connections and back`() = runComposeUiTest {
        setContent { TestApp(appClient()) }

        waitUntil(timeoutMillis = 5000L) { onAllNodesWithTag("project_list_screen").fetchSemanticsNodes().isNotEmpty() }

        onNodeWithTag("connections_button").performClick()
        waitUntil(timeoutMillis = 3000L) { onAllNodesWithTag("connection_list_screen").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("connection_list_screen").assertExists()

        onNodeWithText("Back").performClick()
        waitUntil(timeoutMillis = 3000L) { onAllNodesWithTag("project_list_screen").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("project_list_screen").assertExists()
    }

    @Test
    fun `logout returns to login screen`() = runComposeUiTest {
        setContent { TestApp(appClient()) }

        waitUntil(timeoutMillis = 5000L) { onAllNodesWithTag("project_list_screen").fetchSemanticsNodes().isNotEmpty() }

        onNodeWithTag("logout_button").performClick()
        waitUntil(timeoutMillis = 3000L) { onAllNodesWithTag("login_screen").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("login_screen").assertExists()
    }

    @Test
    fun `navigate to releases and back`() = runComposeUiTest {
        setContent { TestApp(appClient()) }

        waitUntil(timeoutMillis = 5000L) { onAllNodesWithTag("project_list_screen").fetchSemanticsNodes().isNotEmpty() }

        onNodeWithTag("releases_button").performClick()
        waitUntil(timeoutMillis = 3000L) { onAllNodesWithTag("release_list_screen").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("release_list_screen").assertExists()

        onNodeWithText("Back").performClick()
        waitUntil(timeoutMillis = 3000L) { onAllNodesWithTag("project_list_screen").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("project_list_screen").assertExists()
    }

    @Test
    fun `navigate to editor and back`() = runComposeUiTest {
        setContent { TestApp(appClient()) }

        waitUntil(timeoutMillis = 5000L) { onAllNodesWithTag("project_list_screen").fetchSemanticsNodes().isNotEmpty() }

        // Click project to navigate to editor
        waitUntil(timeoutMillis = 5000L) { onAllNodesWithTag("project_item_p1").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("project_item_p1").performClick()
        waitUntil(timeoutMillis = 5000L) { onAllNodesWithTag("dag_editor_screen").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("dag_editor_screen").assertExists()
        waitUntil(timeoutMillis = 5000L) { onAllNodesWithText("Pipeline A", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }

        // Click Back to return
        onNodeWithText("Back").performClick()
        waitUntil(timeoutMillis = 3000L) { onAllNodesWithTag("project_list_screen").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("project_list_screen").assertExists()
    }

    @Test
    fun `navigate to connection form and back`() = runComposeUiTest {
        setContent { TestApp(appClient()) }

        waitUntil(timeoutMillis = 5000L) { onAllNodesWithTag("project_list_screen").fetchSemanticsNodes().isNotEmpty() }

        // Navigate to connections
        onNodeWithTag("connections_button").performClick()
        waitUntil(timeoutMillis = 3000L) { onAllNodesWithTag("connection_list_screen").fetchSemanticsNodes().isNotEmpty() }

        // Click FAB to go to form
        onNodeWithTag("create_connection_fab").performClick()
        waitUntil(timeoutMillis = 3000L) { onAllNodesWithTag("connection_form_screen").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("connection_form_screen").assertExists()

        // Back to connection list
        onNodeWithText("Back").performClick()
        waitUntil(timeoutMillis = 3000L) { onAllNodesWithTag("connection_list_screen").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("connection_list_screen").assertExists()
    }

    @Test
    fun `login flow navigates to project list`() = runComposeUiTest {
        val client = mockHttpClient(mapOf(
            "/auth/me" to json("Not authenticated", HttpStatusCode.Unauthorized),
            "/auth/login" to json("""{"username":"admin"}"""),
            "/projects" to json("""{"projects":[]}"""),
        ))

        setContent { TestApp(client) }

        waitUntil(timeoutMillis = 5000L) { onAllNodesWithTag("login_screen").fetchSemanticsNodes().isNotEmpty() }

        onNodeWithTag("login_username").performTextInput("admin")
        onNodeWithTag("login_password").performTextInput("admin")
        onNodeWithTag("login_button").performClick()

        waitUntil(timeoutMillis = 5000L) { onAllNodesWithTag("project_list_screen").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("project_list_screen").assertExists()
    }
}
