package com.github.mr3zee

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.SemanticsProperties
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
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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

        waitUntil(timeoutMillis = 3000L) { onAllNodesWithText("Alpha Team", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("team_list_screen").assertExists()
        onNodeWithText("Alpha Team", useUnmergedTree = true).assertExists()
        onNodeWithText("Beta Squad", useUnmergedTree = true).assertExists()
        onNodeWithText("3 members", useUnmergedTree = true).assertExists()
        onNodeWithText("1 member", useUnmergedTree = true).assertExists()
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
        onNodeWithTag("create_team_form").assertExists()
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

    // --- QA-TEAMLIST-1: Non-member team shows "Request to Join" button ---

    @Test
    fun `non-member team shows request to join button`() = runComposeUiTest {
        val vm = TeamListViewModel(TeamApiClient(teamListClient()))
        setContent {
            MaterialTheme {
                TeamListScreen(
                    viewModel = vm,
                    onTeamClick = {},
                    onTeamCreated = {},
                    onMyInvites = {},
                    memberTeamIds = setOf(TeamId("t1")), // Only member of t1, NOT t2
                )
            }
        }

        waitUntil(timeoutMillis = 3000L) { onAllNodesWithText("Alpha Team", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        // t1 is a member — should show Member badge, no Request to Join
        onNodeWithTag("member_badge_t1", useUnmergedTree = true).assertExists()
        // t2 is not a member — should show "Request to Join" button
        onNodeWithText("Request to Join", useUnmergedTree = true).assertExists()
    }

    // --- QA-TEAMLIST-2: Member team item click fires callback ---

    @Test
    fun `member team item click fires onTeamClick callback`() = runComposeUiTest {
        val clickedTeamId = AtomicReference<String?>(null)
        val vm = TeamListViewModel(TeamApiClient(teamListClient()))
        setContent {
            MaterialTheme {
                TeamListScreen(
                    viewModel = vm,
                    onTeamClick = { clickedTeamId.set(it.value) },
                    onTeamCreated = {},
                    onMyInvites = {},
                    memberTeamIds = setOf(TeamId("t1"), TeamId("t2")),
                )
            }
        }

        waitUntil(timeoutMillis = 3000L) { onAllNodesWithText("Alpha Team", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("team_item_t1", useUnmergedTree = true).performClick()
        waitUntil(timeoutMillis = 2000L) { clickedTeamId.get() != null }
        assertEquals("t1", clickedTeamId.get())
    }

    // --- QA-TEAMLIST-3: memberTeamIds drives rendering ---

    @Test
    fun `memberTeamIds drives member badge vs request to join rendering`() = runComposeUiTest {
        val vm = TeamListViewModel(TeamApiClient(teamListClient()))
        setContent {
            MaterialTheme {
                TeamListScreen(
                    viewModel = vm,
                    onTeamClick = {},
                    onTeamCreated = {},
                    onMyInvites = {},
                    memberTeamIds = setOf(TeamId("t1"), TeamId("t2")), // Both are members
                )
            }
        }

        waitUntil(timeoutMillis = 3000L) { onAllNodesWithText("Alpha Team", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        // Both items should show Member badge
        onNodeWithTag("member_badge_t1", useUnmergedTree = true).assertExists()
        onNodeWithTag("member_badge_t2", useUnmergedTree = true).assertExists()
        // No "Request to Join" button should be visible
        onAllNodesWithText("Request to Join", useUnmergedTree = true).assertCountEquals(0)
    }

    @Test
    fun `empty memberTeamIds shows request to join for all teams`() = runComposeUiTest {
        val vm = TeamListViewModel(TeamApiClient(teamListClient()))
        setContent {
            MaterialTheme {
                TeamListScreen(
                    viewModel = vm,
                    onTeamClick = {},
                    onTeamCreated = {},
                    onMyInvites = {},
                    memberTeamIds = emptySet(), // No memberships
                )
            }
        }

        waitUntil(timeoutMillis = 3000L) { onAllNodesWithText("Alpha Team", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        // All teams should show "Request to Join"
        onAllNodesWithText("Request to Join", useUnmergedTree = true).assertCountEquals(2)
    }

    // --- QA-TEAMLIST-4: "Request to Join" calls VM and shows info banner ---

    @Test
    fun `request to join calls VM and shows info banner`() = runComposeUiTest {
        val joinRequestJson = """{"id":"jr1","teamId":"t2","teamName":"Beta Squad","userId":"me","username":"me","status":"PENDING","createdAt":0}"""
        val client = mockHttpClient(mapOf(
            "/teams" to json(teamsListJson),
            "/teams/t2/join-requests" to json(joinRequestJson, method = HttpMethod.Post),
        ))
        val vm = TeamListViewModel(TeamApiClient(client))
        setContent {
            MaterialTheme {
                TeamListScreen(
                    viewModel = vm,
                    onTeamClick = {},
                    onTeamCreated = {},
                    onMyInvites = {},
                    memberTeamIds = setOf(TeamId("t1")), // Only t1 is member, t2 is not
                )
            }
        }

        waitUntil(timeoutMillis = 3000L) { onAllNodesWithText("Request to Join", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        // Click "Request to Join" on t2
        onNodeWithText("Request to Join", useUnmergedTree = true).performClick()
        // Should show success info banner
        waitUntil(timeoutMillis = 3000L) { onAllNodesWithText("Join request submitted", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("Join request submitted", useUnmergedTree = true).assertExists()
    }

    // --- QA-TEAMLIST-5: Empty search results state ---

    @Test
    fun `empty search results shows no results message`() = runComposeUiTest {
        val returnEmpty = AtomicBoolean(false)
        val client = HttpClient(MockEngine { request ->
            val path = request.url.encodedPath
            val jsonHeaders = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            when {
                path.endsWith("/teams") -> {
                    val body = if (!returnEmpty.get()) {
                        teamsListJson
                    } else {
                        """{"teams":[],"pagination":{"totalCount":0,"offset":0,"limit":20}}"""
                    }
                    respond(body, status = HttpStatusCode.OK, headers = jsonHeaders)
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

        // Wait for initial load
        waitUntil(timeoutMillis = 3000L) { onAllNodesWithText("Alpha Team", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }

        // Now make API return empty, type search text
        returnEmpty.set(true)
        onNodeWithTag("team_search_field").performTextInput("zzz_nonexistent")

        // Wait for debounce (300ms) + API to return empty
        waitUntil(timeoutMillis = 5000L) { onAllNodesWithText("No results match your search.").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("No results match your search.").assertExists()
        onNodeWithText("Clear search").assertExists()
    }

    // --- QA-TEAMLIST-6: Create form submit button disabled when blank ---

    @Test
    fun `create form submit button disabled when name is blank`() = runComposeUiTest {
        val vm = TeamListViewModel(TeamApiClient(teamListClient("""{"teams":[],"pagination":{"totalCount":0,"offset":0,"limit":20}}""")))
        setContent {
            MaterialTheme {
                TeamListScreen(viewModel = vm, onTeamClick = {}, onTeamCreated = {}, onMyInvites = {})
            }
        }

        waitUntil(timeoutMillis = 3000L) { onAllNodesWithTag("create_team_fab").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("create_team_fab").performClick()
        waitUntil(timeoutMillis = 2000L) { onAllNodesWithTag("team_name_input").fetchSemanticsNodes().isNotEmpty() }
        // With empty name, Create button should be disabled
        onNodeWithTag("create_team_confirm").assertIsNotEnabled()
    }

    // --- QA-TEAMLIST-7: Create form submit calls onTeamCreated ---

    @Test
    fun `create form submit calls onTeamCreated with new team id`() = runComposeUiTest {
        val createdTeamId = AtomicReference<String?>(null)
        val createResponseJson = """{"team":{"id":"t-new","name":"New Team","description":"A new team","createdAt":0},"memberCount":1}"""
        // Custom client to handle both GET /teams and POST /teams
        val customClient = HttpClient(MockEngine { request ->
            val path = request.url.encodedPath
            val method = request.method
            val jsonHeaders = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            when {
                path.endsWith("/teams") && method == HttpMethod.Post -> {
                    respond(createResponseJson, status = HttpStatusCode.OK, headers = jsonHeaders)
                }
                path.endsWith("/teams") -> {
                    respond("""{"teams":[],"pagination":{"totalCount":0,"offset":0,"limit":20}}""", status = HttpStatusCode.OK, headers = jsonHeaders)
                }
                else -> respond("{}", status = HttpStatusCode.OK, headers = jsonHeaders)
            }
        }) {
            install(ContentNegotiation) { json(AppJson) }
            install(HttpCookies)
            expectSuccess = true
        }
        val vm = TeamListViewModel(TeamApiClient(customClient))
        setContent {
            MaterialTheme {
                TeamListScreen(
                    viewModel = vm,
                    onTeamClick = {},
                    onTeamCreated = { createdTeamId.set(it.value) },
                    onMyInvites = {},
                )
            }
        }

        waitUntil(timeoutMillis = 3000L) { onAllNodesWithTag("create_team_fab").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("create_team_fab").performClick()
        waitUntil(timeoutMillis = 2000L) { onAllNodesWithTag("team_name_input").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("team_name_input").performTextInput("New Team")
        // Button should now be enabled
        onNodeWithTag("create_team_confirm").assertIsEnabled()
        onNodeWithTag("create_team_confirm").performClick()
        waitUntil(timeoutMillis = 3000L) { createdTeamId.get() != null }
        assertEquals("t-new", createdTeamId.get())
    }

    // --- QA-TEAMLIST-8: Initial load failure shows snackbar with Retry ---

    @Test
    fun `initial load failure shows snackbar with retry`() = runComposeUiTest {
        val vm = TeamListViewModel(TeamApiClient(teamListClient("Server error", HttpStatusCode.InternalServerError)))
        setContent {
            MaterialTheme {
                TeamListScreen(viewModel = vm, onTeamClick = {}, onTeamCreated = {}, onMyInvites = {})
            }
        }

        // Snackbar should appear with error message and Retry action
        waitUntil(timeoutMillis = 5000L) { onAllNodesWithText("Retry").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("Retry").assertExists()
    }

    // --- QA-TEAMLIST-9: Search field sends query to API ---

    @Test
    fun `search field sends query to API and updates list`() = runComposeUiTest {
        val lastSearchQuery = AtomicReference<String?>(null)
        val client = HttpClient(MockEngine { request ->
            val path = request.url.encodedPath
            val jsonHeaders = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            when {
                path.endsWith("/teams") -> {
                    val query = request.url.parameters["q"]
                    lastSearchQuery.set(query)
                    val body = if (query != null && query.contains("Alpha")) {
                        """{"teams":[{"team":{"id":"t1","name":"Alpha Team","description":"First team","createdAt":0},"memberCount":3}],"pagination":{"totalCount":1,"offset":0,"limit":20}}"""
                    } else {
                        teamsListJson
                    }
                    respond(body, status = HttpStatusCode.OK, headers = jsonHeaders)
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

        waitUntil(timeoutMillis = 3000L) { onAllNodesWithText("Alpha Team", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("team_search_field").performTextInput("Alpha")
        // Wait for debounce (300ms) + API call
        waitUntil(timeoutMillis = 5000L) { lastSearchQuery.get() == "Alpha" }
        assertEquals("Alpha", lastSearchQuery.get())
    }

    // --- QA-TEAMLIST-10: Create form close button dismisses form ---

    @Test
    fun `create form close button dismisses the form`() = runComposeUiTest {
        val vm = TeamListViewModel(TeamApiClient(teamListClient("""{"teams":[],"pagination":{"totalCount":0,"offset":0,"limit":20}}""")))
        setContent {
            MaterialTheme {
                TeamListScreen(viewModel = vm, onTeamClick = {}, onTeamCreated = {}, onMyInvites = {})
            }
        }

        waitUntil(timeoutMillis = 3000L) { onAllNodesWithTag("create_team_fab").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("create_team_fab").performClick()
        waitUntil(timeoutMillis = 2000L) { onAllNodesWithTag("create_team_form").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("create_team_form").assertExists()
        // Close button uses testTag "${testTag}_close" => "create_team_form_close"
        onNodeWithTag("create_team_form_close").performClick()
        waitUntil(timeoutMillis = 2000L) { onAllNodesWithTag("create_team_form").fetchSemanticsNodes().isEmpty() }
        onNodeWithTag("create_team_form").assertDoesNotExist()
    }

    // --- QA-TEAMLIST-11: My Invites button fires callback ---

    @Test
    fun `my invites button fires onMyInvites callback`() = runComposeUiTest {
        val invitesCalled = AtomicBoolean(false)
        val vm = TeamListViewModel(TeamApiClient(teamListClient("""{"teams":[],"pagination":{"totalCount":0,"offset":0,"limit":20}}""")))
        setContent {
            MaterialTheme {
                TeamListScreen(
                    viewModel = vm,
                    onTeamClick = {},
                    onTeamCreated = {},
                    onMyInvites = { invitesCalled.set(true) },
                )
            }
        }

        waitUntil(timeoutMillis = 3000L) { onAllNodesWithTag("my_invites_button").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("my_invites_button").performClick()
        assertTrue(invitesCalled.get(), "onMyInvites callback should have been called")
    }

    // --- QA-TEAMLIST-12: Create form name input enables submit ---

    @Test
    fun `create form name input enables submit button`() = runComposeUiTest {
        val vm = TeamListViewModel(TeamApiClient(teamListClient("""{"teams":[],"pagination":{"totalCount":0,"offset":0,"limit":20}}""")))
        setContent {
            MaterialTheme {
                TeamListScreen(viewModel = vm, onTeamClick = {}, onTeamCreated = {}, onMyInvites = {})
            }
        }

        waitUntil(timeoutMillis = 3000L) { onAllNodesWithTag("create_team_fab").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("create_team_fab").performClick()
        waitUntil(timeoutMillis = 2000L) { onAllNodesWithTag("team_name_input").fetchSemanticsNodes().isNotEmpty() }
        // Initially disabled
        onNodeWithTag("create_team_confirm").assertIsNotEnabled()
        // Type a name
        onNodeWithTag("team_name_input").performTextInput("Test Team")
        // Now enabled
        onNodeWithTag("create_team_confirm").assertIsEnabled()
    }

    // --- QA-TEAMLIST-13: Empty state create button opens form ---

    @Test
    fun `empty state create button opens create form`() = runComposeUiTest {
        val vm = TeamListViewModel(TeamApiClient(teamListClient("""{"teams":[],"pagination":{"totalCount":0,"offset":0,"limit":20}}""")))
        setContent {
            MaterialTheme {
                TeamListScreen(viewModel = vm, onTeamClick = {}, onTeamCreated = {}, onMyInvites = {})
            }
        }

        waitUntil(timeoutMillis = 3000L) { onAllNodesWithTag("empty_state_create_team_button").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("empty_state_create_team_button").performClick()
        waitUntil(timeoutMillis = 2000L) { onAllNodesWithTag("create_team_form").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("create_team_form").assertExists()
        onNodeWithTag("team_name_input").assertExists()
    }

    // --- TeamDetailScreen ---

    private val teamDetailJson = """{"team":{"id":"t1","name":"Alpha Team","description":"The first team","createdAt":0},"members":[
        {"teamId":"t1","userId":"u1","username":"alice","role":"TEAM_LEAD","joinedAt":0},
        {"teamId":"t1","userId":"u2","username":"bob","role":"COLLABORATOR","joinedAt":0}
    ]}"""

    private fun teamDetailClient(json: String = teamDetailJson, status: HttpStatusCode = HttpStatusCode.OK) = mockHttpClient(
        mapOf("/teams/t1" to json(json, status))
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
        // "Alpha Team" appears in TopAppBar title + info card heading — assert at least one exists
        onNodeWithText("Alpha Team", useUnmergedTree = true).assertExists()
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

    // --- QA-TEAMDETAIL-1: Initial load failure renders full-page error ---

    @Test
    fun `team detail initial load failure renders full-page error`() = runComposeUiTest {
        val vm = TeamDetailViewModel(TeamId("t1"), TeamApiClient(teamDetailClient("Server error", HttpStatusCode.InternalServerError)))
        setContent {
            MaterialTheme {
                TeamDetailScreen(viewModel = vm, onManage = {}, onBack = {})
            }
        }

        // Should show error text and Retry button (full-page error state)
        waitUntil(timeoutMillis = 5000L) { onAllNodesWithText("Retry").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("Retry").assertExists()
    }

    // --- QA-TEAMDETAIL-2: Retry button calls loadDetail() ---

    @Test
    fun `team detail retry button reloads after failure`() = runComposeUiTest {
        val callCount = AtomicInteger(0)
        val shouldFail = AtomicBoolean(true)
        val client = HttpClient(MockEngine { request ->
            val path = request.url.encodedPath
            val jsonHeaders = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            when {
                path.endsWith("/teams/t1") -> {
                    callCount.incrementAndGet()
                    if (shouldFail.get()) {
                        respond("Server error", status = HttpStatusCode.InternalServerError, headers = jsonHeaders)
                    } else {
                        respond(teamDetailJson, status = HttpStatusCode.OK, headers = jsonHeaders)
                    }
                }
                else -> respond("{}", status = HttpStatusCode.OK, headers = jsonHeaders)
            }
        }) {
            install(ContentNegotiation) { json(AppJson) }
            install(HttpCookies)
            expectSuccess = true
        }
        val vm = TeamDetailViewModel(TeamId("t1"), TeamApiClient(client))
        setContent {
            MaterialTheme {
                TeamDetailScreen(viewModel = vm, onManage = {}, onBack = {})
            }
        }

        // Wait for error state
        waitUntil(timeoutMillis = 5000L) { onAllNodesWithText("Retry").fetchSemanticsNodes().isNotEmpty() }
        // Now make it succeed
        shouldFail.set(false)
        onNodeWithText("Retry").performClick()
        // Should reload and show team data
        waitUntil(timeoutMillis = 5000L) { onAllNodesWithText("Alpha Team", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("Alpha Team", useUnmergedTree = true).assertExists()
        assertTrue(callCount.get() >= 2, "loadDetail should have been called at least twice (initial + retry)")
    }

    // --- QA-TEAMDETAIL-3: Manage button NOT visible when isTeamLead = false ---

    @Test
    fun `team detail manage button not visible when not team lead`() = runComposeUiTest {
        val vm = TeamDetailViewModel(TeamId("t1"), TeamApiClient(teamDetailClient()))
        setContent {
            MaterialTheme {
                TeamDetailScreen(viewModel = vm, onManage = {}, onBack = {}, isTeamLead = false)
            }
        }

        waitUntil(timeoutMillis = 3000L) { onAllNodesWithText("Alpha Team", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("manage_team_button").assertDoesNotExist()
    }

    // --- QA-TEAMDETAIL-4: Leave Team button shows inline confirmation ---

    @Test
    fun `leave team button shows inline confirmation`() = runComposeUiTest {
        val vm = TeamDetailViewModel(TeamId("t1"), TeamApiClient(teamDetailClient()))
        setContent {
            MaterialTheme {
                TeamDetailScreen(viewModel = vm, onManage = {}, onBack = {})
            }
        }

        waitUntil(timeoutMillis = 3000L) { onAllNodesWithText("Alpha Team", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("leave_team_button").performClick()
        waitUntil(timeoutMillis = 2000L) { onAllNodesWithTag("leave_team_confirm").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("leave_team_confirm").assertExists()
        // Should show confirmation message with team name
        onNodeWithText("Are you sure you want to leave \"Alpha Team\"?", useUnmergedTree = true).assertExists()
    }

    // --- QA-TEAMDETAIL-5: Confirming leave fires onBack() ---

    @Test
    fun `confirming leave fires onBack callback`() = runComposeUiTest {
        val backCalled = AtomicBoolean(false)
        val client = mockHttpClient(mapOf(
            "/teams/t1" to json(teamDetailJson),
            "/teams/t1/leave" to json("{}", method = HttpMethod.Post),
        ))
        val vm = TeamDetailViewModel(TeamId("t1"), TeamApiClient(client))
        setContent {
            MaterialTheme {
                TeamDetailScreen(viewModel = vm, onManage = {}, onBack = { backCalled.set(true) })
            }
        }

        waitUntil(timeoutMillis = 3000L) { onAllNodesWithText("Alpha Team", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("leave_team_button").performClick()
        waitUntil(timeoutMillis = 2000L) { onAllNodesWithTag("leave_team_confirm").fetchSemanticsNodes().isNotEmpty() }
        // The confirm button uses testTag "${testTag}_confirm" => "leave_team_confirm_confirm"
        // Wait for it to be enabled (300ms debounce in RwInlineConfirmation)
        waitUntil(timeoutMillis = 2000L) {
            onAllNodesWithTag("leave_team_confirm_confirm").fetchSemanticsNodes().firstOrNull()
                ?.config?.getOrElseNullable(SemanticsProperties.Disabled) { null } == null
        }
        onNodeWithTag("leave_team_confirm_confirm").performClick()
        waitUntil(timeoutMillis = 3000L) { backCalled.get() }
        assertTrue(backCalled.get(), "onBack should have been called after confirming leave")
    }

    // --- QA-TEAMDETAIL-6: Cancelling leave hides confirmation ---

    @Test
    fun `cancelling leave hides confirmation`() = runComposeUiTest {
        val vm = TeamDetailViewModel(TeamId("t1"), TeamApiClient(teamDetailClient()))
        setContent {
            MaterialTheme {
                TeamDetailScreen(viewModel = vm, onManage = {}, onBack = {})
            }
        }

        waitUntil(timeoutMillis = 3000L) { onAllNodesWithText("Alpha Team", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("leave_team_button").performClick()
        waitUntil(timeoutMillis = 2000L) { onAllNodesWithTag("leave_team_confirm").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("leave_team_confirm").assertExists()
        // Cancel button uses testTag "${testTag}_cancel" => "leave_team_confirm_cancel"
        onNodeWithTag("leave_team_confirm_cancel").performClick()
        waitUntil(timeoutMillis = 2000L) { onAllNodesWithTag("leave_team_confirm").fetchSemanticsNodes().isEmpty() }
        onNodeWithTag("leave_team_confirm").assertDoesNotExist()
    }

    // --- QA-TEAMDETAIL-7: Back button fires onBack ---

    @Test
    fun `team detail back button fires onBack`() = runComposeUiTest {
        val backCalled = AtomicBoolean(false)
        val vm = TeamDetailViewModel(TeamId("t1"), TeamApiClient(teamDetailClient()))
        setContent {
            MaterialTheme {
                TeamDetailScreen(viewModel = vm, onManage = {}, onBack = { backCalled.set(true) })
            }
        }

        waitUntil(timeoutMillis = 3000L) { onAllNodesWithText("Alpha Team", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("back_button").performClick()
        assertTrue(backCalled.get(), "onBack should have been called")
    }

    // --- QA-TEAMDETAIL-8: Manage button fires onManage ---

    @Test
    fun `team detail manage button fires onManage`() = runComposeUiTest {
        val manageCalled = AtomicBoolean(false)
        val vm = TeamDetailViewModel(TeamId("t1"), TeamApiClient(teamDetailClient()))
        setContent {
            MaterialTheme {
                TeamDetailScreen(viewModel = vm, onManage = { manageCalled.set(true) }, onBack = {}, isTeamLead = true)
            }
        }

        waitUntil(timeoutMillis = 3000L) { onAllNodesWithTag("manage_team_button").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("manage_team_button").performClick()
        assertTrue(manageCalled.get(), "onManage should have been called")
    }

    // --- QA-TEAMDETAIL-9: Leave team button exists ---

    @Test
    fun `team detail leave team button exists`() = runComposeUiTest {
        val vm = TeamDetailViewModel(TeamId("t1"), TeamApiClient(teamDetailClient()))
        setContent {
            MaterialTheme {
                TeamDetailScreen(viewModel = vm, onManage = {}, onBack = {})
            }
        }

        waitUntil(timeoutMillis = 3000L) { onAllNodesWithText("Alpha Team", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("leave_team_button").assertExists()
    }

    // --- QA-TEAMDETAIL-10: Audit log button fires callback ---

    @Test
    fun `team detail audit log button fires onAuditLog`() = runComposeUiTest {
        val auditCalled = AtomicBoolean(false)
        val vm = TeamDetailViewModel(TeamId("t1"), TeamApiClient(teamDetailClient()))
        setContent {
            MaterialTheme {
                TeamDetailScreen(viewModel = vm, onManage = {}, onAuditLog = { auditCalled.set(true) }, onBack = {})
            }
        }

        waitUntil(timeoutMillis = 3000L) { onAllNodesWithText("Alpha Team", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("audit_log_button").performClick()
        assertTrue(auditCalled.get(), "onAuditLog should have been called")
    }

    // --- QA-TEAMDETAIL-11: Members section heading and member items ---

    @Test
    fun `team detail shows members section heading and member items`() = runComposeUiTest {
        val vm = TeamDetailViewModel(TeamId("t1"), TeamApiClient(teamDetailClient()))
        setContent {
            MaterialTheme {
                TeamDetailScreen(viewModel = vm, onManage = {}, onBack = {})
            }
        }

        waitUntil(timeoutMillis = 3000L) { onAllNodesWithText("Alpha Team", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        // "Members" appears as section heading
        onNodeWithText("Members", useUnmergedTree = true).assertExists()
        // Member items have testTags
        onNodeWithTag("member_item_u1", useUnmergedTree = true).assertExists()
        onNodeWithTag("member_item_u2", useUnmergedTree = true).assertExists()
        // Role badges
        onNodeWithTag("role_badge_u1", useUnmergedTree = true).assertExists()
        onNodeWithTag("role_badge_u2", useUnmergedTree = true).assertExists()
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

        waitUntil(timeoutMillis = 3000L) { onAllNodesWithText("alice", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("team_manage_screen").assertExists()
        // Members
        onNodeWithText("alice", useUnmergedTree = true).assertExists()
        onNodeWithText("bob", useUnmergedTree = true).assertExists()
        // Pending invites
        onNodeWithText("charlie", useUnmergedTree = true).assertExists()
        onNodeWithText("Pending Invites (1)", useUnmergedTree = true).assertExists()
        // Join requests
        onNodeWithText("dave", useUnmergedTree = true).assertExists()
        onNodeWithText("Join Requests (1)", useUnmergedTree = true).assertExists()
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
        // "Invite User" appears as both button text and form title — assert at least one exists
        onNodeWithText("Invite User", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `team manage shows role toggle and remove for members`() = runComposeUiTest {
        val vm = TeamManageViewModel(TeamId("t1"), TeamApiClient(teamManageClient()))
        setContent {
            MaterialTheme {
                TeamManageScreen(viewModel = vm, onBack = {})
            }
        }

        waitUntil(timeoutMillis = 3000L) { onAllNodesWithText("alice", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        // Alice is TEAM_LEAD, so "Demote to Collaborator" should appear
        onAllNodesWithText("Demote to Collaborator", useUnmergedTree = true).assertCountEquals(1)
        // Bob is COLLABORATOR, so "Promote to Lead" should appear
        onAllNodesWithText("Promote to Lead", useUnmergedTree = true).assertCountEquals(1)
        // Both should have Remove
        onAllNodesWithText("Remove", useUnmergedTree = true).assertCountEquals(2)
    }

    @Test
    fun `team manage shows approve reject for join requests`() = runComposeUiTest {
        val vm = TeamManageViewModel(TeamId("t1"), TeamApiClient(teamManageClient()))
        setContent {
            MaterialTheme {
                TeamManageScreen(viewModel = vm, onBack = {})
            }
        }

        waitUntil(timeoutMillis = 3000L) { onAllNodesWithText("dave", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("Approve", useUnmergedTree = true).assertExists()
        onAllNodesWithText("Reject", useUnmergedTree = true).assertCountEquals(1)
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

        waitUntil(timeoutMillis = 3000L) { onAllNodesWithText("Alpha Team", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("my_invites_screen").assertExists()
        onNodeWithText("Alpha Team", useUnmergedTree = true).assertExists()
        onNodeWithText("Beta Squad", useUnmergedTree = true).assertExists()
        onNodeWithText("Invited by alice", useUnmergedTree = true).assertExists()
        onNodeWithText("Invited by charlie", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `my invites has accept and decline buttons`() = runComposeUiTest {
        val vm = MyInvitesViewModel(TeamApiClient(myInvitesClient()))
        setContent {
            MaterialTheme {
                MyInvitesScreen(viewModel = vm, onBack = {})
            }
        }

        waitUntil(timeoutMillis = 3000L) { onAllNodesWithText("Accept", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        onAllNodesWithText("Accept", useUnmergedTree = true).assertCountEquals(2)
        onAllNodesWithText("Decline", useUnmergedTree = true).assertCountEquals(2)
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

    // QA-INVITES-1: Accept triggers acceptInvite, fires callback, removes invite
    @Test
    fun `my invites accept removes invite and fires callback`() = runComposeUiTest {
        val acceptCalled = AtomicBoolean(false)
        val returnEmpty = AtomicBoolean(false)
        val client = HttpClient(MockEngine { request ->
            val path = request.url.encodedPath
            val jsonHeaders = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            when {
                path.endsWith("/auth/me/invites/inv1/accept") -> {
                    returnEmpty.set(true)
                    respond("{}", status = HttpStatusCode.OK, headers = jsonHeaders)
                }
                path.endsWith("/auth/me/invites") -> {
                    val body = if (returnEmpty.get()) {
                        """{"invites":[{"id":"inv2","teamId":"t2","teamName":"Beta Squad","invitedUserId":"me","invitedUsername":"me","invitedByUserId":"u3","invitedByUsername":"charlie","status":"PENDING","createdAt":0}]}"""
                    } else {
                        myInvitesJson
                    }
                    respond(body, status = HttpStatusCode.OK, headers = jsonHeaders)
                }
                else -> respond("{}", status = HttpStatusCode.OK, headers = jsonHeaders)
            }
        }) {
            install(ContentNegotiation) { json(AppJson) }
            install(HttpCookies)
            expectSuccess = true
        }
        val vm = MyInvitesViewModel(TeamApiClient(client))
        setContent {
            MaterialTheme {
                MyInvitesScreen(viewModel = vm, onBack = {}, onInviteAccepted = { acceptCalled.set(true) })
            }
        }

        waitUntil(timeoutMillis = 3000L) { onAllNodesWithText("Alpha Team", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("accept_invite_inv1").performClick()
        waitUntil(timeoutMillis = 5000L) { onAllNodesWithText("Alpha Team", useUnmergedTree = true).fetchSemanticsNodes().isEmpty() }
        onNodeWithText("Alpha Team", useUnmergedTree = true).assertDoesNotExist()
        onNodeWithText("Beta Squad", useUnmergedTree = true).assertExists()
        assertTrue(acceptCalled.get(), "onInviteAccepted callback should have been called")
    }

    // QA-INVITES-2: Decline opens inline confirmation with team name
    @Test
    fun `my invites decline opens inline confirmation with team name`() = runComposeUiTest {
        val vm = MyInvitesViewModel(TeamApiClient(myInvitesClient()))
        setContent {
            MaterialTheme {
                MyInvitesScreen(viewModel = vm, onBack = {})
            }
        }

        waitUntil(timeoutMillis = 3000L) { onAllNodesWithText("Alpha Team", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("decline_invite_inv1").performClick()
        waitUntil(timeoutMillis = 3000L) { onAllNodesWithTag("decline_invite_confirm_inv1").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("decline_invite_confirm_inv1").assertExists()
        // The confirmation message includes the team name
        onNodeWithText("Decline invite from \"Alpha Team\"?", substring = true, useUnmergedTree = true).assertExists()
    }

    // QA-INVITES-3: Confirming decline removes invite
    @Test
    fun `my invites confirming decline removes invite`() = runComposeUiTest {
        val declineCalled = AtomicBoolean(false)
        val returnEmpty = AtomicBoolean(false)
        val client = HttpClient(MockEngine { request ->
            val path = request.url.encodedPath
            val jsonHeaders = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            when {
                path.endsWith("/auth/me/invites/inv1/decline") -> {
                    declineCalled.set(true)
                    returnEmpty.set(true)
                    respond("{}", status = HttpStatusCode.OK, headers = jsonHeaders)
                }
                path.endsWith("/auth/me/invites") -> {
                    val body = if (returnEmpty.get()) {
                        """{"invites":[{"id":"inv2","teamId":"t2","teamName":"Beta Squad","invitedUserId":"me","invitedUsername":"me","invitedByUserId":"u3","invitedByUsername":"charlie","status":"PENDING","createdAt":0}]}"""
                    } else {
                        myInvitesJson
                    }
                    respond(body, status = HttpStatusCode.OK, headers = jsonHeaders)
                }
                else -> respond("{}", status = HttpStatusCode.OK, headers = jsonHeaders)
            }
        }) {
            install(ContentNegotiation) { json(AppJson) }
            install(HttpCookies)
            expectSuccess = true
        }
        val vm = MyInvitesViewModel(TeamApiClient(client))
        setContent {
            MaterialTheme {
                MyInvitesScreen(viewModel = vm, onBack = {})
            }
        }

        waitUntil(timeoutMillis = 3000L) { onAllNodesWithText("Alpha Team", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        // Click decline to open inline confirmation
        onNodeWithTag("decline_invite_inv1").performClick()
        waitUntil(timeoutMillis = 3000L) { onAllNodesWithTag("decline_invite_confirm_inv1").fetchSemanticsNodes().isNotEmpty() }
        // Click the confirm button inside the inline confirmation
        onNodeWithTag("decline_invite_confirm_inv1_confirm").performClick()
        waitUntil(timeoutMillis = 5000L) { onAllNodesWithText("Alpha Team", useUnmergedTree = true).fetchSemanticsNodes().isEmpty() }
        onNodeWithText("Alpha Team", useUnmergedTree = true).assertDoesNotExist()
        onNodeWithText("Beta Squad", useUnmergedTree = true).assertExists()
        assertTrue(declineCalled.get(), "declineInvite API should have been called")
    }

    // QA-INVITES-4: Initial load error shows snackbar with Retry
    @Test
    fun `my invites load error shows snackbar with retry`() = runComposeUiTest {
        val vm = MyInvitesViewModel(TeamApiClient(mockHttpClient(
            mapOf("/auth/me/invites" to json("{}", HttpStatusCode.InternalServerError))
        )))
        setContent {
            MaterialTheme {
                MyInvitesScreen(viewModel = vm, onBack = {})
            }
        }

        waitUntil(timeoutMillis = 3000L) { onAllNodesWithText("Retry").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("Retry").assertExists()
    }

    // QA-INVITES-5: Refresh button exists on my invites screen
    @Test
    fun `my invites has refresh button`() = runComposeUiTest {
        val vm = MyInvitesViewModel(TeamApiClient(myInvitesClient()))
        setContent {
            MaterialTheme {
                MyInvitesScreen(viewModel = vm, onBack = {})
            }
        }

        waitUntil(timeoutMillis = 3000L) { onAllNodesWithText("Alpha Team", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("refresh_button").assertExists()
    }

    // QA-INVITES-6: Refresh error shows banner
    @Test
    fun `my invites refresh error shows banner`() = runComposeUiTest {
        val failOnRefresh = AtomicBoolean(false)
        val client = HttpClient(MockEngine { request ->
            val path = request.url.encodedPath
            val jsonHeaders = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            when {
                path.endsWith("/auth/me/invites") -> {
                    if (!failOnRefresh.get()) {
                        respond(myInvitesJson, status = HttpStatusCode.OK, headers = jsonHeaders)
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
        val vm = MyInvitesViewModel(TeamApiClient(client))
        setContent {
            MaterialTheme {
                MyInvitesScreen(viewModel = vm, onBack = {})
            }
        }

        waitUntil(timeoutMillis = 3000L) { onAllNodesWithText("Alpha Team", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        failOnRefresh.set(true)
        onNodeWithTag("refresh_button").performClick()
        waitUntil(timeoutMillis = 5000L) { onAllNodesWithTag("refresh_error_banner").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("refresh_error_banner").assertExists()
        // Existing invites should still be visible
        onNodeWithText("Alpha Team", useUnmergedTree = true).assertExists()
    }

    // QA-INVITES-7: Back button triggers onBack
    @Test
    fun `my invites back button triggers callback`() = runComposeUiTest {
        val backClicked = AtomicBoolean(false)
        val vm = MyInvitesViewModel(TeamApiClient(myInvitesClient()))
        setContent {
            MaterialTheme {
                MyInvitesScreen(viewModel = vm, onBack = { backClicked.set(true) })
            }
        }

        waitUntil(timeoutMillis = 3000L) { onAllNodesWithTag("back_button").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("back_button").performClick()
        assertTrue(backClicked.get(), "onBack callback should have been called")
    }

    // QA-INVITES-8: Decline cancel dismisses confirmation
    @Test
    fun `my invites decline cancel dismisses confirmation`() = runComposeUiTest {
        val vm = MyInvitesViewModel(TeamApiClient(myInvitesClient()))
        setContent {
            MaterialTheme {
                MyInvitesScreen(viewModel = vm, onBack = {})
            }
        }

        waitUntil(timeoutMillis = 3000L) { onAllNodesWithText("Alpha Team", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("decline_invite_inv1").performClick()
        waitUntil(timeoutMillis = 3000L) { onAllNodesWithTag("decline_invite_confirm_inv1").fetchSemanticsNodes().isNotEmpty() }
        // Click cancel to dismiss
        onNodeWithTag("decline_invite_confirm_inv1_cancel").performClick()
        waitUntil(timeoutMillis = 3000L) { onAllNodesWithTag("decline_invite_confirm_inv1").fetchSemanticsNodes().isEmpty() }
        onNodeWithTag("decline_invite_confirm_inv1").assertDoesNotExist()
        // Invite should still be there
        onNodeWithText("Alpha Team", useUnmergedTree = true).assertExists()
    }

    // Team switcher and Teams button are now in the sidebar (AppShell),
    // not in ProjectListScreen. See SidebarNavigationTest for those tests.

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

        waitUntil(timeoutMillis = 3000L) { onAllNodesWithText("Alpha Team", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("Gamma New", useUnmergedTree = true).assertDoesNotExist()

        returnNewData.set(true)
        onNodeWithTag("refresh_button").performClick()

        waitUntil(timeoutMillis = 5000L) { onAllNodesWithText("Gamma New", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("Gamma New", useUnmergedTree = true).assertExists()
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

        waitUntil(timeoutMillis = 3000L) { onAllNodesWithText("Alpha Team", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        failOnRefresh.set(true)
        onNodeWithTag("refresh_button").performClick()
        waitUntil(timeoutMillis = 5000L) { onAllNodesWithTag("refresh_error_banner").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("refresh_error_banner").assertExists()
        onNodeWithText("Alpha Team", useUnmergedTree = true).assertExists()
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

        waitUntil(timeoutMillis = 3000L) { onAllNodesWithText("Alpha Team", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        failOnRefresh.set(true)
        onNodeWithTag("refresh_button").performClick()
        waitUntil(timeoutMillis = 5000L) { onAllNodesWithTag("refresh_error_banner").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("dismiss_refresh_error").performClick()
        waitUntil(timeoutMillis = 3000L) { onAllNodesWithTag("refresh_error_banner").fetchSemanticsNodes().isEmpty() }
        onNodeWithTag("refresh_error_banner").assertDoesNotExist()
    }

    // ---- TeamManageScreen QA Gap Tests (HIGH: QA-MANAGE-1 through QA-MANAGE-13, MEDIUM: QA-MANAGE-14 through QA-MANAGE-19) ----

    private val teamDetailJsonForManage = """{"team":{"id":"t1","name":"Alpha Team","description":"First team","createdAt":0},"members":[
        {"teamId":"t1","userId":"u1","username":"alice","role":"TEAM_LEAD","joinedAt":0},
        {"teamId":"t1","userId":"u2","username":"bob","role":"COLLABORATOR","joinedAt":0}
    ]}"""

    private val membersJsonForManage = """{"members":[
        {"teamId":"t1","userId":"u1","username":"alice","role":"TEAM_LEAD","joinedAt":0},
        {"teamId":"t1","userId":"u2","username":"bob","role":"COLLABORATOR","joinedAt":0}
    ]}"""

    private val invitesJsonForManage = """{"invites":[
        {"id":"inv1","teamId":"t1","teamName":"Alpha","invitedUserId":"u3","invitedUsername":"charlie","invitedByUserId":"u1","invitedByUsername":"alice","status":"PENDING","createdAt":0}
    ]}"""

    private val joinRequestsJsonForManage = """{"requests":[
        {"id":"jr1","teamId":"t1","teamName":"Alpha","userId":"u4","username":"dave","status":"PENDING","createdAt":0}
    ]}"""

    private fun teamManageEngineClient(
        inviteStatus: HttpStatusCode = HttpStatusCode.OK,
        inviteResponseBody: String = """{"id":"inv2","teamId":"t1","teamName":"Alpha","invitedUserId":"u5","invitedUsername":"newuser","invitedByUserId":"u1","invitedByUsername":"alice","status":"PENDING","createdAt":0}""",
        deleteTeamStatus: HttpStatusCode = HttpStatusCode.OK,
        removeMemberStatus: HttpStatusCode = HttpStatusCode.OK,
        cancelInviteStatus: HttpStatusCode = HttpStatusCode.OK,
        updateTeamStatus: HttpStatusCode = HttpStatusCode.OK,
        onInviteCalled: (() -> Unit)? = null,
        onRemoveMemberCalled: ((String) -> Unit)? = null,
        onCancelInviteCalled: ((String) -> Unit)? = null,
        onDeleteTeamCalled: (() -> Unit)? = null,
        onUpdateTeamCalled: (() -> Unit)? = null,
    ): HttpClient {
        return HttpClient(MockEngine { request ->
            val path = request.url.encodedPath
            val method = request.method
            val jsonHeaders = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            when {
                method == HttpMethod.Put && path == "/api/v1/teams/t1" -> {
                    onUpdateTeamCalled?.invoke()
                    respond(
                        """{"team":{"id":"t1","name":"Alpha Team","description":"First team","createdAt":0},"memberCount":2}""",
                        status = updateTeamStatus, headers = jsonHeaders,
                    )
                }
                method == HttpMethod.Delete && path == "/api/v1/teams/t1" -> {
                    onDeleteTeamCalled?.invoke()
                    respond("{}", status = deleteTeamStatus, headers = jsonHeaders)
                }
                method == HttpMethod.Get && path == "/api/v1/teams/t1" ->
                    respond(teamDetailJsonForManage, status = HttpStatusCode.OK, headers = jsonHeaders)
                method == HttpMethod.Delete && path.startsWith("/api/v1/teams/t1/members/") -> {
                    val userId = path.substringAfterLast("/")
                    onRemoveMemberCalled?.invoke(userId)
                    respond("{}", status = removeMemberStatus, headers = jsonHeaders)
                }
                method == HttpMethod.Put && path.startsWith("/api/v1/teams/t1/members/") ->
                    respond("{}", status = HttpStatusCode.OK, headers = jsonHeaders)
                method == HttpMethod.Get && path == "/api/v1/teams/t1/members" ->
                    respond(membersJsonForManage, status = HttpStatusCode.OK, headers = jsonHeaders)
                method == HttpMethod.Post && path == "/api/v1/teams/t1/invites" -> {
                    onInviteCalled?.invoke()
                    respond(inviteResponseBody, status = inviteStatus, headers = jsonHeaders)
                }
                method == HttpMethod.Delete && path.startsWith("/api/v1/teams/t1/invites/") -> {
                    val inviteId = path.substringAfterLast("/")
                    onCancelInviteCalled?.invoke(inviteId)
                    respond("{}", status = cancelInviteStatus, headers = jsonHeaders)
                }
                method == HttpMethod.Get && path == "/api/v1/teams/t1/invites" ->
                    respond(invitesJsonForManage, status = HttpStatusCode.OK, headers = jsonHeaders)
                path == "/api/v1/teams/t1/join-requests" && method == HttpMethod.Get ->
                    respond(joinRequestsJsonForManage, status = HttpStatusCode.OK, headers = jsonHeaders)
                path.contains("/join-requests/") && path.endsWith("/approve") ->
                    respond("{}", status = HttpStatusCode.OK, headers = jsonHeaders)
                path.contains("/join-requests/") && path.endsWith("/reject") ->
                    respond("{}", status = HttpStatusCode.OK, headers = jsonHeaders)
                else -> respond("{}", status = HttpStatusCode.OK, headers = jsonHeaders)
            }
        }) {
            install(ContentNegotiation) { json(AppJson) }
            install(HttpCookies)
            expectSuccess = true
        }
    }

    // QA-MANAGE-1: Save button enabled only when dirty

    @Test
    fun `manage save button disabled when no changes made`() = runComposeUiTest {
        val vm = TeamManageViewModel(TeamId("t1"), TeamApiClient(teamManageEngineClient()))
        setContent { MaterialTheme { TeamManageScreen(viewModel = vm, onBack = {}) } }

        waitUntil(timeoutMillis = 3000L) { onAllNodesWithTag("save_team_button").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("save_team_button").assertIsNotEnabled()
    }

    @Test
    fun `manage save button enabled after editing name`() = runComposeUiTest {
        val vm = TeamManageViewModel(TeamId("t1"), TeamApiClient(teamManageEngineClient()))
        setContent { MaterialTheme { TeamManageScreen(viewModel = vm, onBack = {}) } }

        waitUntil(timeoutMillis = 3000L) { onAllNodesWithTag("edit_team_name").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("save_team_button").assertIsNotEnabled()
        onNodeWithTag("edit_team_name").performTextInput(" Modified")
        onNodeWithTag("save_team_button").assertIsEnabled()
    }

    @Test
    fun `manage save button enabled after editing description`() = runComposeUiTest {
        val vm = TeamManageViewModel(TeamId("t1"), TeamApiClient(teamManageEngineClient()))
        setContent { MaterialTheme { TeamManageScreen(viewModel = vm, onBack = {}) } }

        waitUntil(timeoutMillis = 3000L) { onAllNodesWithTag("edit_team_description").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("save_team_button").assertIsNotEnabled()
        onNodeWithTag("edit_team_description").performTextInput(" extra info")
        onNodeWithTag("save_team_button").assertIsEnabled()
    }

    @Test
    fun `manage save button disabled when name cleared to blank`() = runComposeUiTest {
        val vm = TeamManageViewModel(TeamId("t1"), TeamApiClient(teamManageEngineClient()))
        setContent { MaterialTheme { TeamManageScreen(viewModel = vm, onBack = {}) } }

        waitUntil(timeoutMillis = 3000L) { onAllNodesWithTag("edit_team_name").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("edit_team_name").performTextClearance()
        onNodeWithTag("save_team_button").assertIsNotEnabled()
    }

    // QA-MANAGE-2: Save button disabled when saving in progress / after successful save

    @Test
    fun `manage save button disabled after successful save`() = runComposeUiTest {
        val updateCalled = AtomicBoolean(false)
        val client = teamManageEngineClient(onUpdateTeamCalled = { updateCalled.set(true) })
        val vm = TeamManageViewModel(TeamId("t1"), TeamApiClient(client))
        setContent { MaterialTheme { TeamManageScreen(viewModel = vm, onBack = {}) } }

        waitUntil(timeoutMillis = 3000L) { onAllNodesWithTag("edit_team_description").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("edit_team_description").performTextInput(" updated")
        onNodeWithTag("save_team_button").assertIsEnabled()
        onNodeWithTag("save_team_button").performClick()

        waitUntil(timeoutMillis = 5000L) { updateCalled.get() }
        waitUntil(timeoutMillis = 5000L) {
            onAllNodesWithTag("save_team_button").fetchSemanticsNodes().firstOrNull()
                ?.config?.getOrElseNullable(SemanticsProperties.Disabled) { null } != null
        }
        onNodeWithTag("save_team_button").assertIsNotEnabled()
    }

    // QA-MANAGE-3: Unsaved changes confirmation on Back when dirty

    @Test
    fun `manage back with unsaved changes shows discard confirmation`() = runComposeUiTest {
        val backCalled = AtomicBoolean(false)
        val vm = TeamManageViewModel(TeamId("t1"), TeamApiClient(teamManageEngineClient()))
        setContent { MaterialTheme { TeamManageScreen(viewModel = vm, onBack = { backCalled.set(true) }) } }

        waitUntil(timeoutMillis = 3000L) { onAllNodesWithTag("edit_team_name").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("edit_team_name").performTextInput(" changed")
        onNodeWithTag("back_button").performClick()

        waitUntil(timeoutMillis = 3000L) { onAllNodesWithTag("discard_changes_confirm").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("discard_changes_confirm").assertExists()
        assertTrue(!backCalled.get(), "onBack should not be called before confirming discard")
    }

    @Test
    fun `manage back without unsaved changes navigates directly`() = runComposeUiTest {
        val backCalled = AtomicBoolean(false)
        val vm = TeamManageViewModel(TeamId("t1"), TeamApiClient(teamManageEngineClient()))
        setContent { MaterialTheme { TeamManageScreen(viewModel = vm, onBack = { backCalled.set(true) }) } }

        waitUntil(timeoutMillis = 3000L) { onAllNodesWithTag("back_button").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("back_button").performClick()
        waitUntil(timeoutMillis = 3000L) { backCalled.get() }
        assertTrue(backCalled.get(), "onBack should be called directly when no changes")
    }

    // QA-MANAGE-4: Discard navigates, Cancel stays

    @Test
    fun `manage discard confirmation discard navigates back`() = runComposeUiTest {
        val backCalled = AtomicBoolean(false)
        val vm = TeamManageViewModel(TeamId("t1"), TeamApiClient(teamManageEngineClient()))
        setContent { MaterialTheme { TeamManageScreen(viewModel = vm, onBack = { backCalled.set(true) }) } }

        waitUntil(timeoutMillis = 3000L) { onAllNodesWithTag("edit_team_name").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("edit_team_name").performTextInput(" changed")
        onNodeWithTag("back_button").performClick()

        waitUntil(timeoutMillis = 3000L) { onAllNodesWithTag("discard_changes_confirm").fetchSemanticsNodes().isNotEmpty() }
        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("discard_changes_confirm_confirm").fetchSemanticsNodes().firstOrNull()
                ?.config?.getOrElseNullable(SemanticsProperties.Disabled) { null } == null
        }
        onNodeWithTag("discard_changes_confirm_confirm").performClick()

        waitUntil(timeoutMillis = 3000L) { backCalled.get() }
        assertTrue(backCalled.get(), "onBack should be called after discard")
    }

    @Test
    fun `manage discard confirmation cancel stays on screen`() = runComposeUiTest {
        val backCalled = AtomicBoolean(false)
        val vm = TeamManageViewModel(TeamId("t1"), TeamApiClient(teamManageEngineClient()))
        setContent { MaterialTheme { TeamManageScreen(viewModel = vm, onBack = { backCalled.set(true) }) } }

        waitUntil(timeoutMillis = 3000L) { onAllNodesWithTag("edit_team_name").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("edit_team_name").performTextInput(" changed")
        onNodeWithTag("back_button").performClick()

        waitUntil(timeoutMillis = 3000L) { onAllNodesWithTag("discard_changes_confirm").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("discard_changes_confirm_cancel").performClick()

        waitUntil(timeoutMillis = 3000L) { onAllNodesWithTag("discard_changes_confirm").fetchSemanticsNodes().isEmpty() }
        onNodeWithTag("discard_changes_confirm").assertDoesNotExist()
        onNodeWithTag("team_manage_screen").assertExists()
        assertTrue(!backCalled.get(), "onBack should not be called after cancel")
    }

    // QA-MANAGE-5: Invite form field validation (error state from API)

    @Test
    fun `manage invite form shows error on invalid username`() = runComposeUiTest {
        val client = teamManageEngineClient(
            inviteStatus = HttpStatusCode.BadRequest,
            inviteResponseBody = """{"error":"User not found"}""",
        )
        val vm = TeamManageViewModel(TeamId("t1"), TeamApiClient(client))
        setContent { MaterialTheme { TeamManageScreen(viewModel = vm, onBack = {}) } }

        waitUntil(timeoutMillis = 3000L) { onAllNodesWithTag("invite_user_button").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("invite_user_button").performClick()
        waitUntil(timeoutMillis = 3000L) { onAllNodesWithTag("invite_user_id_input").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("invite_user_id_input").performTextInput("nonexistent")
        onNodeWithTag("invite_user_confirm").performClick()

        waitUntil(timeoutMillis = 5000L) { onAllNodesWithTag("invite_error_text").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("invite_error_text").assertExists()
    }

    // QA-MANAGE-6: Invite form submit disabled when username blank

    @Test
    fun `manage invite confirm button disabled when username blank`() = runComposeUiTest {
        val vm = TeamManageViewModel(TeamId("t1"), TeamApiClient(teamManageEngineClient()))
        setContent { MaterialTheme { TeamManageScreen(viewModel = vm, onBack = {}) } }

        waitUntil(timeoutMillis = 3000L) { onAllNodesWithTag("invite_user_button").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("invite_user_button").performClick()
        waitUntil(timeoutMillis = 3000L) { onAllNodesWithTag("invite_user_confirm").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("invite_user_confirm").assertIsNotEnabled()
    }

    @Test
    fun `manage invite confirm button enabled after entering username`() = runComposeUiTest {
        val vm = TeamManageViewModel(TeamId("t1"), TeamApiClient(teamManageEngineClient()))
        setContent { MaterialTheme { TeamManageScreen(viewModel = vm, onBack = {}) } }

        waitUntil(timeoutMillis = 3000L) { onAllNodesWithTag("invite_user_button").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("invite_user_button").performClick()
        waitUntil(timeoutMillis = 3000L) { onAllNodesWithTag("invite_user_id_input").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("invite_user_confirm").assertIsNotEnabled()
        onNodeWithTag("invite_user_id_input").performTextInput("newuser")
        onNodeWithTag("invite_user_confirm").assertIsEnabled()
    }

    // QA-MANAGE-7: Invite form submit calls inviteMember

    @Test
    fun `manage invite form submit calls invite API`() = runComposeUiTest {
        val inviteCalled = AtomicBoolean(false)
        val client = teamManageEngineClient(onInviteCalled = { inviteCalled.set(true) })
        val vm = TeamManageViewModel(TeamId("t1"), TeamApiClient(client))
        setContent { MaterialTheme { TeamManageScreen(viewModel = vm, onBack = {}) } }

        waitUntil(timeoutMillis = 3000L) { onAllNodesWithTag("invite_user_button").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("invite_user_button").performClick()
        waitUntil(timeoutMillis = 3000L) { onAllNodesWithTag("invite_user_id_input").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("invite_user_id_input").performTextInput("newuser")
        onNodeWithTag("invite_user_confirm").performClick()

        waitUntil(timeoutMillis = 5000L) { inviteCalled.get() }
        assertTrue(inviteCalled.get(), "Invite API should have been called")
    }

    // QA-MANAGE-8: Invite form auto-closes on success

    @Test
    fun `manage invite form closes on successful invite`() = runComposeUiTest {
        val vm = TeamManageViewModel(TeamId("t1"), TeamApiClient(teamManageEngineClient()))
        setContent { MaterialTheme { TeamManageScreen(viewModel = vm, onBack = {}) } }

        waitUntil(timeoutMillis = 3000L) { onAllNodesWithTag("invite_user_button").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("invite_user_button").performClick()
        waitUntil(timeoutMillis = 3000L) { onAllNodesWithTag("invite_user_id_input").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("invite_user_id_input").performTextInput("newuser")
        onNodeWithTag("invite_user_confirm").performClick()

        waitUntil(timeoutMillis = 5000L) { onAllNodesWithTag("invite_user_form").fetchSemanticsNodes().isEmpty() }
        onNodeWithTag("invite_user_form").assertDoesNotExist()
    }

    // QA-MANAGE-9: Invite form shows error on failure

    @Test
    fun `manage invite form shows error on API failure`() = runComposeUiTest {
        val client = teamManageEngineClient(
            inviteStatus = HttpStatusCode.Conflict,
            inviteResponseBody = """{"error":"User already invited"}""",
        )
        val vm = TeamManageViewModel(TeamId("t1"), TeamApiClient(client))
        setContent { MaterialTheme { TeamManageScreen(viewModel = vm, onBack = {}) } }

        waitUntil(timeoutMillis = 3000L) { onAllNodesWithTag("invite_user_button").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("invite_user_button").performClick()
        waitUntil(timeoutMillis = 3000L) { onAllNodesWithTag("invite_user_id_input").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("invite_user_id_input").performTextInput("charlie")
        onNodeWithTag("invite_user_confirm").performClick()

        waitUntil(timeoutMillis = 5000L) { onAllNodesWithTag("invite_error_text").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("invite_error_text").assertExists()
        onNodeWithTag("invite_user_form").assertExists()
    }

    // QA-MANAGE-10: Remove member inline confirmation + callback

    @Test
    fun `manage remove member shows inline confirmation`() = runComposeUiTest {
        val vm = TeamManageViewModel(TeamId("t1"), TeamApiClient(teamManageEngineClient()))
        setContent { MaterialTheme { TeamManageScreen(viewModel = vm, onBack = {}) } }

        waitUntil(timeoutMillis = 3000L) { onAllNodesWithText("bob", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("remove_member_u2", useUnmergedTree = true).performClick()

        waitUntil(timeoutMillis = 3000L) { onAllNodesWithTag("remove_member_confirm_u2").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("remove_member_confirm_u2").assertExists()
    }

    @Test
    fun `manage remove member confirmation triggers API call`() = runComposeUiTest {
        val removedUserId = AtomicReference<String?>(null)
        val client = teamManageEngineClient(onRemoveMemberCalled = { uid -> removedUserId.set(uid) })
        val vm = TeamManageViewModel(TeamId("t1"), TeamApiClient(client))
        setContent { MaterialTheme { TeamManageScreen(viewModel = vm, onBack = {}) } }

        waitUntil(timeoutMillis = 3000L) { onAllNodesWithText("bob", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("remove_member_u2", useUnmergedTree = true).performClick()
        waitUntil(timeoutMillis = 3000L) { onAllNodesWithTag("remove_member_confirm_u2").fetchSemanticsNodes().isNotEmpty() }
        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("remove_member_confirm_u2_confirm").fetchSemanticsNodes().firstOrNull()
                ?.config?.getOrElseNullable(SemanticsProperties.Disabled) { null } == null
        }
        onNodeWithTag("remove_member_confirm_u2_confirm").performClick()

        waitUntil(timeoutMillis = 5000L) { removedUserId.get() != null }
        assertEquals("u2", removedUserId.get())
    }

    @Test
    fun `manage remove member confirmation cancel hides confirmation`() = runComposeUiTest {
        val vm = TeamManageViewModel(TeamId("t1"), TeamApiClient(teamManageEngineClient()))
        setContent { MaterialTheme { TeamManageScreen(viewModel = vm, onBack = {}) } }

        waitUntil(timeoutMillis = 3000L) { onAllNodesWithText("bob", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("remove_member_u2", useUnmergedTree = true).performClick()
        waitUntil(timeoutMillis = 3000L) { onAllNodesWithTag("remove_member_confirm_u2").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("remove_member_confirm_u2_cancel").performClick()

        waitUntil(timeoutMillis = 3000L) { onAllNodesWithTag("remove_member_confirm_u2").fetchSemanticsNodes().isEmpty() }
        onNodeWithTag("remove_member_confirm_u2").assertDoesNotExist()
        onNodeWithText("bob", useUnmergedTree = true).assertExists()
    }

    // QA-MANAGE-11: Revoke invite inline confirmation + callback

    @Test
    fun `manage revoke invite shows inline confirmation`() = runComposeUiTest {
        val vm = TeamManageViewModel(TeamId("t1"), TeamApiClient(teamManageEngineClient()))
        setContent { MaterialTheme { TeamManageScreen(viewModel = vm, onBack = {}) } }

        waitUntil(timeoutMillis = 3000L) { onAllNodesWithText("charlie", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("revoke_invite_btn_inv1", useUnmergedTree = true).performClick()

        waitUntil(timeoutMillis = 3000L) { onAllNodesWithTag("revoke_invite_confirm_inv1").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("revoke_invite_confirm_inv1").assertExists()
    }

    @Test
    fun `manage revoke invite confirmation triggers API call`() = runComposeUiTest {
        val revokedInviteId = AtomicReference<String?>(null)
        val client = teamManageEngineClient(onCancelInviteCalled = { id -> revokedInviteId.set(id) })
        val vm = TeamManageViewModel(TeamId("t1"), TeamApiClient(client))
        setContent { MaterialTheme { TeamManageScreen(viewModel = vm, onBack = {}) } }

        waitUntil(timeoutMillis = 3000L) { onAllNodesWithText("charlie", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("revoke_invite_btn_inv1", useUnmergedTree = true).performClick()
        waitUntil(timeoutMillis = 3000L) { onAllNodesWithTag("revoke_invite_confirm_inv1").fetchSemanticsNodes().isNotEmpty() }
        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("revoke_invite_confirm_inv1_confirm").fetchSemanticsNodes().firstOrNull()
                ?.config?.getOrElseNullable(SemanticsProperties.Disabled) { null } == null
        }
        onNodeWithTag("revoke_invite_confirm_inv1_confirm").performClick()

        waitUntil(timeoutMillis = 5000L) { revokedInviteId.get() != null }
        assertEquals("inv1", revokedInviteId.get())
    }

    @Test
    fun `manage revoke invite confirmation cancel hides confirmation`() = runComposeUiTest {
        val vm = TeamManageViewModel(TeamId("t1"), TeamApiClient(teamManageEngineClient()))
        setContent { MaterialTheme { TeamManageScreen(viewModel = vm, onBack = {}) } }

        waitUntil(timeoutMillis = 3000L) { onAllNodesWithText("charlie", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("revoke_invite_btn_inv1", useUnmergedTree = true).performClick()
        waitUntil(timeoutMillis = 3000L) { onAllNodesWithTag("revoke_invite_confirm_inv1").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("revoke_invite_confirm_inv1_cancel").performClick()

        waitUntil(timeoutMillis = 3000L) { onAllNodesWithTag("revoke_invite_confirm_inv1").fetchSemanticsNodes().isEmpty() }
        onNodeWithTag("revoke_invite_confirm_inv1").assertDoesNotExist()
        onNodeWithText("charlie", useUnmergedTree = true).assertExists()
    }

    // QA-MANAGE-12: Delete team inline confirmation + callback

    @Test
    fun `manage delete team shows inline confirmation`() = runComposeUiTest {
        val vm = TeamManageViewModel(TeamId("t1"), TeamApiClient(teamManageEngineClient()))
        setContent { MaterialTheme { TeamManageScreen(viewModel = vm, onBack = {}) } }

        waitUntil(timeoutMillis = 3000L) { onAllNodesWithTag("delete_team_button").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("delete_team_button").performClick()

        waitUntil(timeoutMillis = 3000L) { onAllNodesWithTag("delete_team_confirm").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("delete_team_confirm").assertExists()
    }

    @Test
    fun `manage delete team confirmation triggers API and onTeamDeleted`() = runComposeUiTest {
        val deleteCalled = AtomicBoolean(false)
        val teamDeleted = AtomicBoolean(false)
        val client = teamManageEngineClient(onDeleteTeamCalled = { deleteCalled.set(true) })
        val vm = TeamManageViewModel(TeamId("t1"), TeamApiClient(client))
        setContent {
            MaterialTheme {
                TeamManageScreen(viewModel = vm, onBack = {}, onTeamDeleted = { teamDeleted.set(true) })
            }
        }

        waitUntil(timeoutMillis = 3000L) { onAllNodesWithTag("delete_team_button").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("delete_team_button").performClick()
        waitUntil(timeoutMillis = 3000L) { onAllNodesWithTag("delete_team_confirm").fetchSemanticsNodes().isNotEmpty() }
        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("delete_team_confirm_confirm").fetchSemanticsNodes().firstOrNull()
                ?.config?.getOrElseNullable(SemanticsProperties.Disabled) { null } == null
        }
        onNodeWithTag("delete_team_confirm_confirm").performClick()

        waitUntil(timeoutMillis = 5000L) { deleteCalled.get() }
        assertTrue(deleteCalled.get(), "Delete API should have been called")
        waitUntil(timeoutMillis = 5000L) { teamDeleted.get() }
        assertTrue(teamDeleted.get(), "onTeamDeleted callback should have been invoked")
    }

    @Test
    fun `manage delete team confirmation cancel hides confirmation`() = runComposeUiTest {
        val vm = TeamManageViewModel(TeamId("t1"), TeamApiClient(teamManageEngineClient()))
        setContent { MaterialTheme { TeamManageScreen(viewModel = vm, onBack = {}) } }

        waitUntil(timeoutMillis = 3000L) { onAllNodesWithTag("delete_team_button").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("delete_team_button").performClick()
        waitUntil(timeoutMillis = 3000L) { onAllNodesWithTag("delete_team_confirm").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("delete_team_confirm_cancel").performClick()

        waitUntil(timeoutMillis = 3000L) { onAllNodesWithTag("delete_team_confirm").fetchSemanticsNodes().isEmpty() }
        onNodeWithTag("delete_team_confirm").assertDoesNotExist()
        onNodeWithTag("team_manage_screen").assertExists()
    }

    // QA-MANAGE-13: "You" badge for current user

    @Test
    fun `manage shows You badge for current user`() = runComposeUiTest {
        val vm = TeamManageViewModel(TeamId("t1"), TeamApiClient(teamManageEngineClient()))
        setContent { MaterialTheme { TeamManageScreen(viewModel = vm, onBack = {}, currentUserId = "u1") } }

        waitUntil(timeoutMillis = 3000L) { onAllNodesWithText("alice", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("you_badge_u1", useUnmergedTree = true).assertExists()
        onAllNodesWithTag("you_badge_u2", useUnmergedTree = true).assertCountEquals(0)
    }

    @Test
    fun `manage You badge hides role toggle and remove for current user`() = runComposeUiTest {
        val vm = TeamManageViewModel(TeamId("t1"), TeamApiClient(teamManageEngineClient()))
        setContent { MaterialTheme { TeamManageScreen(viewModel = vm, onBack = {}, currentUserId = "u1") } }

        waitUntil(timeoutMillis = 3000L) { onAllNodesWithText("alice", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("you_badge_u1", useUnmergedTree = true).assertExists()
        onAllNodesWithTag("toggle_role_u1", useUnmergedTree = true).assertCountEquals(0)
        onAllNodesWithTag("remove_member_u1", useUnmergedTree = true).assertCountEquals(0)
        onNodeWithTag("toggle_role_u2", useUnmergedTree = true).assertExists()
        onNodeWithTag("remove_member_u2", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `manage no You badge when currentUserId is null`() = runComposeUiTest {
        val vm = TeamManageViewModel(TeamId("t1"), TeamApiClient(teamManageEngineClient()))
        setContent { MaterialTheme { TeamManageScreen(viewModel = vm, onBack = {}) } }

        waitUntil(timeoutMillis = 3000L) { onAllNodesWithText("alice", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        onAllNodesWithTag("you_badge_u1", useUnmergedTree = true).assertCountEquals(0)
        onAllNodesWithTag("you_badge_u2", useUnmergedTree = true).assertCountEquals(0)
        onNodeWithTag("toggle_role_u1", useUnmergedTree = true).assertExists()
        onNodeWithTag("toggle_role_u2", useUnmergedTree = true).assertExists()
        onNodeWithTag("remove_member_u1", useUnmergedTree = true).assertExists()
        onNodeWithTag("remove_member_u2", useUnmergedTree = true).assertExists()
    }

    // ---- MEDIUM priority gaps (QA-MANAGE-14 through QA-MANAGE-19) ----

    // QA-MANAGE-14: Role toggle calls updateMemberRole

    @Test
    fun `manage promote member triggers role update API`() = runComposeUiTest {
        val updateRoleCalled = AtomicBoolean(false)
        val client = HttpClient(MockEngine { request ->
            val path = request.url.encodedPath
            val method = request.method
            val jsonHeaders = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            when {
                method == HttpMethod.Put && path.startsWith("/api/v1/teams/t1/members/") -> {
                    updateRoleCalled.set(true)
                    respond("{}", status = HttpStatusCode.OK, headers = jsonHeaders)
                }
                method == HttpMethod.Get && path == "/api/v1/teams/t1" ->
                    respond(teamDetailJsonForManage, status = HttpStatusCode.OK, headers = jsonHeaders)
                method == HttpMethod.Get && path == "/api/v1/teams/t1/members" ->
                    respond(membersJsonForManage, status = HttpStatusCode.OK, headers = jsonHeaders)
                method == HttpMethod.Get && path == "/api/v1/teams/t1/invites" ->
                    respond(invitesJsonForManage, status = HttpStatusCode.OK, headers = jsonHeaders)
                method == HttpMethod.Get && path == "/api/v1/teams/t1/join-requests" ->
                    respond(joinRequestsJsonForManage, status = HttpStatusCode.OK, headers = jsonHeaders)
                else -> respond("{}", status = HttpStatusCode.OK, headers = jsonHeaders)
            }
        }) {
            install(ContentNegotiation) { json(AppJson) }
            install(HttpCookies)
            expectSuccess = true
        }
        val vm = TeamManageViewModel(TeamId("t1"), TeamApiClient(client))
        setContent { MaterialTheme { TeamManageScreen(viewModel = vm, onBack = {}) } }

        waitUntil(timeoutMillis = 3000L) { onAllNodesWithText("bob", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("toggle_role_u2", useUnmergedTree = true).performClick()

        waitUntil(timeoutMillis = 5000L) { updateRoleCalled.get() }
        assertTrue(updateRoleCalled.get(), "Role update API should have been called")
    }

    // QA-MANAGE-15: Approve join request calls API

    @Test
    fun `manage approve join request calls API`() = runComposeUiTest {
        val approveCalled = AtomicBoolean(false)
        val client = HttpClient(MockEngine { request ->
            val path = request.url.encodedPath
            val method = request.method
            val jsonHeaders = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            when {
                method == HttpMethod.Post && path.contains("/join-requests/") && path.endsWith("/approve") -> {
                    approveCalled.set(true)
                    respond("{}", status = HttpStatusCode.OK, headers = jsonHeaders)
                }
                method == HttpMethod.Get && path == "/api/v1/teams/t1" ->
                    respond(teamDetailJsonForManage, status = HttpStatusCode.OK, headers = jsonHeaders)
                method == HttpMethod.Get && path == "/api/v1/teams/t1/members" ->
                    respond(membersJsonForManage, status = HttpStatusCode.OK, headers = jsonHeaders)
                method == HttpMethod.Get && path == "/api/v1/teams/t1/invites" ->
                    respond(invitesJsonForManage, status = HttpStatusCode.OK, headers = jsonHeaders)
                method == HttpMethod.Get && path == "/api/v1/teams/t1/join-requests" ->
                    respond(joinRequestsJsonForManage, status = HttpStatusCode.OK, headers = jsonHeaders)
                else -> respond("{}", status = HttpStatusCode.OK, headers = jsonHeaders)
            }
        }) {
            install(ContentNegotiation) { json(AppJson) }
            install(HttpCookies)
            expectSuccess = true
        }
        val vm = TeamManageViewModel(TeamId("t1"), TeamApiClient(client))
        setContent { MaterialTheme { TeamManageScreen(viewModel = vm, onBack = {}) } }

        waitUntil(timeoutMillis = 3000L) { onAllNodesWithText("dave", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("approve_request_jr1", useUnmergedTree = true).performClick()

        waitUntil(timeoutMillis = 5000L) { approveCalled.get() }
        assertTrue(approveCalled.get(), "Approve join request API should have been called")
    }

    // QA-MANAGE-16: Reject join request calls API

    @Test
    fun `manage reject join request calls API`() = runComposeUiTest {
        val rejectCalled = AtomicBoolean(false)
        val client = HttpClient(MockEngine { request ->
            val path = request.url.encodedPath
            val method = request.method
            val jsonHeaders = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            when {
                method == HttpMethod.Post && path.contains("/join-requests/") && path.endsWith("/reject") -> {
                    rejectCalled.set(true)
                    respond("{}", status = HttpStatusCode.OK, headers = jsonHeaders)
                }
                method == HttpMethod.Get && path == "/api/v1/teams/t1" ->
                    respond(teamDetailJsonForManage, status = HttpStatusCode.OK, headers = jsonHeaders)
                method == HttpMethod.Get && path == "/api/v1/teams/t1/members" ->
                    respond(membersJsonForManage, status = HttpStatusCode.OK, headers = jsonHeaders)
                method == HttpMethod.Get && path == "/api/v1/teams/t1/invites" ->
                    respond(invitesJsonForManage, status = HttpStatusCode.OK, headers = jsonHeaders)
                method == HttpMethod.Get && path == "/api/v1/teams/t1/join-requests" ->
                    respond(joinRequestsJsonForManage, status = HttpStatusCode.OK, headers = jsonHeaders)
                else -> respond("{}", status = HttpStatusCode.OK, headers = jsonHeaders)
            }
        }) {
            install(ContentNegotiation) { json(AppJson) }
            install(HttpCookies)
            expectSuccess = true
        }
        val vm = TeamManageViewModel(TeamId("t1"), TeamApiClient(client))
        setContent { MaterialTheme { TeamManageScreen(viewModel = vm, onBack = {}) } }

        waitUntil(timeoutMillis = 3000L) { onAllNodesWithText("dave", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("reject_request_jr1", useUnmergedTree = true).performClick()

        waitUntil(timeoutMillis = 5000L) { rejectCalled.get() }
        assertTrue(rejectCalled.get(), "Reject join request API should have been called")
    }

    // QA-MANAGE-17: Empty invites and join requests show zero count

    @Test
    fun `manage shows zero count when no invites or join requests`() = runComposeUiTest {
        val client = HttpClient(MockEngine { request ->
            val path = request.url.encodedPath
            val jsonHeaders = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            when {
                path == "/api/v1/teams/t1" ->
                    respond(teamDetailJsonForManage, status = HttpStatusCode.OK, headers = jsonHeaders)
                path == "/api/v1/teams/t1/members" ->
                    respond(membersJsonForManage, status = HttpStatusCode.OK, headers = jsonHeaders)
                path == "/api/v1/teams/t1/invites" ->
                    respond("""{"invites":[]}""", status = HttpStatusCode.OK, headers = jsonHeaders)
                path == "/api/v1/teams/t1/join-requests" ->
                    respond("""{"requests":[]}""", status = HttpStatusCode.OK, headers = jsonHeaders)
                else -> respond("{}", status = HttpStatusCode.OK, headers = jsonHeaders)
            }
        }) {
            install(ContentNegotiation) { json(AppJson) }
            install(HttpCookies)
            expectSuccess = true
        }
        val vm = TeamManageViewModel(TeamId("t1"), TeamApiClient(client))
        setContent { MaterialTheme { TeamManageScreen(viewModel = vm, onBack = {}) } }

        waitUntil(timeoutMillis = 3000L) { onAllNodesWithText("alice", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("Pending Invites (0)", useUnmergedTree = true).assertExists()
        onNodeWithText("Join Requests (0)", useUnmergedTree = true).assertExists()
    }

    // QA-MANAGE-18: Invite form close button dismisses form

    @Test
    fun `manage invite form close button dismisses form`() = runComposeUiTest {
        val vm = TeamManageViewModel(TeamId("t1"), TeamApiClient(teamManageEngineClient()))
        setContent { MaterialTheme { TeamManageScreen(viewModel = vm, onBack = {}) } }

        waitUntil(timeoutMillis = 3000L) { onAllNodesWithTag("invite_user_button").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("invite_user_button").performClick()
        waitUntil(timeoutMillis = 3000L) { onAllNodesWithTag("invite_user_form").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("invite_user_form").assertExists()
        onNodeWithTag("invite_user_form_close").performClick()

        waitUntil(timeoutMillis = 3000L) { onAllNodesWithTag("invite_user_form").fetchSemanticsNodes().isEmpty() }
        onNodeWithTag("invite_user_form").assertDoesNotExist()
    }

    // QA-MANAGE-19: Invite form resets username field on reopen

    @Test
    fun `manage invite form resets username field on reopen`() = runComposeUiTest {
        val vm = TeamManageViewModel(TeamId("t1"), TeamApiClient(teamManageEngineClient()))
        setContent { MaterialTheme { TeamManageScreen(viewModel = vm, onBack = {}) } }

        waitUntil(timeoutMillis = 3000L) { onAllNodesWithTag("invite_user_button").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("invite_user_button").performClick()
        waitUntil(timeoutMillis = 3000L) { onAllNodesWithTag("invite_user_id_input").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("invite_user_id_input").performTextInput("someuser")

        onNodeWithTag("invite_user_form_close").performClick()
        waitUntil(timeoutMillis = 3000L) { onAllNodesWithTag("invite_user_form").fetchSemanticsNodes().isEmpty() }

        onNodeWithTag("invite_user_button").performClick()
        waitUntil(timeoutMillis = 3000L) { onAllNodesWithTag("invite_user_id_input").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("invite_user_confirm").assertIsNotEnabled()
    }
}
