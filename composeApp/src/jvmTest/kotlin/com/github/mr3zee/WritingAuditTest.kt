package com.github.mr3zee

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import com.github.mr3zee.api.*
import com.github.mr3zee.auth.AuthViewModel
import com.github.mr3zee.auth.LoginScreen
import com.github.mr3zee.automation.ProjectAutomationScreen
import com.github.mr3zee.automation.ProjectAutomationViewModel
import com.github.mr3zee.connections.ConnectionFormScreen
import com.github.mr3zee.connections.ConnectionListScreen
import com.github.mr3zee.connections.ConnectionsViewModel
import com.github.mr3zee.model.*
import com.github.mr3zee.projects.ProjectListScreen
import com.github.mr3zee.projects.ProjectListViewModel
import com.github.mr3zee.teams.*
import com.github.mr3zee.util.typeLabel
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.cookies.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.test.Test

/**
 * Tests verifying UX copy & tech writing audit changes.
 *
 * Each test covers a specific string/label change from the writing audit,
 * ensuring the updated copy appears correctly in the rendered UI.
 */
@OptIn(ExperimentalTestApi::class)
class WritingAuditTest {

    // ══════════════════════════════════════════════════════════════
    // 1. Password policy text updated
    // ══════════════════════════════════════════════════════════════

    @Test
    fun `register mode shows updated password policy text`() = runComposeUiTest {
        val client = mockHttpClient(
            mapOf("/auth/register" to json("""{"username":"newuser"}"""))
        )
        val viewModel = AuthViewModel(AuthApiClient(client))
        setContent { MaterialTheme { LoginScreen(viewModel = viewModel) } }

        // Switch to register mode
        onNodeWithTag("toggle_auth_mode").performClick()
        waitForIdle()

        // Verify updated password policy text
        onNodeWithText("At least 12 characters, including an uppercase letter and a number")
            .assertExists()
    }

    @Test
    fun `login mode does not show password policy text`() = runComposeUiTest {
        val client = mockHttpClient(
            mapOf("/auth/login" to json("""{"username":"admin"}"""))
        )
        val viewModel = AuthViewModel(AuthApiClient(client))
        setContent { MaterialTheme { LoginScreen(viewModel = viewModel) } }

        // In login mode, password policy should not be visible
        onNodeWithText("At least 12 characters", substring = true).assertDoesNotExist()
    }

    // ══════════════════════════════════════════════════════════════
    // 2. Error messages improved
    // ══════════════════════════════════════════════════════════════

