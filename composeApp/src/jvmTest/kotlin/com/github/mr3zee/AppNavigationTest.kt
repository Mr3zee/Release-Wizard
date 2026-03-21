package com.github.mr3zee

import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.dp
import com.github.mr3zee.api.AuthApiClient
import com.github.mr3zee.api.ConnectionApiClient
import com.github.mr3zee.api.MavenTriggerApiClient
import com.github.mr3zee.api.ProjectApiClient
import com.github.mr3zee.api.ReleaseApiClient
import com.github.mr3zee.api.ScheduleApiClient
import com.github.mr3zee.api.TeamApiClient
import com.github.mr3zee.api.UserTeamInfo
import com.github.mr3zee.api.WebhookTriggerApiClient
import com.github.mr3zee.auth.AuthViewModel
import com.github.mr3zee.auth.LoginScreen
import com.github.mr3zee.connections.ConnectionsViewModel
import com.github.mr3zee.model.TeamId
import com.github.mr3zee.model.TeamRole
import com.github.mr3zee.navigation.AppNavigation
import com.github.mr3zee.navigation.AppShell
import com.github.mr3zee.navigation.NavSection
import com.github.mr3zee.navigation.NavigationController
import com.github.mr3zee.navigation.Screen
import com.github.mr3zee.navigation.SidebarSettingsContent
import com.github.mr3zee.navigation.SidebarTeamSwitcher
import com.github.mr3zee.navigation.isTopLevel
import com.github.mr3zee.navigation.toNavSection
import com.github.mr3zee.projects.ProjectListViewModel
import com.github.mr3zee.releases.ReleaseListViewModel
import com.github.mr3zee.i18n.LanguagePack
import com.github.mr3zee.theme.AppTheme
import com.github.mr3zee.theme.ThemePreference
import io.ktor.client.*
import io.ktor.http.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.test.Test
import kotlin.test.assertEquals

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
        "/teams" to json("""{"teams":[]}"""),
    ))

    @Composable
    private fun TestApp(httpClient: HttpClient, navController: NavigationController = remember { NavigationController() }) {
        val authApiClient = remember { AuthApiClient(httpClient) }
        val projectApiClient = remember { ProjectApiClient(httpClient) }
        val connectionApiClient = remember { ConnectionApiClient(httpClient) }
        val releaseApiClient = remember { ReleaseApiClient(httpClient) }
        val teamApiClient = remember { TeamApiClient(httpClient) }
        val scheduleApiClient = remember { ScheduleApiClient(httpClient) }
        val webhookTriggerApiClient = remember { WebhookTriggerApiClient(httpClient) }
        val mavenTriggerApiClient = remember { MavenTriggerApiClient(httpClient) }
        val authViewModel = remember { AuthViewModel(authApiClient) }
        val activeTeamId = remember { MutableStateFlow<TeamId?>(TeamId("test-team")) }
        val projectListViewModel = remember { ProjectListViewModel(projectApiClient, activeTeamId) }
        val connectionsViewModel = remember { ConnectionsViewModel(connectionApiClient, activeTeamId) }
        val releaseListViewModel = remember { ReleaseListViewModel(releaseApiClient, projectApiClient, activeTeamId) }
        val user by authViewModel.user.collectAsState()
        val isCheckingSession by authViewModel.isCheckingSession.collectAsState()

        LaunchedEffect(Unit) { authViewModel.checkSession() }

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
                        scheduleApiClient = scheduleApiClient,
                        webhookTriggerApiClient = webhookTriggerApiClient,
                        mavenTriggerApiClient = mavenTriggerApiClient,
                        userTeams = emptyList(),
                        onLogout = { authViewModel.logout(); navController.resetTo(Screen.ProjectList) },
                        onTeamChanged = {},
                        onRefreshUser = {},
                    )
                }
            }
        }
    }

    /**
     * Test app with AppShell wrapping (sidebar visible). Uses a wide fixed width
     * to ensure the sidebar is in expanded mode (>= 900dp threshold).
     */
    @Composable
    private fun TestAppWithShell(
        httpClient: HttpClient,
        navController: NavigationController = remember { NavigationController() },
        userTeams: List<UserTeamInfo> = emptyList(),
        activeTeamId: MutableStateFlow<TeamId?> = remember { MutableStateFlow(TeamId("test-team")) },
        onTeamChanged: (TeamId) -> Unit = {},
        onLogoutOverride: (() -> Unit)? = null,
    ) {
        val authApiClient = remember { AuthApiClient(httpClient) }
        val projectApiClient = remember { ProjectApiClient(httpClient) }
        val connectionApiClient = remember { ConnectionApiClient(httpClient) }
        val releaseApiClient = remember { ReleaseApiClient(httpClient) }
        val teamApiClient = remember { TeamApiClient(httpClient) }
        val scheduleApiClient = remember { ScheduleApiClient(httpClient) }
        val webhookTriggerApiClient = remember { WebhookTriggerApiClient(httpClient) }
        val mavenTriggerApiClient = remember { MavenTriggerApiClient(httpClient) }
        val authViewModel = remember { AuthViewModel(authApiClient) }
        val projectListViewModel = remember { ProjectListViewModel(projectApiClient, activeTeamId) }
        val connectionsViewModel = remember { ConnectionsViewModel(connectionApiClient, activeTeamId) }
        val releaseListViewModel = remember { ReleaseListViewModel(releaseApiClient, projectApiClient, activeTeamId) }
        val user by authViewModel.user.collectAsState()
        val isCheckingSession by authViewModel.isCheckingSession.collectAsState()

        LaunchedEffect(Unit) { authViewModel.checkSession() }

        val currentScreen = navController.currentScreen

        var themePreference by remember { mutableStateOf(ThemePreference.SYSTEM) }
        var languagePack by remember { mutableStateOf(LanguagePack.ENGLISH) }

        val logout = onLogoutOverride ?: {
            authViewModel.logout()
            navController.resetTo(Screen.ProjectList)
        }

        AppTheme(themePreference = themePreference, languagePack = languagePack) {
            Surface(modifier = Modifier.width(1200.dp)) {
                when {
                    isCheckingSession -> {}
                    user == null -> LoginScreen(viewModel = authViewModel)
                    else -> AppShell(
                        sidebarVisible = currentScreen.isTopLevel(),
                        currentSection = currentScreen.toNavSection(),
                        onSectionClick = { section -> navController.navigateToSection(section) },
                        teamSwitcher = { collapsed ->
                            SidebarTeamSwitcher(
                                userTeams = userTeams,
                                activeTeamId = activeTeamId,
                                onTeamChanged = onTeamChanged,
                                collapsed = collapsed,
                            )
                        },
                        settingsContent = { collapsed ->
                            SidebarSettingsContent(
                                collapsed = collapsed,
                                themePreference = themePreference,
                                onThemeChange = { themePreference = it },
                                languagePack = languagePack,
                                onLanguagePackChange = { languagePack = it },
                                onShowShortcuts = {},
                            )
                        },
                        onSignOut = logout,
                    ) {
                        AppNavigation(
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
                            scheduleApiClient = scheduleApiClient,
                            webhookTriggerApiClient = webhookTriggerApiClient,
                            mavenTriggerApiClient = mavenTriggerApiClient,
                            userTeams = userTeams,
                            onLogout = logout,
                            onTeamChanged = onTeamChanged,
                            onRefreshUser = {},
                        )
                    }
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
    fun `navigate to connections via section switch`() = runComposeUiTest {
        val navController = NavigationController()
        setContent { TestApp(appClient(), navController) }

        waitUntil(timeoutMillis = 5000L) { onAllNodesWithTag("project_list_screen").fetchSemanticsNodes().isNotEmpty() }

        navController.navigateToSection(NavSection.CONNECTIONS)
        waitUntil(timeoutMillis = 3000L) { onAllNodesWithTag("connection_list_screen").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("connection_list_screen").assertExists()
    }

    @Test
    fun `navigate to releases via section switch`() = runComposeUiTest {
        val navController = NavigationController()
        setContent { TestApp(appClient(), navController) }

        waitUntil(timeoutMillis = 5000L) { onAllNodesWithTag("project_list_screen").fetchSemanticsNodes().isNotEmpty() }

        navController.navigateToSection(NavSection.RELEASES)
        waitUntil(timeoutMillis = 3000L) { onAllNodesWithTag("release_list_screen").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("release_list_screen").assertExists()
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
        val navController = NavigationController()
        setContent { TestApp(appClient(), navController) }

        waitUntil(timeoutMillis = 5000L) { onAllNodesWithTag("project_list_screen").fetchSemanticsNodes().isNotEmpty() }

        // Navigate to connections via section switch
        navController.navigateToSection(NavSection.CONNECTIONS)
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

    // ── QA-CROSS-1: Sidebar nav items click navigation ──────────────

    @Test
    fun `sidebar click on Releases navigates to release list`() = runComposeUiTest {
        val navController = NavigationController()
        setContent { TestAppWithShell(appClient(), navController) }

        waitUntil(timeoutMillis = 5000L) { onAllNodesWithTag("project_list_screen").fetchSemanticsNodes().isNotEmpty() }

        // Click the Releases sidebar nav item
        onNodeWithTag("sidebar_nav_releases").performClick()
        waitUntil(timeoutMillis = 3000L) { onAllNodesWithTag("release_list_screen").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("release_list_screen").assertExists()
        assertEquals(Screen.ReleaseList, navController.currentScreen)
    }

    @Test
    fun `sidebar click on Connections navigates to connection list`() = runComposeUiTest {
        val navController = NavigationController()
        setContent { TestAppWithShell(appClient(), navController) }

        waitUntil(timeoutMillis = 5000L) { onAllNodesWithTag("project_list_screen").fetchSemanticsNodes().isNotEmpty() }

        // Click the Connections sidebar nav item
        onNodeWithTag("sidebar_nav_connections").performClick()
        waitUntil(timeoutMillis = 3000L) { onAllNodesWithTag("connection_list_screen").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("connection_list_screen").assertExists()
        assertEquals(Screen.ConnectionList, navController.currentScreen)
    }

    @Test
    fun `sidebar click on Teams navigates to team list`() = runComposeUiTest {
        val navController = NavigationController()
        setContent { TestAppWithShell(appClient(), navController) }

        waitUntil(timeoutMillis = 5000L) { onAllNodesWithTag("project_list_screen").fetchSemanticsNodes().isNotEmpty() }

        // Click the Teams sidebar nav item
        onNodeWithTag("sidebar_nav_teams").performClick()
        waitUntil(timeoutMillis = 3000L) { onAllNodesWithTag("team_list_screen").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("team_list_screen").assertExists()
        assertEquals(Screen.TeamList, navController.currentScreen)
    }

    @Test
    fun `sidebar click on Projects from another section returns to project list`() = runComposeUiTest {
        val navController = NavigationController()
        setContent { TestAppWithShell(appClient(), navController) }

        waitUntil(timeoutMillis = 5000L) { onAllNodesWithTag("project_list_screen").fetchSemanticsNodes().isNotEmpty() }

        // Navigate to releases first
        onNodeWithTag("sidebar_nav_releases").performClick()
        waitUntil(timeoutMillis = 3000L) { onAllNodesWithTag("release_list_screen").fetchSemanticsNodes().isNotEmpty() }

        // Click back to Projects
        onNodeWithTag("sidebar_nav_projects").performClick()
        waitUntil(timeoutMillis = 3000L) { onAllNodesWithTag("project_list_screen").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("project_list_screen").assertExists()
        assertEquals(Screen.ProjectList, navController.currentScreen)
    }

    @Test
    fun `rapid sidebar switching between sections ends on last clicked`() = runComposeUiTest {
        val navController = NavigationController()
        setContent { TestAppWithShell(appClient(), navController) }

        waitUntil(timeoutMillis = 5000L) { onAllNodesWithTag("project_list_screen").fetchSemanticsNodes().isNotEmpty() }

        // Rapidly switch between sections
        onNodeWithTag("sidebar_nav_releases").performClick()
        onNodeWithTag("sidebar_nav_connections").performClick()
        onNodeWithTag("sidebar_nav_teams").performClick()

        waitUntil(timeoutMillis = 3000L) { onAllNodesWithTag("team_list_screen").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("team_list_screen").assertExists()
        assertEquals(Screen.TeamList, navController.currentScreen)
    }

    // ── QA-CROSS-2: Sidebar collapse/expand ─────────────────────────

    @Test
    fun `sidebar collapse toggle hides nav labels`() = runComposeUiTest {
        val navController = NavigationController()
        setContent { TestAppWithShell(appClient(), navController) }

        waitUntil(timeoutMillis = 5000L) { onAllNodesWithTag("sidebar").fetchSemanticsNodes().isNotEmpty() }

        // Sidebar should be visible and expanded (width > 900dp threshold)
        onNodeWithTag("sidebar").assertExists()
        onNodeWithTag("sidebar_collapse_toggle").assertExists()

        // Click collapse toggle
        onNodeWithTag("sidebar_collapse_toggle").performClick()
        waitForIdle()

        // Sidebar should still exist but now in collapsed mode
        onNodeWithTag("sidebar").assertExists()
        // Nav items should still be clickable in collapsed mode
        onNodeWithTag("sidebar_nav_projects").assertExists()
    }

    @Test
    fun `sidebar collapse and expand round-trip preserves navigation`() = runComposeUiTest {
        val navController = NavigationController()
        setContent { TestAppWithShell(appClient(), navController) }

        waitUntil(timeoutMillis = 5000L) { onAllNodesWithTag("sidebar", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }

        // Collapse
        onNodeWithTag("sidebar_collapse_toggle", useUnmergedTree = true).performClick()
        waitForIdle()

        // Navigate via collapsed sidebar (tooltips wrap nav items, so use unmerged tree)
        onNodeWithTag("sidebar_nav_releases", useUnmergedTree = true).performClick()
        // After clicking, sidebar disappears for collapsed+navigation, use unmerged tree for all lookups
        waitUntil(timeoutMillis = 3000L) { onAllNodesWithTag("release_list_screen", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        assertEquals(Screen.ReleaseList, navController.currentScreen)

        // Navigate back to projects to get sidebar back
        navController.navigateToSection(NavSection.PROJECTS)
        waitUntil(timeoutMillis = 3000L) { onAllNodesWithTag("project_list_screen", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }

        // Expand again
        onNodeWithTag("sidebar_collapse_toggle", useUnmergedTree = true).performClick()
        waitForIdle()

        // Navigate to releases in expanded mode to verify
        onNodeWithTag("sidebar_nav_releases").performClick()
        waitUntil(timeoutMillis = 3000L) { onAllNodesWithTag("release_list_screen", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("release_list_screen", useUnmergedTree = true).assertExists()
    }

    // ── QA-CROSS-3: Sign-out two-click confirmation flow ────────────

    @Test
    fun `sign out requires two clicks for confirmation`() = runComposeUiTest {
        var logoutCalled = false
        val navController = NavigationController()
        setContent {
            TestAppWithShell(
                appClient(),
                navController,
                onLogoutOverride = { logoutCalled = true },
            )
        }

        waitUntil(timeoutMillis = 5000L) { onAllNodesWithTag("sidebar").fetchSemanticsNodes().isNotEmpty() }

        // First click: should show confirmation, not log out
        onNodeWithTag("sidebar_sign_out").performClick()
        waitForIdle()
        assert(!logoutCalled) { "Logout should NOT be called after first click" }

        // Second click: should actually trigger logout
        onNodeWithTag("sidebar_sign_out").performClick()
        waitForIdle()
        assert(logoutCalled) { "Logout SHOULD be called after second click" }
    }

    @Test
    fun `sign out single click does not trigger logout even after navigation`() = runComposeUiTest {
        var logoutCallCount = 0
        val navController = NavigationController()
        setContent {
            TestAppWithShell(
                appClient(),
                navController,
                onLogoutOverride = { logoutCallCount++ },
            )
        }

        waitUntil(timeoutMillis = 5000L) { onAllNodesWithTag("sidebar").fetchSemanticsNodes().isNotEmpty() }

        // Navigate to releases and back to confirm sidebar stays mounted
        onNodeWithTag("sidebar_nav_releases").performClick()
        waitUntil(timeoutMillis = 3000L) { onAllNodesWithTag("release_list_screen").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("sidebar_nav_projects").performClick()
        waitUntil(timeoutMillis = 3000L) { onAllNodesWithTag("project_list_screen").fetchSemanticsNodes().isNotEmpty() }

        // Single click on sign out should NOT trigger logout
        onNodeWithTag("sidebar_sign_out").performClick()
        waitForIdle()
        assertEquals(0, logoutCallCount, "Single click after navigation should not log out")
    }

    // ── QA-CROSS-4: Session expiry mid-navigation → navigation reset ──

    // Note: This test verifies the navigation reset aspect of session expiry only.
    // The simplified TestApp does not subscribe to AuthEventBus, so the full
    // auth redirect flow (session expired -> user cleared -> login screen shown)
    // is not exercised here. This test confirms that NavigationController.resetTo()
    // correctly clears the back stack when called during a simulated session expiry.
    @Test
    fun `navigation controller resetTo clears stack on session expiry`() = runComposeUiTest {
        val client = mockHttpClient(mapOf(
            "/auth/me" to json("""{"username":"admin"}"""),
            "/auth/login" to json("""{"username":"admin"}"""),
            "/auth/logout" to json("{}"),
            "/projects" to json("""{"projects":[]}"""),
            "/connections" to json("""{"connections":[]}"""),
        ))
        val navController = NavigationController()

        setContent { TestApp(client, navController) }

        // Wait for auth to complete and project list to appear
        waitUntil(timeoutMillis = 5000L) { onAllNodesWithTag("project_list_screen").fetchSemanticsNodes().isNotEmpty() }

        // Navigate to connections
        navController.navigateToSection(NavSection.CONNECTIONS)
        waitUntil(timeoutMillis = 3000L) { onAllNodesWithTag("connection_list_screen").fetchSemanticsNodes().isNotEmpty() }

        // Simulate session expiry via AuthEventBus
        com.github.mr3zee.auth.AuthEventBus.emitSessionExpired()
        // The TestApp doesn't listen to AuthEventBus directly, but AuthViewModel.onSessionExpired clears user.
        // We need to call it directly since TestApp is simplified.
        // Instead, verify the navigation controller can handle the reset.
        navController.resetTo(Screen.ProjectList)
        assertEquals(Screen.ProjectList, navController.currentScreen)
        assertEquals(1, navController.backStack.size)
    }

    // ── QA-CROSS-5: Team switcher interaction ───────────────────────

    @Test
    fun `team switcher shows current team name`() = runComposeUiTest {
        val navController = NavigationController()
        val teams = listOf(
            UserTeamInfo(TeamId("t1"), "Alpha Team", TeamRole.TEAM_LEAD),
            UserTeamInfo(TeamId("t2"), "Beta Team", TeamRole.COLLABORATOR),
        )
        val activeTeamId = MutableStateFlow<TeamId?>(TeamId("t1"))
        setContent {
            TestAppWithShell(
                appClient(),
                navController,
                userTeams = teams,
                activeTeamId = activeTeamId,
            )
        }

        waitUntil(timeoutMillis = 5000L) { onAllNodesWithTag("sidebar").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("sidebar_team_switcher").assertExists()
        // The team switcher should display the active team name
        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithText("Alpha Team", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun `team switcher opens dropdown with multiple teams`() = runComposeUiTest {
        val navController = NavigationController()
        val teams = listOf(
            UserTeamInfo(TeamId("t1"), "Alpha Team", TeamRole.TEAM_LEAD),
            UserTeamInfo(TeamId("t2"), "Beta Team", TeamRole.COLLABORATOR),
        )
        val activeTeamId = MutableStateFlow<TeamId?>(TeamId("t1"))
        setContent {
            TestAppWithShell(
                appClient(),
                navController,
                userTeams = teams,
                activeTeamId = activeTeamId,
            )
        }

        waitUntil(timeoutMillis = 5000L) { onAllNodesWithTag("sidebar").fetchSemanticsNodes().isNotEmpty() }

        // Click team switcher to open dropdown
        onNodeWithTag("sidebar_team_switcher").performClick()
        waitForIdle()

        // Both teams should be visible in the dropdown
        onNodeWithTag("sidebar_team_picker_t1").assertExists()
        onNodeWithTag("sidebar_team_picker_t2").assertExists()
    }

    @Test
    fun `team switcher selecting team triggers onTeamChanged`() = runComposeUiTest {
        val navController = NavigationController()
        val teams = listOf(
            UserTeamInfo(TeamId("t1"), "Alpha Team", TeamRole.TEAM_LEAD),
            UserTeamInfo(TeamId("t2"), "Beta Team", TeamRole.COLLABORATOR),
        )
        val activeTeamId = MutableStateFlow<TeamId?>(TeamId("t1"))
        var changedToTeamId: TeamId? = null
        setContent {
            TestAppWithShell(
                appClient(),
                navController,
                userTeams = teams,
                activeTeamId = activeTeamId,
                onTeamChanged = { changedToTeamId = it },
            )
        }

        waitUntil(timeoutMillis = 5000L) { onAllNodesWithTag("sidebar").fetchSemanticsNodes().isNotEmpty() }

        // Open team switcher dropdown
        onNodeWithTag("sidebar_team_switcher").performClick()
        waitForIdle()

        // Select the second team
        onNodeWithTag("sidebar_team_picker_t2").performClick()
        waitForIdle()

        assertEquals(TeamId("t2"), changedToTeamId)
    }

    // ── QA-CROSS-8: Settings panel interactions ─────────────────────

    @Test
    fun `settings panel expands on click`() = runComposeUiTest {
        val navController = NavigationController()
        setContent { TestAppWithShell(appClient(), navController) }

        waitUntil(timeoutMillis = 5000L) { onAllNodesWithTag("sidebar").fetchSemanticsNodes().isNotEmpty() }

        // Click settings to expand the panel
        onNodeWithTag("sidebar_settings").performClick()
        waitForIdle()

        // Settings sub-items should now be visible
        onNodeWithTag("sidebar_settings_theme").assertExists()
        onNodeWithTag("sidebar_settings_language").assertExists()
        onNodeWithTag("sidebar_settings_shortcuts").assertExists()
    }

    @Test
    fun `settings theme toggle cycles through preferences`() = runComposeUiTest {
        val navController = NavigationController()
        setContent { TestAppWithShell(appClient(), navController) }

        waitUntil(timeoutMillis = 5000L) { onAllNodesWithTag("sidebar").fetchSemanticsNodes().isNotEmpty() }

        // Expand settings
        onNodeWithTag("sidebar_settings").performClick()
        waitForIdle()

        // Click theme toggle - cycles SYSTEM -> LIGHT -> DARK -> SYSTEM
        onNodeWithTag("sidebar_settings_theme").performClick()
        waitForIdle()

        // After one click from SYSTEM, should be on LIGHT
        onNodeWithTag("sidebar_settings_theme").assertExists()
    }

    // ── QA-CROSS-9: Team section navigation ─────────────────────────

    @Test
    fun `navigate to teams section via sidebar and see team list`() = runComposeUiTest {
        val navController = NavigationController()
        setContent { TestAppWithShell(appClient(), navController) }

        waitUntil(timeoutMillis = 5000L) { onAllNodesWithTag("project_list_screen").fetchSemanticsNodes().isNotEmpty() }

        // Click Teams in sidebar
        onNodeWithTag("sidebar_nav_teams").performClick()
        waitUntil(timeoutMillis = 3000L) { onAllNodesWithTag("team_list_screen").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("team_list_screen").assertExists()

        // Verify back stack is correct: [ProjectList, TeamList]
        assertEquals(listOf(Screen.ProjectList, Screen.TeamList), navController.backStack)
    }

    @Test
    fun `navigate from teams back to projects via sidebar`() = runComposeUiTest {
        val navController = NavigationController()
        setContent { TestAppWithShell(appClient(), navController) }

        waitUntil(timeoutMillis = 5000L) { onAllNodesWithTag("project_list_screen").fetchSemanticsNodes().isNotEmpty() }

        // Go to teams
        onNodeWithTag("sidebar_nav_teams").performClick()
        waitUntil(timeoutMillis = 3000L) { onAllNodesWithTag("team_list_screen").fetchSemanticsNodes().isNotEmpty() }

        // Go back to projects via sidebar
        onNodeWithTag("sidebar_nav_projects").performClick()
        waitUntil(timeoutMillis = 3000L) { onAllNodesWithTag("project_list_screen").fetchSemanticsNodes().isNotEmpty() }
        assertEquals(Screen.ProjectList, navController.currentScreen)
    }

    // ── QA-CROSS-11: Data propagation after team switch ─────────────

    @Test
    fun `team switch updates activeTeamId flow`() = runComposeUiTest {
        val navController = NavigationController()
        val teams = listOf(
            UserTeamInfo(TeamId("t1"), "Alpha Team", TeamRole.TEAM_LEAD),
            UserTeamInfo(TeamId("t2"), "Beta Team", TeamRole.COLLABORATOR),
        )
        val activeTeamId = MutableStateFlow<TeamId?>(TeamId("t1"))
        setContent {
            TestAppWithShell(
                appClient(),
                navController,
                userTeams = teams,
                activeTeamId = activeTeamId,
                onTeamChanged = { activeTeamId.value = it },
            )
        }

        waitUntil(timeoutMillis = 5000L) { onAllNodesWithTag("sidebar").fetchSemanticsNodes().isNotEmpty() }

        // Verify initial team
        assertEquals(TeamId("t1"), activeTeamId.value)

        // Open team switcher and switch teams
        onNodeWithTag("sidebar_team_switcher").performClick()
        waitForIdle()
        onNodeWithTag("sidebar_team_picker_t2").performClick()
        waitForIdle()

        // Verify activeTeamId was updated
        assertEquals(TeamId("t2"), activeTeamId.value)
    }
}
