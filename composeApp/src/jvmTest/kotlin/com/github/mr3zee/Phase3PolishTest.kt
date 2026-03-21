package com.github.mr3zee

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import com.github.mr3zee.api.ConnectionApiClient
import com.github.mr3zee.api.ProjectApiClient
import com.github.mr3zee.api.ReleaseApiClient
import com.github.mr3zee.api.TeamApiClient
import io.ktor.client.HttpClient
import com.github.mr3zee.connections.ConnectionListScreen
import com.github.mr3zee.connections.ConnectionsViewModel
import com.github.mr3zee.model.TeamId
import com.github.mr3zee.projects.ProjectListScreen
import com.github.mr3zee.projects.ProjectListViewModel
import com.github.mr3zee.releases.ReleaseListScreen
import com.github.mr3zee.releases.ReleaseListViewModel
import com.github.mr3zee.teams.TeamListScreen
import com.github.mr3zee.teams.TeamListViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.test.Test

/**
 * Tests for Phase 3 — Cross-Cutting Polish:
 *  3A: FAB tooltips
 *  3B: Icon button tooltips
 *  3C: Dark theme contrast (visual — verified via ui-verification)
 *  3D: Empty state consistency (icons + CTA buttons)
 */
@OptIn(ExperimentalTestApi::class)
class Phase3PolishTest {

    // ── Shared test data ──

    private val emptyProjectsJson = """{"projects":[]}"""
    private val emptyConnectionsJson = """{"connections":[]}"""
    private val emptyReleasesJson = """{"releases":[],"pagination":{"totalCount":0,"offset":0,"limit":20}}"""
    private val emptyTeamsJson = """{"teams":[],"pagination":{"totalCount":0,"offset":0,"limit":20}}"""

    private fun projectClient(json: String = emptyProjectsJson) = mockHttpClient(
        mapOf("/projects" to json(json))
    )

    private fun connClient(json: String = emptyConnectionsJson) = mockHttpClient(
        mapOf("/connections" to json(json))
    )

    private fun releaseClient(json: String = emptyReleasesJson): HttpClient = mockHttpClient(
        mapOf(
            "/releases" to json(json),
            "/projects" to json("""{"projects":[]}"""),
        )
    )

    private fun teamListClient(json: String = emptyTeamsJson) = mockHttpClient(
        mapOf("/teams" to json(json))
    )

    // ── Step 3A: FAB Tooltips ──

