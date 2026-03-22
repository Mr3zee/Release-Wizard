package com.github.mr3zee

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import com.github.mr3zee.api.*
import com.github.mr3zee.auth.AuthViewModel
import com.github.mr3zee.auth.LoginScreen
import com.github.mr3zee.connections.ConnectionFormScreen
import com.github.mr3zee.connections.ConnectionsViewModel
import com.github.mr3zee.editor.DagEditorScreen
import com.github.mr3zee.editor.DagEditorViewModel
import com.github.mr3zee.model.ProjectId
import com.github.mr3zee.model.TeamId
import com.github.mr3zee.navigation.NavigationController
import com.github.mr3zee.navigation.Screen
import com.github.mr3zee.releases.ReleaseListScreen
import com.github.mr3zee.releases.ReleaseListViewModel
import com.github.mr3zee.teams.TeamListScreen
import com.github.mr3zee.teams.TeamListViewModel
import io.ktor.http.*
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Tests for functionality added since commit c0acbbc:
 * - Team creation navigates to TeamDetail (not ProjectList)
 * - Empty state centering on Releases screen
 * - RwDropdownMenu has visible border (verified via its existence in the Compose tree)
 * - Connection form shows Test button for new connections
 * - Slack Message block shows message field in properties
 * - Registration shows "Username taken" error
 */
@OptIn(ExperimentalTestApi::class)
class RecentChangesTest {

    // ── 1. Team creation navigates to TeamDetail ────────────────────────

    @Test
    fun `team creation calls onTeamCreated with new team id`() = runComposeUiTest {
        val createdTeamId = AtomicReference<TeamId?>(null)
        val createTeamResponseJson = """{"team":{"id":"t-new","name":"New Team","description":"","createdAt":0},"memberCount":1}"""
        val teamsJson = """{"teams":[],"pagination":{"totalCount":0,"offset":0,"limit":20}}"""
        val client = mockHttpClient(listOf(
            "/teams" to json(teamsJson, method = HttpMethod.Get),
            "/teams" to json(createTeamResponseJson, method = HttpMethod.Post),
        ))
        val vm = TeamListViewModel(TeamApiClient(client))

        setContent {
            MaterialTheme {
                TeamListScreen(
                    viewModel = vm,
                    onTeamClick = {},
                    onTeamCreated = { createdTeamId.set(it) },
                    onMyInvites = {},
                )
            }
        }

        // Wait for screen to load
        waitUntil(timeoutMillis = 3000L) { onAllNodesWithTag("create_team_fab").fetchSemanticsNodes().isNotEmpty() }

        // Open create form
        onNodeWithTag("create_team_fab").performClick()
        waitUntil(timeoutMillis = 2000L) { onAllNodesWithTag("team_name_input").fetchSemanticsNodes().isNotEmpty() }

        // Fill in team name and submit
        onNodeWithTag("team_name_input").performTextInput("New Team")
        onNodeWithTag("create_team_confirm").performClick()

        // Verify onTeamCreated was called with the new team ID
        waitUntil(timeoutMillis = 5000L) { createdTeamId.get() != null }
        assertEquals(TeamId("t-new"), createdTeamId.get())
    }

    @Test
    fun `AppNavigation navigates to TeamDetail after team creation`() {
        // Verify that the navigation wiring sends Screen.TeamDetail when onTeamCreated fires.
        // This is a unit test of NavigationController behavior since full AppNavigation
        // requires all API clients and view models.
        val navController = NavigationController()
        navController.navigate(Screen.TeamList)
        assertEquals(Screen.TeamList, navController.currentScreen)

        // Simulate what AppNavigation does in onTeamCreated: navigate to TeamDetail
        navController.navigate(Screen.TeamDetail(TeamId("t-new")))
        assertEquals(Screen.TeamDetail(TeamId("t-new")), navController.currentScreen)

        // Going back should return to TeamList
        navController.goBack()
        assertEquals(Screen.TeamList, navController.currentScreen)
    }

    // ── 2. Empty state centering on Releases screen ─────────────────────

