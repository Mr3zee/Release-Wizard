package com.github.mr3zee

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import com.github.mr3zee.api.TeamApiClient
import com.github.mr3zee.model.TeamId
import com.github.mr3zee.teams.*
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.cookies.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class TeamScreensTest {

    // --- TeamListScreen ---

    private val teamsListJson = """{"teams":[
        {"team":{"id":"t1","name":"Alpha Team","description":"First team","createdAt":0},"memberCount":3},
        {"team":{"id":"t2","name":"Beta Squad","description":"","createdAt":0},"memberCount":1}
    ],"pagination":{"totalCount":2,"offset":0,"limit":20}}"""

    private fun teamListClient(json: String = teamsListJson, status: HttpStatusCode = HttpStatusCode.OK) = mockHttpClient(
        mapOf("/teams" to json(json, status))
    )

    @Test
    fun `team list shows teams from API`() = runComposeUiTest {
        val vm = TeamListViewModel(TeamApiClient(teamListClient()))
        setContent {
            MaterialTheme {
                TeamListScreen(viewModel = vm, onTeamClick = {}, onTeamCreated = {}, onMyInvites = {})
            }
        }

        waitUntil(timeoutMillis = 3000L) { onAllNodesWithText("Alpha Team").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("team_list_screen").assertExists()
        onNodeWithText("Alpha Team").assertExists()
        onNodeWithText("Beta Squad").assertExists()
        onNodeWithText("3 members").assertExists()
        onNodeWithText("1 member").assertExists()
    }

    @Test
    fun `team list shows empty state`() = runComposeUiTest {
        val vm = TeamListViewModel(TeamApiClient(teamListClient("""{"teams":[],"pagination":{"totalCount":0,"offset":0,"limit":20}}""")))
        setContent {
            MaterialTheme {
                TeamListScreen(viewModel = vm, onTeamClick = {}, onTeamCreated = {}, onMyInvites = {})
            }
        }

        waitUntil(timeoutMillis = 3000L) { onAllNodesWithText("No teams yet", substring = true).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("No teams yet. Create one to get started.").assertExists()
    }

    @Test
    fun `team list has create FAB and My Invites button`() = runComposeUiTest {
        val vm = TeamListViewModel(TeamApiClient(teamListClient("""{"teams":[],"pagination":{"totalCount":0,"offset":0,"limit":20}}""")))
        setContent {
            MaterialTheme {
                TeamListScreen(viewModel = vm, onTeamClick = {}, onTeamCreated = {}, onMyInvites = {})
            }
        }

        waitUntil(timeoutMillis = 3000L) { onAllNodesWithTag("create_team_fab").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("create_team_fab").assertExists()
        onNodeWithTag("my_invites_button").assertExists()
    }

    @Test
    fun `team list shows create team dialog`() = runComposeUiTest {
        val vm = TeamListViewModel(TeamApiClient(teamListClient("""{"teams":[],"pagination":{"totalCount":0,"offset":0,"limit":20}}""")))
        setContent {
            MaterialTheme {
                TeamListScreen(viewModel = vm, onTeamClick = {}, onTeamCreated = {}, onMyInvites = {})
            }
        }

        waitUntil(timeoutMillis = 3000L) { onAllNodesWithTag("create_team_fab").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("create_team_fab").performClick()
        waitUntil(timeoutMillis = 2000L) { onAllNodesWithTag("team_name_input").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("team_name_input").assertExists()
        onNodeWithTag("team_description_input").assertExists()
        onNodeWithText("New Team").assertExists()
    }

    @Test
    fun `team list has search field`() = runComposeUiTest {
        val vm = TeamListViewModel(TeamApiClient(teamListClient()))
        setContent {
            MaterialTheme {
                TeamListScreen(viewModel = vm, onTeamClick = {}, onTeamCreated = {}, onMyInvites = {})
            }
        }

        waitUntil(timeoutMillis = 3000L) { onAllNodesWithTag("team_search_field").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("team_search_field").assertExists()
    }

    // --- TeamDetailScreen ---

    private val teamDetailJson = """{"team":{"id":"t1","name":"Alpha Team","description":"The first team","createdAt":0},"members":[
        {"teamId":"t1","userId":"u1","username":"alice","role":"TEAM_LEAD","joinedAt":0},
        {"teamId":"t1","userId":"u2","username":"bob","role":"COLLABORATOR","joinedAt":0}
    ]}"""

    private fun teamDetailClient(json: String = teamDetailJson) = mockHttpClient(
        mapOf("/teams/t1" to json(json))
    )

    @Test
    fun `team detail shows team info and members`() = runComposeUiTest {
        val vm = TeamDetailViewModel(TeamId("t1"), TeamApiClient(teamDetailClient()))
        setContent {
            MaterialTheme {
                TeamDetailScreen(viewModel = vm, onManage = {}, onBack = {})
            }
        }

        waitUntil(timeoutMillis = 3000L) { onAllNodesWithText("Alpha Team", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("team_detail_screen").assertExists()
        // "Alpha Team" now appears only in the TopAppBar title (removed from info card)
        onAllNodesWithText("Alpha Team", useUnmergedTree = true).assertCountEquals(1)
        onNodeWithText("The first team", useUnmergedTree = true).assertExists()
        onNodeWithText("alice", useUnmergedTree = true).assertExists()
        onNodeWithText("bob", useUnmergedTree = true).assertExists()
        onNodeWithText("Lead", useUnmergedTree = true).assertExists()
        onNodeWithText("Collaborator", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `team detail has manage button`() = runComposeUiTest {
        val vm = TeamDetailViewModel(TeamId("t1"), TeamApiClient(teamDetailClient()))
        setContent {
            MaterialTheme {
                TeamDetailScreen(viewModel = vm, onManage = {}, onBack = {}, isTeamLead = true)
            }
        }

        waitUntil(timeoutMillis = 3000L) { onAllNodesWithTag("manage_team_button").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("manage_team_button").assertExists()
    }

    // --- TeamManageScreen ---

    private fun teamManageClient() = mockHttpClient(mapOf(
        "/teams/t1" to json("""{"team":{"id":"t1","name":"Alpha Team","description":"First team","createdAt":0},"members":[
            {"teamId":"t1","userId":"u1","username":"alice","role":"TEAM_LEAD","joinedAt":0},
            {"teamId":"t1","userId":"u2","username":"bob","role":"COLLABORATOR","joinedAt":0}
        ],"memberCount":2,"inviteCount":1,"joinRequestCount":1}"""),
        "/teams/t1/members" to json("""{"members":[
            {"teamId":"t1","userId":"u1","username":"alice","role":"TEAM_LEAD","joinedAt":0},
            {"teamId":"t1","userId":"u2","username":"bob","role":"COLLABORATOR","joinedAt":0}
        ]}"""),
        "/teams/t1/invites" to json("""{"invites":[
            {"id":"inv1","teamId":"t1","teamName":"Alpha","invitedUserId":"u3","invitedUsername":"charlie","invitedByUserId":"u1","invitedByUsername":"alice","status":"PENDING","createdAt":0}
        ]}"""),
        "/teams/t1/join-requests" to json("""{"requests":[
            {"id":"jr1","teamId":"t1","teamName":"Alpha","userId":"u4","username":"dave","status":"PENDING","createdAt":0}
        ]}"""),
    ))

    @Test
    fun `team manage shows members invites and join requests`() = runComposeUiTest {
        val vm = TeamManageViewModel(TeamId("t1"), TeamApiClient(teamManageClient()))
        setContent {
            MaterialTheme {
                TeamManageScreen(viewModel = vm, onBack = {})
            }
        }

        waitUntil(timeoutMillis = 3000L) { onAllNodesWithText("alice").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("team_manage_screen").assertExists()
        // Members
        onNodeWithText("alice").assertExists()
        onNodeWithText("bob").assertExists()
        // Pending invites
        onNodeWithText("charlie").assertExists()
        onNodeWithText("Pending Invites (1)").assertExists()
        // Join requests
        onNodeWithText("dave").assertExists()
        onNodeWithText("Join Requests (1)").assertExists()
    }

    @Test
    fun `team manage has invite user button`() = runComposeUiTest {
        val vm = TeamManageViewModel(TeamId("t1"), TeamApiClient(teamManageClient()))
        setContent {
            MaterialTheme {
                TeamManageScreen(viewModel = vm, onBack = {})
            }
        }

        waitUntil(timeoutMillis = 3000L) { onAllNodesWithTag("invite_user_button").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("invite_user_button").assertExists()
    }

    @Test
    fun `team manage shows invite dialog`() = runComposeUiTest {
        val vm = TeamManageViewModel(TeamId("t1"), TeamApiClient(teamManageClient()))
        setContent {
            MaterialTheme {
                TeamManageScreen(viewModel = vm, onBack = {})
            }
        }

        waitUntil(timeoutMillis = 3000L) { onAllNodesWithTag("invite_user_button").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("invite_user_button").performClick()
        waitUntil(timeoutMillis = 2000L) { onAllNodesWithTag("invite_user_id_input").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("invite_user_id_input").assertExists()
        // "Invite User" appears as both button text and dialog title — verify dialog showed via input field
        onAllNodesWithText("Invite User", useUnmergedTree = true).assertCountEquals(2)
    }

    @Test
    fun `team manage shows role toggle and remove for members`() = runComposeUiTest {
        val vm = TeamManageViewModel(TeamId("t1"), TeamApiClient(teamManageClient()))
        setContent {
            MaterialTheme {
                TeamManageScreen(viewModel = vm, onBack = {})
            }
        }

        waitUntil(timeoutMillis = 3000L) { onAllNodesWithText("alice").fetchSemanticsNodes().isNotEmpty() }
        // Alice is TEAM_LEAD, so "Demote" should appear
        onAllNodesWithText("Demote").assertCountEquals(1)
        // Bob is COLLABORATOR, so "Promote" should appear
        onAllNodesWithText("Promote").assertCountEquals(1)
        // Both should have Remove
        onAllNodesWithText("Remove").assertCountEquals(2)
    }

    @Test
    fun `team manage shows approve reject for join requests`() = runComposeUiTest {
        val vm = TeamManageViewModel(TeamId("t1"), TeamApiClient(teamManageClient()))
        setContent {
            MaterialTheme {
                TeamManageScreen(viewModel = vm, onBack = {})
            }
        }

        waitUntil(timeoutMillis = 3000L) { onAllNodesWithText("dave").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("Approve").assertExists()
        onAllNodesWithText("Reject").assertCountEquals(1)
    }

    // --- MyInvitesScreen ---

    private val myInvitesJson = """{"invites":[
        {"id":"inv1","teamId":"t1","teamName":"Alpha Team","invitedUserId":"me","invitedUsername":"me","invitedByUserId":"u1","invitedByUsername":"alice","status":"PENDING","createdAt":0},
        {"id":"inv2","teamId":"t2","teamName":"Beta Squad","invitedUserId":"me","invitedUsername":"me","invitedByUserId":"u3","invitedByUsername":"charlie","status":"PENDING","createdAt":0}
    ]}"""

    private fun myInvitesClient(json: String = myInvitesJson) = mockHttpClient(
        mapOf("/auth/me/invites" to json(json))
    )

    @Test
    fun `my invites shows pending invites`() = runComposeUiTest {
        val vm = MyInvitesViewModel(TeamApiClient(myInvitesClient()))
        setContent {
            MaterialTheme {
                MyInvitesScreen(viewModel = vm, onBack = {})
            }
        }

        waitUntil(timeoutMillis = 3000L) { onAllNodesWithText("Alpha Team").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("my_invites_screen").assertExists()
        onNodeWithText("Alpha Team").assertExists()
        onNodeWithText("Beta Squad").assertExists()
        onNodeWithText("Invited by alice").assertExists()
        onNodeWithText("Invited by charlie").assertExists()
    }

    @Test
    fun `my invites has accept and decline buttons`() = runComposeUiTest {
        val vm = MyInvitesViewModel(TeamApiClient(myInvitesClient()))
        setContent {
            MaterialTheme {
                MyInvitesScreen(viewModel = vm, onBack = {})
            }
        }

        waitUntil(timeoutMillis = 3000L) { onAllNodesWithText("Accept").fetchSemanticsNodes().isNotEmpty() }
        onAllNodesWithText("Accept").assertCountEquals(2)
        onAllNodesWithText("Decline").assertCountEquals(2)
    }

    @Test
    fun `my invites shows empty state`() = runComposeUiTest {
        val vm = MyInvitesViewModel(TeamApiClient(myInvitesClient("""{"invites":[]}""")))
        setContent {
            MaterialTheme {
                MyInvitesScreen(viewModel = vm, onBack = {})
            }
        }

        waitUntil(timeoutMillis = 3000L) { onAllNodesWithText("No pending invites", substring = true).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("No pending invites.").assertExists()
    }

    // --- ProjectListScreen team switcher ---

    @Test
    fun `project list shows Teams button`() = runComposeUiTest {
        val projectClient = mockHttpClient(mapOf("/projects" to json("""{"projects":[]}""")))
        val vm = com.github.mr3zee.projects.ProjectListViewModel(
            com.github.mr3zee.api.ProjectApiClient(projectClient),
            kotlinx.coroutines.flow.MutableStateFlow(TeamId("t1")),
        )
        setContent {
            MaterialTheme {
                com.github.mr3zee.projects.ProjectListScreen(
                    viewModel = vm,
                    onEditProject = {},
                    onTeams = {},
                    onLogout = {},
                )
            }
        }

        waitUntil(timeoutMillis = 3000L) { onAllNodesWithTag("teams_button").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("teams_button").assertExists()
    }

    @Test
    fun `project list shows team switcher when multiple teams`() = runComposeUiTest {
        val projectClient = mockHttpClient(mapOf("/projects" to json("""{"projects":[]}""")))
        val activeTeamId = kotlinx.coroutines.flow.MutableStateFlow<TeamId?>(TeamId("t1"))
        val userTeams = listOf(
            com.github.mr3zee.api.UserTeamInfo(TeamId("t1"), "Alpha", com.github.mr3zee.model.TeamRole.TEAM_LEAD),
            com.github.mr3zee.api.UserTeamInfo(TeamId("t2"), "Beta", com.github.mr3zee.model.TeamRole.COLLABORATOR),
        )
        val vm = com.github.mr3zee.projects.ProjectListViewModel(
            com.github.mr3zee.api.ProjectApiClient(projectClient),
            activeTeamId,
        )
        setContent {
            MaterialTheme {
                com.github.mr3zee.projects.ProjectListScreen(
                    viewModel = vm,
                    onEditProject = {},
                    activeTeamId = activeTeamId,
                    userTeams = userTeams,
                )
            }
        }

        waitUntil(timeoutMillis = 3000L) { onAllNodesWithTag("team_switcher").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("team_switcher").assertExists()
        // Click team switcher to open DropdownMenu picker
        onNodeWithTag("team_switcher").performClick()
        waitUntil(timeoutMillis = 2000L) { onAllNodesWithTag("team_picker_t1", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("team_picker_t1", useUnmergedTree = true).assertExists()
        onNodeWithTag("team_picker_t2", useUnmergedTree = true).assertExists()
    }

    // ---- Refresh Tests ----

    @Test
    fun `refresh button exists on team list`() = runComposeUiTest {
        val vm = TeamListViewModel(TeamApiClient(teamListClient("""{"teams":[],"pagination":{"totalCount":0,"offset":0,"limit":20}}""")))
        setContent {
            MaterialTheme {
                TeamListScreen(viewModel = vm, onTeamClick = {}, onTeamCreated = {}, onMyInvites = {})
            }
        }

        onNodeWithTag("refresh_button").assertExists()
    }

    @Test
    fun `refresh triggers re-fetch with new data`() = runComposeUiTest {
        val returnNewData = AtomicBoolean(false)
        val client = HttpClient(MockEngine { request ->
            val path = request.url.encodedPath
            val jsonHeaders = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            when {
                path.endsWith("/teams") -> {
                    val teams = if (!returnNewData.get()) {
                        """[{"team":{"id":"t1","name":"Alpha Team","description":"","createdAt":0},"memberCount":3}]"""
                    } else {
                        """[{"team":{"id":"t1","name":"Alpha Team","description":"","createdAt":0},"memberCount":3},{"team":{"id":"t-new","name":"Gamma New","description":"","createdAt":0},"memberCount":1}]"""
                    }
                    respond("""{"teams":$teams,"pagination":{"totalCount":2,"offset":0,"limit":20}}""", status = HttpStatusCode.OK, headers = jsonHeaders)
                }
                else -> respond("{}", status = HttpStatusCode.OK, headers = jsonHeaders)
            }
        }) {
            install(ContentNegotiation) { json(AppJson) }
            install(HttpCookies)
            expectSuccess = true
        }
        val vm = TeamListViewModel(TeamApiClient(client))

        setContent {
            MaterialTheme {
                TeamListScreen(viewModel = vm, onTeamClick = {}, onTeamCreated = {}, onMyInvites = {})
            }
        }

        waitUntil(timeoutMillis = 3000L) { onAllNodesWithText("Alpha Team").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("Gamma New").assertDoesNotExist()

        returnNewData.set(true)
        onNodeWithTag("refresh_button").performClick()

        waitUntil(timeoutMillis = 5000L) { onAllNodesWithText("Gamma New").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("Gamma New").assertExists()
    }

    @Test
    fun `refresh error shows banner and keeps list visible`() = runComposeUiTest {
        val failOnRefresh = AtomicBoolean(false)
        val client = HttpClient(MockEngine { request ->
            val path = request.url.encodedPath
            val jsonHeaders = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            when {
                path.endsWith("/teams") -> {
                    if (!failOnRefresh.get()) {
                        respond(teamsListJson, status = HttpStatusCode.OK, headers = jsonHeaders)
                    } else {
                        respond("Server error", status = HttpStatusCode.InternalServerError, headers = jsonHeaders)
                    }
                }
                else -> respond("{}", status = HttpStatusCode.OK, headers = jsonHeaders)
            }
        }) {
            install(ContentNegotiation) { json(AppJson) }
            install(HttpCookies)
            expectSuccess = true
        }
        val vm = TeamListViewModel(TeamApiClient(client))

        setContent {
            MaterialTheme {
                TeamListScreen(viewModel = vm, onTeamClick = {}, onTeamCreated = {}, onMyInvites = {})
            }
        }

        waitUntil(timeoutMillis = 3000L) { onAllNodesWithText("Alpha Team").fetchSemanticsNodes().isNotEmpty() }
        failOnRefresh.set(true)
        onNodeWithTag("refresh_button").performClick()
        waitUntil(timeoutMillis = 5000L) { onAllNodesWithTag("refresh_error_banner").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("refresh_error_banner").assertExists()
        onNodeWithText("Alpha Team").assertExists()
    }

    @Test
    fun `dismiss refresh error hides banner`() = runComposeUiTest {
        val failOnRefresh = AtomicBoolean(false)
        val client = HttpClient(MockEngine { request ->
            val path = request.url.encodedPath
            val jsonHeaders = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            when {
                path.endsWith("/teams") -> {
                    if (!failOnRefresh.get()) {
                        respond(teamsListJson, status = HttpStatusCode.OK, headers = jsonHeaders)
                    } else {
                        respond("Server error", status = HttpStatusCode.InternalServerError, headers = jsonHeaders)
                    }
                }
                else -> respond("{}", status = HttpStatusCode.OK, headers = jsonHeaders)
            }
        }) {
            install(ContentNegotiation) { json(AppJson) }
            install(HttpCookies)
            expectSuccess = true
        }
        val vm = TeamListViewModel(TeamApiClient(client))

        setContent {
            MaterialTheme {
                TeamListScreen(viewModel = vm, onTeamClick = {}, onTeamCreated = {}, onMyInvites = {})
            }
        }

        waitUntil(timeoutMillis = 3000L) { onAllNodesWithText("Alpha Team").fetchSemanticsNodes().isNotEmpty() }
        failOnRefresh.set(true)
        onNodeWithTag("refresh_button").performClick()
        waitUntil(timeoutMillis = 5000L) { onAllNodesWithTag("refresh_error_banner").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("dismiss_refresh_error").performClick()
        waitUntil(timeoutMillis = 3000L) { onAllNodesWithTag("refresh_error_banner").fetchSemanticsNodes().isEmpty() }
        onNodeWithTag("refresh_error_banner").assertDoesNotExist()
    }
}