    @Test
    fun `failed login shows improved error message`() = runComposeUiTest {
        val client = mockHttpClient(
            mapOf(
                "/auth/login" to json(
                    """{"error":"Invalid credentials","code":"INVALID_CREDENTIALS"}""",
                    HttpStatusCode.Unauthorized,
                ),
            )
        )
        val viewModel = AuthViewModel(AuthApiClient(client))
        setContent { MaterialTheme { LoginScreen(viewModel = viewModel) } }

        onNodeWithTag("login_username").performTextInput("admin")
        onNodeWithTag("login_password").performTextInput("wrong")
        onNodeWithTag("login_button").performClick()

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithText("Incorrect username or password", substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        // The full message should include guidance to check credentials
        onNodeWithText("Incorrect username or password", substring = true).assertExists()
    }

    // ══════════════════════════════════════════════════════════════
    // 4. Connection placeholders localized — TeamCity token placeholder
    // ══════════════════════════════════════════════════════════════

    @Test
    fun `connection form TeamCity type shows localized token placeholder`() = runComposeUiTest {
        val client = mockHttpClient(mapOf("/connections" to json("""{"connections":[]}""")))
        val vm = ConnectionsViewModel(ConnectionApiClient(client), MutableStateFlow(TeamId("test-team")))
        setContent {
            MaterialTheme { ConnectionFormScreen(viewModel = vm, onBack = {}) }
        }

        // Switch to TeamCity connection type
        onNodeWithTag("connection_type_selector").performClick()
        onNodeWithText("TeamCity").performClick()

        // TeamCity token field should have the localized placeholder
        onNodeWithText("Paste your TeamCity access token", useUnmergedTree = true).assertExists()
    }

    // ══════════════════════════════════════════════════════════════
    // 5. Sort labels updated — "Recently updated" for NEWEST
    // ══════════════════════════════════════════════════════════════

    private val sortableConnectionsJson = """{"connections":[
        {"id":"c1","name":"Zebra Conn","type":"GITHUB","config":{"type":"github","token":"t","owner":"o","repo":"r"},"createdAt":"2026-03-10T00:00:00Z","updatedAt":"2026-03-10T00:00:00Z"},
        {"id":"c2","name":"Alpha Conn","type":"SLACK","config":{"type":"slack","webhookUrl":"https://hooks.slack.com/test"},"createdAt":"2026-03-12T00:00:00Z","updatedAt":"2026-03-12T00:00:00Z"}
    ]}"""

    @Test
    fun `sort by NEWEST shows Recently updated label`() = runComposeUiTest {
        val client = mockHttpClient(mapOf("/connections" to json(sortableConnectionsJson)))
        val vm = ConnectionsViewModel(ConnectionApiClient(client), MutableStateFlow(TeamId("test-team")))
        setContent {
            MaterialTheme {
                ConnectionListScreen(
                    viewModel = vm,
                    onCreateConnection = {},
                    onEditConnection = {},
                    onBack = {},
                )
            }
        }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithText("Zebra Conn").fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithTag("sort_dropdown_button").performClick()
        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("sort_option_NEWEST").fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithTag("sort_option_NEWEST").performClick()
        waitForIdle()
        onNodeWithText("Recently updated", substring = true).assertExists()
    }

    // ══════════════════════════════════════════════════════════════
    // 6. Leave team label updated — shows "Leave team" (not "Leave")
    // ══════════════════════════════════════════════════════════════

    private val teamDetailJson = """{"team":{"id":"t1","name":"Alpha Team","description":"The first team","createdAt":0},"members":[
        {"teamId":"t1","userId":"u1","username":"alice","role":"TEAM_LEAD","joinedAt":0},
        {"teamId":"t1","userId":"u2","username":"bob","role":"COLLABORATOR","joinedAt":0}
    ]}"""

    @Test
    fun `team detail shows Leave team button label`() = runComposeUiTest {
        val client = mockHttpClient(mapOf("/teams/t1" to json(teamDetailJson)))
        val vm = TeamDetailViewModel(TeamId("t1"), TeamApiClient(client))
        setContent {
            MaterialTheme {
                TeamDetailScreen(viewModel = vm, onManage = {}, onBack = {})
            }
        }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithText("Alpha Team", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        // The button text should be "Leave team" (not bare "Leave")
        onNodeWithText("Leave team", useUnmergedTree = true).assertExists()
    }

    // ══════════════════════════════════════════════════════════════
    // 7. Confirmation messages have consequences
    // ══════════════════════════════════════════════════════════════

    @Test
    fun `team leave confirmation shows consequence text`() = runComposeUiTest {
        val client = mockHttpClient(mapOf("/teams/t1" to json(teamDetailJson)))
        val vm = TeamDetailViewModel(TeamId("t1"), TeamApiClient(client))
        setContent {
            MaterialTheme {
                TeamDetailScreen(viewModel = vm, onManage = {}, onBack = {})
            }
        }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithText("Alpha Team", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithTag("leave_team_button").performClick()
        waitUntil(timeoutMillis = 2000L) {
            onAllNodesWithTag("leave_team_confirm").fetchSemanticsNodes().isNotEmpty()
        }
        // Confirmation should include consequence language
        onNodeWithText("You will lose access", substring = true, useUnmergedTree = true)
            .assertExists()
    }

    // ══════════════════════════════════════════════════════════════
    // 8. Empty states improved — Team list empty state
    // ══════════════════════════════════════════════════════════════

    @Test
    fun `team list empty state shows improved copy`() = runComposeUiTest {
        val client = mockHttpClient(
            mapOf("/teams" to json("""{"teams":[],"pagination":{"totalCount":0,"offset":0,"limit":20}}"""))
        )
        val vm = TeamListViewModel(TeamApiClient(client))
        setContent {
            MaterialTheme {
                TeamListScreen(viewModel = vm, onTeamClick = {}, onTeamCreated = {}, onMyInvites = {})
            }
        }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithText("Create a team or ask a colleague", substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithText("Create a team or ask a colleague", substring = true).assertExists()
    }

    // ══════════════════════════════════════════════════════════════
    // 9. Invitation text fixed — "Decline invitation to" (not "Decline invite from")
    // ══════════════════════════════════════════════════════════════

    private val myInvitesJson = """{"invites":[
        {"id":"inv1","teamId":"t1","teamName":"Alpha Team","invitedUserId":"me","invitedUsername":"me","invitedByUserId":"u1","invitedByUsername":"alice","status":"PENDING","createdAt":0}
    ]}"""

    @Test
    fun `decline confirmation shows Decline invitation to with team name`() = runComposeUiTest {
        val client = mockHttpClient(mapOf("/auth/me/invites" to json(myInvitesJson)))
        val vm = MyInvitesViewModel(TeamApiClient(client))
        setContent {
            MaterialTheme {
                MyInvitesScreen(viewModel = vm, onBack = {})
            }
        }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithText("Alpha Team", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        // Click decline to open inline confirmation
        onNodeWithTag("decline_invite_inv1").performClick()
        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("decline_invite_confirm_inv1").fetchSemanticsNodes().isNotEmpty()
        }
        // The updated text should say "Decline invitation to" (not "Decline invite from")
        onNodeWithText("Decline invitation to", substring = true, useUnmergedTree = true)
            .assertExists()
        onAllNodesWithText("Alpha Team", substring = true, useUnmergedTree = true)
            .onFirst().assertExists()
    }

    // ══════════════════════════════════════════════════════════════
    // 10. Role actions renamed — "Set as Lead" / "Set as Collaborator"
    // ══════════════════════════════════════════════════════════════

    private fun teamManageClient() = mockHttpClient(mapOf(
        "/teams/t1" to json("""{"team":{"id":"t1","name":"Alpha Team","description":"First team","createdAt":0},"members":[
            {"teamId":"t1","userId":"u1","username":"alice","role":"TEAM_LEAD","joinedAt":0},
            {"teamId":"t1","userId":"u2","username":"bob","role":"COLLABORATOR","joinedAt":0}
        ],"memberCount":2,"inviteCount":0,"joinRequestCount":0}"""),
        "/teams/t1/members" to json("""{"members":[
            {"teamId":"t1","userId":"u1","username":"alice","role":"TEAM_LEAD","joinedAt":0},
            {"teamId":"t1","userId":"u2","username":"bob","role":"COLLABORATOR","joinedAt":0}
        ]}"""),
        "/teams/t1/invites" to json("""{"invites":[]}"""),
        "/teams/t1/join-requests" to json("""{"requests":[]}"""),
    ))

    @Test
    fun `team manage shows Set as Lead and Set as Collaborator labels`() = runComposeUiTest {
        val vm = TeamManageViewModel(TeamId("t1"), TeamApiClient(teamManageClient()))
        setContent {
            MaterialTheme {
                TeamManageScreen(viewModel = vm, onBack = {})
            }
        }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithText("alice", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        // Alice is TEAM_LEAD => "Set as Collaborator" should appear (not "Demote")
        onAllNodesWithText("Set as Collaborator", useUnmergedTree = true).assertCountEquals(1)
        // Bob is COLLABORATOR => "Set as Lead" should appear (not "Promote")
        onAllNodesWithText("Set as Lead", useUnmergedTree = true).assertCountEquals(1)
        // Old labels should not exist
        onAllNodesWithText("Promote", useUnmergedTree = true).assertCountEquals(0)
        onAllNodesWithText("Demote", useUnmergedTree = true).assertCountEquals(0)
    }

    // ══════════════════════════════════════════════════════════════
    // 11. Loading accessibility — spinner has content description
    // ══════════════════════════════════════════════════════════════

    @Test
    fun `project list loading spinner has accessible content description`() = runComposeUiTest {
        // Use a client that hangs so we stay in loading state
        val neverComplete = kotlinx.coroutines.CompletableDeferred<Unit>()
        val hangingClient = HttpClient(MockEngine { request ->
            neverComplete.await()
            respond(
                content = """{"projects":[]}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }) {
            install(ContentNegotiation) { json(AppJson) }
            install(HttpCookies)
            expectSuccess = true
        }
        val vm = ProjectListViewModel(ProjectApiClient(hangingClient), MutableStateFlow(TeamId("test-team")))
        setContent {
            MaterialTheme {
                ProjectListScreen(viewModel = vm, onEditProject = {})
            }
        }

        // While loading, spinner should have content description "Loading projects"
        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithContentDescription("Loading projects").fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithContentDescription("Loading projects").assertExists()
    }

    // ══════════════════════════════════════════════════════════════
    // 12. Cron validation improved — shows "Invalid format" for bad cron
    // ══════════════════════════════════════════════════════════════

    private fun emptyAutomationClient() = mockHttpClient(mapOf(
        "/projects/p1/schedules" to json("""{"schedules":[]}"""),
        "/projects/p1/triggers" to json("""{"triggers":[]}"""),
        "/projects/p1/maven-triggers" to json("""{"triggers":[]}"""),
    ))

    @Test
    fun `automation screen shows Invalid format for bad cron expression`() = runComposeUiTest {
        val vm = ProjectAutomationViewModel(
            projectId = ProjectId("p1"),
            scheduleClient = ScheduleApiClient(emptyAutomationClient()),
            webhookClient = WebhookTriggerApiClient(emptyAutomationClient()),
            mavenClient = MavenTriggerApiClient(emptyAutomationClient()),
        )
        setContent { MaterialTheme { ProjectAutomationScreen(viewModel = vm, onBack = {}) } }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("add_schedule_button", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }

        // Open create schedule form
        onNodeWithTag("add_schedule_button", useUnmergedTree = true).performClick()
        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("schedule_cron_input", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }

        // Enter an invalid cron expression
        onNodeWithTag("schedule_cron_input", useUnmergedTree = true).performTextInput("bad cron")

        // Updated text should say "Invalid format" (not "Invalid cron expression")
        onNodeWithText("Invalid format", substring = true).assertExists()
    }

    // ══════════════════════════════════════════════════════════════
    // 13. Brand name capitalization — "TeamCity build" (proper caps)
    //     Canvas-drawn text is not accessible via semantics, so we test
    //     the composable typeLabel() function by rendering it as Text.
    // ══════════════════════════════════════════════════════════════

    @Test
    fun `block typeLabel shows TeamCity build with proper capitalization`() = runComposeUiTest {
        val block = Block.ActionBlock(
            id = BlockId("b1"),
            name = "Build",
            type = BlockType.TEAMCITY_BUILD,
        )
        setContent {
            MaterialTheme {
                Column {
                    Text(
                        text = block.typeLabel(),
                        modifier = Modifier.testTag("type_label"),
                    )
                }
            }
        }

        // Verify the label uses proper capitalization: "TeamCity build"
        onNodeWithTag("type_label").assertTextEquals("TeamCity build")
    }

    @Test
    fun `block typeLabel shows GitHub action with proper capitalization`() = runComposeUiTest {
        val block = Block.ActionBlock(
            id = BlockId("b2"),
            name = "CI",
            type = BlockType.GITHUB_ACTION,
        )
        setContent {
            MaterialTheme {
                Column {
                    Text(
                        text = block.typeLabel(),
                        modifier = Modifier.testTag("type_label"),
                    )
                }
            }
        }

        // Verify "GitHub action" (not "github action")
        onNodeWithTag("type_label").assertTextEquals("GitHub action")
    }
}