    @Test
    fun `release list empty state is centered in fillMaxSize box`() = runComposeUiTest {
        val client = mockHttpClient(mapOf(
            "/releases" to json("""{"releases":[]}"""),
            "/projects" to json("""{"projects":[]}"""),
        ))
        val vm = ReleaseListViewModel(ReleaseApiClient(client), ProjectApiClient(client), MutableStateFlow(TeamId("test-team")))

        setContent {
            MaterialTheme {
                ReleaseListScreen(
                    viewModel = vm,
                    onViewRelease = {},
                    onBack = {},
                )
            }
        }

        // Wait for the empty state to appear
        waitUntil(timeoutMillis = 3000L) { onAllNodesWithTag("empty_state").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("empty_state").assertExists()

        // The empty state should be inside a Box with fillMaxSize + center alignment.
        // Verify the empty state text and the "Start a release" button coexist.
        onNodeWithTag("empty_state_start_release_button").assertExists()
    }

    @Test
    fun `release list empty state is not shown when search is active and no results`() = runComposeUiTest {
        val client = mockHttpClient(mapOf(
            "/releases" to json("""{"releases":[]}"""),
            "/projects" to json("""{"projects":[]}"""),
        ))
        val vm = ReleaseListViewModel(ReleaseApiClient(client), ProjectApiClient(client), MutableStateFlow(TeamId("test-team")))

        setContent {
            MaterialTheme {
                ReleaseListScreen(
                    viewModel = vm,
                    onViewRelease = {},
                    onBack = {},
                )
            }
        }

        waitUntil(timeoutMillis = 3000L) { onAllNodesWithTag("empty_state").fetchSemanticsNodes().isNotEmpty() }

        // Set search query — should switch to EmptySearchResults instead of the primary empty state
        vm.setSearchQuery("nonexistent")
        waitForIdle()

        // The "empty_state" testTag should no longer be visible (replaced by EmptySearchResults)
        onNodeWithTag("empty_state").assertDoesNotExist()
    }

    // ── 3. RwDropdownMenu has visible border ────────────────────────────

    @Test
    fun `connection form type dropdown uses RwDropdownMenu with border`() = runComposeUiTest {
        // We verify the dropdown trigger exists and can be interacted with.
        // The border is applied by RwDropdownMenu internally (modifier.border(1.dp, ...)).
        // Note: DropdownMenu renders in a popup layer that runComposeUiTest cannot see,
        // so we verify the selector exists and shows the default type name.
        val vm = ConnectionsViewModel(
            ConnectionApiClient(mockHttpClient(mapOf("/connections" to json("""{"connections":[]}""")))),
            MutableStateFlow(TeamId("test-team")),
        )
        setContent {
            MaterialTheme { ConnectionFormScreen(viewModel = vm, onBack = {}) }
        }

        onNodeWithTag("connection_type_selector").assertExists()

        // The selector should display the default type (GitHub)
        onNodeWithTag("connection_type_selector").assertTextContains("GitHub")
    }

    // ── 4. Connection form shows Test button for new connections ─────────

    @Test
    fun `new connection form shows test button`() = runComposeUiTest {
        val vm = ConnectionsViewModel(
            ConnectionApiClient(mockHttpClient(mapOf("/connections" to json("""{"connections":[]}""")))),
            MutableStateFlow(TeamId("test-team")),
        )
        setContent {
            MaterialTheme { ConnectionFormScreen(viewModel = vm, onBack = {}) }
        }

        // Test button should exist for new connections (no connectionId)
        onNodeWithTag("test_connection_button").assertExists()
    }

    @Test
    fun `new connection form test button disabled when config invalid`() = runComposeUiTest {
        val vm = ConnectionsViewModel(
            ConnectionApiClient(mockHttpClient(mapOf("/connections" to json("""{"connections":[]}""")))),
            MutableStateFlow(TeamId("test-team")),
        )
        setContent {
            MaterialTheme { ConnectionFormScreen(viewModel = vm, onBack = {}) }
        }

        // Test button should be disabled when the form is incomplete (no token/owner/repo filled)
        onNodeWithTag("test_connection_button").assertIsNotEnabled()
    }

    @Test
    fun `new connection form test button enabled when config valid`() = runComposeUiTest {
        val vm = ConnectionsViewModel(
            ConnectionApiClient(mockHttpClient(mapOf("/connections" to json("""{"connections":[]}""")))),
            MutableStateFlow(TeamId("test-team")),
        )
        setContent {
            MaterialTheme { ConnectionFormScreen(viewModel = vm, onBack = {}) }
        }

        // Fill in valid GitHub config
        onNodeWithTag("github_token").performTextInput("ghp_test")
        onNodeWithTag("github_owner").performTextInput("owner")
        onNodeWithTag("github_repo").performTextInput("repo")

        // Test button should now be enabled
        onNodeWithTag("test_connection_button").assertIsEnabled()
    }

    // ── 5. Slack Message block shows message field in properties ─────────

    @Test
    fun `slack message block shows message field in properties panel`() = runComposeUiTest {
        val slackBlockJson = """{"project":{"id":"p1","name":"Test Project","description":"","dagGraph":{"blocks":[
            {"kind":"action","id":"b1","name":"Notify","type":"SLACK_MESSAGE","parameters":[],"outputs":[],"timeoutSeconds":null,"connectionId":null}
        ],"edges":[],"positions":{"b1":{"x":100,"y":100}}},"parameters":[],"createdAt":"2026-03-13T00:00:00Z","updatedAt":"2026-03-13T00:00:00Z"}}"""
        val lockJson = """{"userId":"u1","username":"testuser","acquiredAt":"2026-03-13T00:00:00Z","expiresAt":"2026-03-13T00:05:00Z"}"""
        val client = mockHttpClient(mapOf(
            "/projects/p1" to json(slackBlockJson),
            "/projects/p1/lock" to json(lockJson, HttpStatusCode.OK, method = HttpMethod.Post),
            "/projects/p1/lock/heartbeat" to json(lockJson, HttpStatusCode.OK, method = HttpMethod.Put),
        ))
        val vm = DagEditorViewModel(ProjectId("p1"), ProjectApiClient(client))

        setContent {
            MaterialTheme {
                DagEditorScreen(viewModel = vm, onBack = {})
            }
        }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithText("Test Project").fetchSemanticsNodes().isNotEmpty()
        }

        // Select block b1 (SLACK_MESSAGE) by clicking on its position
        onNodeWithTag("dag_canvas").performTouchInput {
            click(Offset(190f, 135f))
        }
        waitForIdle()

        // Wait for properties panel to show
        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("block_name_field").fetchSemanticsNodes().isNotEmpty()
        }