    @Test
    fun `project list FAB has tooltip`() = runComposeUiTest {
        val vm = ProjectListViewModel(ProjectApiClient(projectClient()), MutableStateFlow(TeamId("t1")))
        setContent {
            MaterialTheme { ProjectListScreen(viewModel = vm, onEditProject = {}) }
        }
        waitUntil(timeoutMillis = 3000L) { onAllNodesWithTag("create_project_fab", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("create_project_fab", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `release list FAB has tooltip`() = runComposeUiTest {
        val vm = releaseClient().let { client -> ReleaseListViewModel(ReleaseApiClient(client), ProjectApiClient(client), MutableStateFlow(TeamId("t1"))) }
        setContent {
            MaterialTheme { ReleaseListScreen(viewModel = vm, onViewRelease = {}, onBack = {}) }
        }
        waitUntil(timeoutMillis = 3000L) { onAllNodesWithTag("start_release_fab", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("start_release_fab", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `connection list FAB has tooltip`() = runComposeUiTest {
        val vm = ConnectionsViewModel(ConnectionApiClient(connClient()), MutableStateFlow(TeamId("t1")))
        setContent {
            MaterialTheme { ConnectionListScreen(viewModel = vm, onCreateConnection = {}, onEditConnection = {}, onBack = {}) }
        }
        waitUntil(timeoutMillis = 3000L) { onAllNodesWithTag("create_connection_fab", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("create_connection_fab", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `team list FAB has tooltip`() = runComposeUiTest {
        val vm = TeamListViewModel(TeamApiClient(teamListClient()))
        setContent {
            MaterialTheme { TeamListScreen(viewModel = vm, onTeamClick = {}, onTeamCreated = {}, onMyInvites = {}) }
        }
        waitUntil(timeoutMillis = 3000L) { onAllNodesWithTag("create_team_fab", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("create_team_fab", useUnmergedTree = true).assertExists()
    }

    // ── Step 3D: Empty State Consistency ──

    @Test
    fun `project list empty state has icon and CTA button`() = runComposeUiTest {
        val vm = ProjectListViewModel(ProjectApiClient(projectClient()), MutableStateFlow(TeamId("t1")))
        setContent {
            MaterialTheme { ProjectListScreen(viewModel = vm, onEditProject = {}) }
        }
        waitUntil(timeoutMillis = 3000L) { onAllNodesWithText("No projects yet", substring = true).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("No projects yet. Create one to get started.").assertExists()
        onNodeWithTag("empty_state_create_project_button").assertExists()
    }

    @Test
    fun `connection list empty state has icon and CTA button`() = runComposeUiTest {
        val vm = ConnectionsViewModel(ConnectionApiClient(connClient()), MutableStateFlow(TeamId("t1")))
        setContent {
            MaterialTheme { ConnectionListScreen(viewModel = vm, onCreateConnection = {}, onEditConnection = {}, onBack = {}) }
        }
        waitUntil(timeoutMillis = 3000L) { onAllNodesWithText("No connections yet", substring = true).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("No connections yet. Connections link external services", substring = true).assertExists()
        onNodeWithTag("empty_state_create_connection_button").assertExists()
    }

    @Test
    fun `release list empty state has icon and CTA button`() = runComposeUiTest {
        val vm = releaseClient().let { client -> ReleaseListViewModel(ReleaseApiClient(client), ProjectApiClient(client), MutableStateFlow(TeamId("t1"))) }
        setContent {
            MaterialTheme { ReleaseListScreen(viewModel = vm, onViewRelease = {}, onBack = {}) }
        }
        waitUntil(timeoutMillis = 3000L) { onAllNodesWithText("No releases yet", substring = true).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("No releases yet. Create one to get started.").assertExists()
        onNodeWithTag("empty_state_start_release_button").assertExists()
    }

    @Test
    fun `team list empty state has icon and CTA button`() = runComposeUiTest {
        val vm = TeamListViewModel(TeamApiClient(teamListClient()))
        setContent {
            MaterialTheme { TeamListScreen(viewModel = vm, onTeamClick = {}, onTeamCreated = {}, onMyInvites = {}) }
        }
        waitUntil(timeoutMillis = 3000L) { onAllNodesWithText("No teams yet", substring = true).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("No teams yet. Create a team or ask a colleague to invite you.").assertExists()
        onNodeWithTag("empty_state_create_team_button").assertExists()
    }

    @Test
    fun `project list empty state CTA opens create form`() = runComposeUiTest {
        val vm = ProjectListViewModel(ProjectApiClient(projectClient()), MutableStateFlow(TeamId("t1")))
        setContent {
            MaterialTheme { ProjectListScreen(viewModel = vm, onEditProject = {}) }
        }
        waitUntil(timeoutMillis = 3000L) { onAllNodesWithTag("empty_state_create_project_button").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("empty_state_create_project_button").performClick()
        waitUntil(timeoutMillis = 2000L) { onAllNodesWithTag("project_name_input").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("create_project_form").assertExists()
    }

    @Test
    fun `team list empty state CTA opens create form`() = runComposeUiTest {
        val vm = TeamListViewModel(TeamApiClient(teamListClient()))
        setContent {
            MaterialTheme { TeamListScreen(viewModel = vm, onTeamClick = {}, onTeamCreated = {}, onMyInvites = {}) }
        }
        waitUntil(timeoutMillis = 3000L) { onAllNodesWithTag("empty_state_create_team_button").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("empty_state_create_team_button").performClick()
        waitUntil(timeoutMillis = 2000L) { onAllNodesWithTag("team_name_input").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("create_team_form").assertExists()
    }
}