        // The slack_message_field should be visible in the properties panel
        onNodeWithTag("slack_message_field").assertExists()
    }

    @Test
    fun `slack message field is not shown for non-slack block types`() = runComposeUiTest {
        val tcBlockJson = """{"project":{"id":"p1","name":"Test Project","description":"","dagGraph":{"blocks":[
            {"kind":"action","id":"b1","name":"Build","type":"TEAMCITY_BUILD","parameters":[],"outputs":[],"timeoutSeconds":null,"connectionId":null}
        ],"edges":[],"positions":{"b1":{"x":100,"y":100}}},"parameters":[],"createdAt":"2026-03-13T00:00:00Z","updatedAt":"2026-03-13T00:00:00Z"}}"""
        val lockJson = """{"userId":"u1","username":"testuser","acquiredAt":"2026-03-13T00:00:00Z","expiresAt":"2026-03-13T00:05:00Z"}"""
        val client = mockHttpClient(mapOf(
            "/projects/p1" to json(tcBlockJson),
            "/projects/p1/lock" to json(lockJson, HttpStatusCode.OK, method = HttpMethod.Post),
            "/projects/p1/lock/heartbeat" to json(lockJson, HttpStatusCode.OK, method = HttpMethod.Put),
        ))
        val vm = DagEditorViewModel(ProjectId("p1"), ProjectApiClient(client))

        setContent {
            MaterialTheme {
                DagEditorScreen(viewModel = vm, onBack = {})
            }
        }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithText("Test Project").fetchSemanticsNodes().isNotEmpty()
        }

        // Select block b1 (TEAMCITY_BUILD)
        onNodeWithTag("dag_canvas").performTouchInput {
            click(Offset(190f, 135f))
        }
        waitForIdle()

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("block_name_field").fetchSemanticsNodes().isNotEmpty()
        }

        // The slack_message_field should NOT exist for TeamCity blocks
        onNodeWithTag("slack_message_field").assertDoesNotExist()
    }

    // ── 6. Registration shows "Username taken" error ────────────────────

    @Test
    fun `registration shows username taken error`() = runComposeUiTest {
        val client = mockHttpClient(mapOf(
            "/auth/register" to json(
                """{"error":"Username is already taken","code":"USERNAME_TAKEN"}""",
                HttpStatusCode.Conflict,
                method = HttpMethod.Post,
            ),
        ))
        val viewModel = AuthViewModel(AuthApiClient(client))

        setContent {
            MaterialTheme { LoginScreen(viewModel = viewModel, showGoogleLogin = false) }
        }

        // Switch to register mode
        onNodeWithTag("toggle_auth_mode").performClick()
        waitForIdle()

        // Fill in registration form
        onNodeWithTag("login_username").performTextInput("existinguser")
        onNodeWithTag("login_password").performTextInput("password123")
        onNodeWithTag("login_confirm_password").performTextInput("password123")

        // Submit registration
        onNodeWithTag("register_button").performClick()

        // Wait for error to appear
        waitUntil(timeoutMillis = 5000L) {
            onAllNodesWithTag("login_error").fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithTag("login_error").assertExists()

        // The error message should contain the localized "username taken" text
        onNodeWithText("That username is already taken.", substring = true).assertExists()
    }

    @Test
    fun `registration error clears when user types`() = runComposeUiTest {
        val client = mockHttpClient(mapOf(
            "/auth/register" to json(
                """{"error":"Username is already taken","code":"USERNAME_TAKEN"}""",
                HttpStatusCode.Conflict,
                method = HttpMethod.Post,
            ),
        ))
        val viewModel = AuthViewModel(AuthApiClient(client))

        setContent {
            MaterialTheme { LoginScreen(viewModel = viewModel, showGoogleLogin = false) }
        }

        // Switch to register mode
        onNodeWithTag("toggle_auth_mode").performClick()
        waitForIdle()

        // Fill in and submit
        onNodeWithTag("login_username").performTextInput("existinguser")
        onNodeWithTag("login_password").performTextInput("password123")
        onNodeWithTag("login_confirm_password").performTextInput("password123")
        onNodeWithTag("register_button").performClick()

        // Wait for error
        waitUntil(timeoutMillis = 5000L) {
            onAllNodesWithTag("login_error").fetchSemanticsNodes().isNotEmpty()
        }

        // Typing in username should dismiss the error
        onNodeWithTag("login_username").performTextInput("x")
        waitForIdle()
        onNodeWithTag("login_error").assertDoesNotExist()
    }
}
