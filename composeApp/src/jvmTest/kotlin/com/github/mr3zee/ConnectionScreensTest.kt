package com.github.mr3zee

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import com.github.mr3zee.api.ConnectionApiClient
import com.github.mr3zee.connections.ConnectionFormScreen
import com.github.mr3zee.connections.ConnectionListScreen
import com.github.mr3zee.connections.ConnectionsViewModel
import com.github.mr3zee.model.ConnectionId
import com.github.mr3zee.model.TeamId
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.cookies.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class ConnectionScreensTest {

    private val connectionsJson = """{"connections":[
        {"id":"c1","name":"My GitHub","type":"GITHUB","config":{"type":"github","token":"****token","owner":"mr3zee","repo":"release-wizard"},"createdAt":"2026-03-13T00:00:00Z","updatedAt":"2026-03-13T00:00:00Z"},
        {"id":"c2","name":"Team Slack","type":"SLACK","config":{"type":"slack","webhookUrl":"https://hooks.slack.com/test"},"createdAt":"2026-03-13T00:00:00Z","updatedAt":"2026-03-13T00:00:00Z"}
    ]}"""

    private fun connClient(json: String = connectionsJson, status: HttpStatusCode = HttpStatusCode.OK) = mockHttpClient(
        mapOf("/connections" to json(json, status))
    )

    @Test
    fun `connection list shows connections`() = runComposeUiTest {
        val vm = ConnectionsViewModel(ConnectionApiClient(connClient()), MutableStateFlow(TeamId("test-team")))
        setContent {
            MaterialTheme { ConnectionListScreen(viewModel = vm, onCreateConnection = {}, onEditConnection = {}, onBack = {}) }
        }

        waitUntil(timeoutMillis = 3000L) { onAllNodesWithText("My GitHub").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("connection_list_screen").assertExists()
        onNodeWithText("My GitHub").assertExists()
        onNodeWithTag("connection_item_c1", useUnmergedTree = true).assertExists()
        onNodeWithText("Team Slack").assertExists()
        onNodeWithTag("connection_item_c2", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `connection list shows empty state`() = runComposeUiTest {
        val vm = ConnectionsViewModel(ConnectionApiClient(connClient("""{"connections":[]}""")), MutableStateFlow(TeamId("test-team")))
        setContent {
            MaterialTheme { ConnectionListScreen(viewModel = vm, onCreateConnection = {}, onEditConnection = {}, onBack = {}) }
        }

        waitUntil(timeoutMillis = 3000L) { onAllNodesWithText("No connections yet", substring = true).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("No connections yet. Add one to get started.").assertExists()
    }

    @Test
    fun `connection list has create fab and back button`() = runComposeUiTest {
        val vm = ConnectionsViewModel(ConnectionApiClient(connClient("""{"connections":[]}""")), MutableStateFlow(TeamId("test-team")))
        setContent {
            MaterialTheme { ConnectionListScreen(viewModel = vm, onCreateConnection = {}, onEditConnection = {}, onBack = {}) }
        }

        onNodeWithTag("create_connection_fab").assertExists()
        onNodeWithText("Back").assertExists()
    }

    @Test
    fun `connection items have test and delete buttons`() = runComposeUiTest {
        val vm = ConnectionsViewModel(ConnectionApiClient(connClient()), MutableStateFlow(TeamId("test-team")))
        setContent {
            MaterialTheme { ConnectionListScreen(viewModel = vm, onCreateConnection = {}, onEditConnection = {}, onBack = {}) }
        }

        waitUntil(timeoutMillis = 3000L) { onAllNodesWithText("My GitHub").fetchSemanticsNodes().isNotEmpty() }
        onAllNodesWithText("Test").assertCountEquals(2)
        onAllNodesWithText("Delete").assertCountEquals(2)
    }

    @Test
    fun `connection form renders with GitHub fields by default`() = runComposeUiTest {
        val vm = ConnectionsViewModel(ConnectionApiClient(connClient("""{"connections":[]}""")), MutableStateFlow(TeamId("test-team")))
        setContent {
            MaterialTheme { ConnectionFormScreen(viewModel = vm, onBack = {}) }
        }

        onNodeWithTag("connection_form_screen").assertExists()
        onNodeWithTag("connection_name_field").assertExists()
        onNodeWithTag("connection_type_selector").assertExists()
        onNodeWithTag("save_connection_button").assertExists()
        onNodeWithTag("github_token").assertExists()
        onNodeWithTag("github_owner").assertExists()
        onNodeWithTag("github_repo").assertExists()
    }

    @Test
    fun `save button disabled when form incomplete`() = runComposeUiTest {
        val vm = ConnectionsViewModel(ConnectionApiClient(connClient("""{"connections":[]}""")), MutableStateFlow(TeamId("test-team")))
        setContent {
            MaterialTheme { ConnectionFormScreen(viewModel = vm, onBack = {}) }
        }

        onNodeWithTag("save_connection_button").assertIsNotEnabled()
        onNodeWithTag("connection_name_field").performTextInput("My Conn")
        onNodeWithTag("save_connection_button").assertIsNotEnabled()
    }

    @Test
    fun `save button enables when form complete`() = runComposeUiTest {
        val vm = ConnectionsViewModel(ConnectionApiClient(connClient("""{"connections":[]}""")), MutableStateFlow(TeamId("test-team")))
        setContent {
            MaterialTheme { ConnectionFormScreen(viewModel = vm, onBack = {}) }
        }

        onNodeWithTag("connection_name_field").performTextInput("My GitHub")
        onNodeWithTag("github_token").performTextInput("ghp_test")
        onNodeWithTag("github_owner").performTextInput("owner")
        onNodeWithTag("github_repo").performTextInput("repo")
        onNodeWithTag("save_connection_button").assertIsEnabled()
    }

    @Test
    fun `back button triggers callback`() = runComposeUiTest {
        val vm = ConnectionsViewModel(ConnectionApiClient(connClient("""{"connections":[]}""")), MutableStateFlow(TeamId("test-team")))
        var backClicked = false
        setContent {
            MaterialTheme { ConnectionListScreen(viewModel = vm, onCreateConnection = {}, onEditConnection = {}, onBack = { backClicked = true }) }
        }

        onNodeWithText("Back").performClick()
        assertTrue(backClicked)
    }

    @Test
    fun `connection list delete opens confirmation dialog`() = runComposeUiTest {
        val vm = ConnectionsViewModel(ConnectionApiClient(connClient()), MutableStateFlow(TeamId("test-team")))
        setContent {
            MaterialTheme { ConnectionListScreen(viewModel = vm, onCreateConnection = {}, onEditConnection = {}, onBack = {}) }
        }

        waitUntil(timeoutMillis = 3000L) { onAllNodesWithText("My GitHub").fetchSemanticsNodes().isNotEmpty() }
        onAllNodesWithText("Delete").onFirst().performClick()
        waitUntil(timeoutMillis = 3000L) { onAllNodesWithTag("delete_connection_confirm_c1", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("delete_connection_confirm_c1", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `connection list delete confirmation cancel dismisses`() = runComposeUiTest {
        val vm = ConnectionsViewModel(ConnectionApiClient(connClient()), MutableStateFlow(TeamId("test-team")))
        setContent {
            MaterialTheme { ConnectionListScreen(viewModel = vm, onCreateConnection = {}, onEditConnection = {}, onBack = {}) }
        }

        waitUntil(timeoutMillis = 3000L) { onAllNodesWithText("My GitHub").fetchSemanticsNodes().isNotEmpty() }
        onAllNodesWithText("Delete").onFirst().performClick()
        waitUntil(timeoutMillis = 3000L) { onAllNodesWithTag("delete_connection_confirm_c1", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("delete_connection_confirm_c1_cancel", useUnmergedTree = true).performClick()
        onNodeWithTag("delete_connection_confirm_c1", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun `connection form Slack type shows webhook field`() = runComposeUiTest {
        val vm = ConnectionsViewModel(ConnectionApiClient(connClient("""{"connections":[]}""")), MutableStateFlow(TeamId("test-team")))
        setContent {
            MaterialTheme { ConnectionFormScreen(viewModel = vm, onBack = {}) }
        }

        onNodeWithTag("connection_type_selector").performClick()
        onNodeWithText("Slack").performClick()
        onNodeWithTag("slack_webhook_url").assertExists()
        onNodeWithTag("github_token").assertDoesNotExist()
    }

    @Test
    fun `connection form TeamCity type shows server and token fields`() = runComposeUiTest {
        val vm = ConnectionsViewModel(ConnectionApiClient(connClient("""{"connections":[]}""")), MutableStateFlow(TeamId("test-team")))
        setContent {
            MaterialTheme { ConnectionFormScreen(viewModel = vm, onBack = {}) }
        }

        onNodeWithTag("connection_type_selector").performClick()
        onNodeWithText("TeamCity").performClick()
        onNodeWithTag("teamcity_server_url").assertExists()
        onNodeWithTag("teamcity_token").assertExists()
    }

    @Test
    fun `connection form Slack validation requires webhook URL`() = runComposeUiTest {
        val vm = ConnectionsViewModel(ConnectionApiClient(connClient("""{"connections":[]}""")), MutableStateFlow(TeamId("test-team")))
        setContent {
            MaterialTheme { ConnectionFormScreen(viewModel = vm, onBack = {}) }
        }

        onNodeWithTag("connection_type_selector").performClick()
        onNodeWithText("Slack").performClick()

        onNodeWithTag("connection_name_field").performTextInput("My Slack")
        onNodeWithTag("save_connection_button").assertIsNotEnabled()

        onNodeWithTag("slack_webhook_url").performTextInput("https://hooks.slack.com/test")
        onNodeWithTag("save_connection_button").assertIsEnabled()
    }

    @Test
    fun `connection form TeamCity validation requires both fields`() = runComposeUiTest {
        val vm = ConnectionsViewModel(ConnectionApiClient(connClient("""{"connections":[]}""")), MutableStateFlow(TeamId("test-team")))
        setContent {
            MaterialTheme { ConnectionFormScreen(viewModel = vm, onBack = {}) }
        }

        onNodeWithTag("connection_type_selector").performClick()
        onNodeWithText("TeamCity").performClick()

        onNodeWithTag("connection_name_field").performTextInput("My TC")
        onNodeWithTag("teamcity_server_url").performTextInput("https://tc.example.com")
        // Only server URL — save still disabled
        onNodeWithTag("save_connection_button").assertIsNotEnabled()

        onNodeWithTag("teamcity_token").performTextInput("token123")
        onNodeWithTag("save_connection_button").assertIsEnabled()
    }

    @Test
    fun `connection form back button triggers callback`() = runComposeUiTest {
        val vm = ConnectionsViewModel(ConnectionApiClient(connClient("""{"connections":[]}""")), MutableStateFlow(TeamId("test-team")))
        var backClicked = false
        setContent {
            MaterialTheme { ConnectionFormScreen(viewModel = vm, onBack = { backClicked = true }) }
        }

        onNodeWithText("Back").performClick()
        assertTrue(backClicked)
    }

    @Test
    fun `connection list error state shows snackbar`() = runComposeUiTest {
        val vm = ConnectionsViewModel(ConnectionApiClient(connClient("error", HttpStatusCode.InternalServerError)), MutableStateFlow(TeamId("test-team")))
        setContent {
            MaterialTheme { ConnectionListScreen(viewModel = vm, onCreateConnection = {}, onEditConnection = {}, onBack = {}) }
        }

        waitUntil(timeoutMillis = 3000L) { onAllNodesWithText("Retry").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("Retry").assertExists()
    }

    @Test
    fun `connection form GitHub requires all three fields`() = runComposeUiTest {
        val vm = ConnectionsViewModel(ConnectionApiClient(connClient("""{"connections":[]}""")), MutableStateFlow(TeamId("test-team")))
        setContent {
            MaterialTheme { ConnectionFormScreen(viewModel = vm, onBack = {}) }
        }

        onNodeWithTag("connection_name_field").performTextInput("My GH")
        onNodeWithTag("github_token").performTextInput("ghp_test")
        // Only token — save still disabled (owner and repo missing)
        onNodeWithTag("save_connection_button").assertIsNotEnabled()

        onNodeWithTag("github_owner").performTextInput("owner")
        // Still missing repo
        onNodeWithTag("save_connection_button").assertIsNotEnabled()

        onNodeWithTag("github_repo").performTextInput("repo")
        onNodeWithTag("save_connection_button").assertIsEnabled()
    }

    // --- Edit connection tests ---

    private val githubConnectionJson = """{"connection":{"id":"c1","name":"My GitHub","type":"GITHUB","config":{"type":"github","token":"ghp_secret","owner":"mr3zee","repo":"release-wizard","pollingIntervalSeconds":30},"createdAt":"2026-03-13T00:00:00Z","updatedAt":"2026-03-13T00:00:00Z"}}"""

    private val slackConnectionJson = """{"connection":{"id":"c2","name":"Team Slack","type":"SLACK","config":{"type":"slack","webhookUrl":"https://hooks.slack.com/test"},"createdAt":"2026-03-13T00:00:00Z","updatedAt":"2026-03-13T00:00:00Z"}}"""

    private val teamCityConnectionJson = """{"connection":{"id":"c3","name":"My TC","type":"TEAMCITY","config":{"type":"teamcity","serverUrl":"https://tc.example.com","token":"tc_token","pollingIntervalSeconds":30},"createdAt":"2026-03-13T00:00:00Z","updatedAt":"2026-03-13T00:00:00Z"}}"""

    private fun editClient(connectionJson: String, connectionPath: String) = mockHttpClient(
        mapOf(
            "/connections" to json("""{"connections":[]}"""),
            connectionPath to json(connectionJson),
        )
    )

    @Test
    fun `edit form shows Edit Connection title`() = runComposeUiTest {
        val vm = ConnectionsViewModel(ConnectionApiClient(editClient(githubConnectionJson, "/connections/c1")), MutableStateFlow(TeamId("test-team")))
        setContent {
            MaterialTheme {
                ConnectionFormScreen(viewModel = vm, connectionId = ConnectionId("c1"), onBack = {})
            }
        }

        waitUntil(timeoutMillis = 3000L) { onAllNodesWithText("Edit Connection").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("Edit Connection").assertExists()
    }

    @Test
    fun `edit form pre-populates GitHub connection fields`() = runComposeUiTest {
        val vm = ConnectionsViewModel(ConnectionApiClient(editClient(githubConnectionJson, "/connections/c1")), MutableStateFlow(TeamId("test-team")))
        setContent {
            MaterialTheme {
                ConnectionFormScreen(viewModel = vm, connectionId = ConnectionId("c1"), onBack = {})
            }
        }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodes(hasTestTag("connection_name_field") and hasText("My GitHub")).fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithTag("connection_name_field").assertTextContains("My GitHub")
        onNodeWithTag("connection_type_selector").assertTextContains("GitHub")
        onNodeWithTag("github_owner").assertTextContains("mr3zee")
        onNodeWithTag("github_repo").assertTextContains("release-wizard")
    }

    @Test
    fun `edit form pre-populates Slack connection fields`() = runComposeUiTest {
        val vm = ConnectionsViewModel(ConnectionApiClient(editClient(slackConnectionJson, "/connections/c2")), MutableStateFlow(TeamId("test-team")))
        setContent {
            MaterialTheme {
                ConnectionFormScreen(viewModel = vm, connectionId = ConnectionId("c2"), onBack = {})
            }
        }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodes(hasTestTag("connection_name_field") and hasText("Team Slack")).fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithTag("connection_name_field").assertTextContains("Team Slack")
        onNodeWithTag("slack_webhook_url").assertTextContains("https://hooks.slack.com/test")
    }

    @Test
    fun `edit form pre-populates TeamCity connection fields`() = runComposeUiTest {
        val vm = ConnectionsViewModel(ConnectionApiClient(editClient(teamCityConnectionJson, "/connections/c3")), MutableStateFlow(TeamId("test-team")))
        setContent {
            MaterialTheme {
                ConnectionFormScreen(viewModel = vm, connectionId = ConnectionId("c3"), onBack = {})
            }
        }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodes(hasTestTag("connection_name_field") and hasText("My TC")).fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithTag("connection_name_field").assertTextContains("My TC")
        onNodeWithTag("teamcity_server_url").assertTextContains("https://tc.example.com")
    }

    @Test
    fun `edit form disables type selector`() = runComposeUiTest {
        val vm = ConnectionsViewModel(ConnectionApiClient(editClient(githubConnectionJson, "/connections/c1")), MutableStateFlow(TeamId("test-team")))
        setContent {
            MaterialTheme {
                ConnectionFormScreen(viewModel = vm, connectionId = ConnectionId("c1"), onBack = {})
            }
        }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodes(hasTestTag("connection_name_field") and hasText("My GitHub")).fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithTag("connection_type_selector").assertIsNotEnabled()
    }

    @Test
    fun `edit form save button enabled when loaded`() = runComposeUiTest {
        val vm = ConnectionsViewModel(ConnectionApiClient(editClient(githubConnectionJson, "/connections/c1")), MutableStateFlow(TeamId("test-team")))
        setContent {
            MaterialTheme {
                ConnectionFormScreen(viewModel = vm, connectionId = ConnectionId("c1"), onBack = {})
            }
        }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodes(hasTestTag("connection_name_field") and hasText("My GitHub")).fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithTag("save_connection_button").assertIsEnabled()
    }

    @Test
    fun `edit form save triggers onBack`() = runComposeUiTest {
        val vm = ConnectionsViewModel(ConnectionApiClient(editClient(githubConnectionJson, "/connections/c1")), MutableStateFlow(TeamId("test-team")))
        var backCalled = false
        setContent {
            MaterialTheme {
                ConnectionFormScreen(viewModel = vm, connectionId = ConnectionId("c1"), onBack = { backCalled = true })
            }
        }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodes(hasTestTag("connection_name_field") and hasText("My GitHub")).fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithTag("save_connection_button").performClick()
        waitUntil(timeoutMillis = 3000L) { backCalled }
    }

    @Test
    fun `edit form allows editing name`() = runComposeUiTest {
        val vm = ConnectionsViewModel(ConnectionApiClient(editClient(githubConnectionJson, "/connections/c1")), MutableStateFlow(TeamId("test-team")))
        setContent {
            MaterialTheme {
                ConnectionFormScreen(viewModel = vm, connectionId = ConnectionId("c1"), onBack = {})
            }
        }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodes(hasTestTag("connection_name_field") and hasText("My GitHub")).fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithTag("connection_name_field").performTextClearance()
        onNodeWithTag("connection_name_field").performTextInput("Renamed")
        onNodeWithTag("connection_name_field").assertTextContains("Renamed")
        onNodeWithTag("save_connection_button").assertIsEnabled()
    }

    @Test
    fun `create form still shows New Connection title`() = runComposeUiTest {
        val vm = ConnectionsViewModel(ConnectionApiClient(connClient("""{"connections":[]}""")), MutableStateFlow(TeamId("test-team")))
        setContent {
            MaterialTheme { ConnectionFormScreen(viewModel = vm, onBack = {}) }
        }

        onNodeWithText("New Connection").assertExists()
    }

    @Test
    fun `create form type selector is enabled`() = runComposeUiTest {
        val vm = ConnectionsViewModel(ConnectionApiClient(connClient("""{"connections":[]}""")), MutableStateFlow(TeamId("test-team")))
        setContent {
            MaterialTheme { ConnectionFormScreen(viewModel = vm, onBack = {}) }
        }

        onNodeWithTag("connection_type_selector").assertIsEnabled()
    }

    @Test
    fun `clicking connection item triggers edit callback`() = runComposeUiTest {
        val vm = ConnectionsViewModel(ConnectionApiClient(connClient()), MutableStateFlow(TeamId("test-team")))
        var editedId: ConnectionId? = null
        setContent {
            MaterialTheme {
                ConnectionListScreen(
                    viewModel = vm,
                    onCreateConnection = {},
                    onEditConnection = { editedId = it },
                    onBack = {},
                )
            }
        }

        waitUntil(timeoutMillis = 3000L) { onAllNodesWithText("My GitHub").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("connection_item_c1", useUnmergedTree = true).performClick()
        assertEquals(editedId?.value, "c1")
    }

    // ---- Refresh Tests ----

    @Test
    fun `refresh button exists on connection list`() = runComposeUiTest {
        val vm = ConnectionsViewModel(ConnectionApiClient(connClient("""{"connections":[]}""")), MutableStateFlow(TeamId("test-team")))
        setContent {
            MaterialTheme { ConnectionListScreen(viewModel = vm, onCreateConnection = {}, onEditConnection = {}, onBack = {}) }
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
                path.endsWith("/connections") -> {
                    val conns = if (!returnNewData.get()) {
                        """[{"id":"c1","name":"My GitHub","type":"GITHUB","config":{"type":"github","token":"****","owner":"x","repo":"y"},"createdAt":"2026-03-13T00:00:00Z","updatedAt":"2026-03-13T00:00:00Z"}]"""
                    } else {
                        """[{"id":"c1","name":"My GitHub","type":"GITHUB","config":{"type":"github","token":"****","owner":"x","repo":"y"},"createdAt":"2026-03-13T00:00:00Z","updatedAt":"2026-03-13T00:00:00Z"},{"id":"c-new","name":"New Slack","type":"SLACK","config":{"type":"slack","webhookUrl":"https://hooks.slack.com/new"},"createdAt":"2026-03-13T00:00:00Z","updatedAt":"2026-03-13T00:00:00Z"}]"""
                    }
                    respond("""{"connections":$conns}""", status = HttpStatusCode.OK, headers = jsonHeaders)
                }
                else -> respond("{}", status = HttpStatusCode.OK, headers = jsonHeaders)
            }
        }) {
            install(ContentNegotiation) { json(AppJson) }
            install(HttpCookies)
            expectSuccess = true
        }
        val vm = ConnectionsViewModel(ConnectionApiClient(client), MutableStateFlow(TeamId("test-team")))

        setContent {
            MaterialTheme { ConnectionListScreen(viewModel = vm, onCreateConnection = {}, onEditConnection = {}, onBack = {}) }
        }

        waitUntil(timeoutMillis = 3000L) { onAllNodesWithText("My GitHub").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("New Slack").assertDoesNotExist()

        returnNewData.set(true)
        onNodeWithTag("refresh_button").performClick()

        waitUntil(timeoutMillis = 5000L) { onAllNodesWithText("New Slack").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("New Slack").assertExists()
    }

    @Test
    fun `refresh error shows banner and keeps list visible`() = runComposeUiTest {
        val failOnRefresh = AtomicBoolean(false)
        val client = HttpClient(MockEngine { request ->
            val path = request.url.encodedPath
            val jsonHeaders = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            when {
                path.endsWith("/connections") -> {
                    if (!failOnRefresh.get()) {
                        respond(connectionsJson, status = HttpStatusCode.OK, headers = jsonHeaders)
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
        val vm = ConnectionsViewModel(ConnectionApiClient(client), MutableStateFlow(TeamId("test-team")))

        setContent {
            MaterialTheme { ConnectionListScreen(viewModel = vm, onCreateConnection = {}, onEditConnection = {}, onBack = {}) }
        }

        waitUntil(timeoutMillis = 3000L) { onAllNodesWithText("My GitHub").fetchSemanticsNodes().isNotEmpty() }
        failOnRefresh.set(true)
        onNodeWithTag("refresh_button").performClick()
        waitUntil(timeoutMillis = 5000L) { onAllNodesWithTag("refresh_error_banner").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("refresh_error_banner").assertExists()
        onNodeWithText("My GitHub").assertExists()
    }

    @Test
    fun `dismiss refresh error hides banner`() = runComposeUiTest {
        val failOnRefresh = AtomicBoolean(false)
        val client = HttpClient(MockEngine { request ->
            val path = request.url.encodedPath
            val jsonHeaders = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            when {
                path.endsWith("/connections") -> {
                    if (!failOnRefresh.get()) {
                        respond(connectionsJson, status = HttpStatusCode.OK, headers = jsonHeaders)
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
        val vm = ConnectionsViewModel(ConnectionApiClient(client), MutableStateFlow(TeamId("test-team")))

        setContent {
            MaterialTheme { ConnectionListScreen(viewModel = vm, onCreateConnection = {}, onEditConnection = {}, onBack = {}) }
        }

        waitUntil(timeoutMillis = 3000L) { onAllNodesWithText("My GitHub").fetchSemanticsNodes().isNotEmpty() }
        failOnRefresh.set(true)
        onNodeWithTag("refresh_button").performClick()
        waitUntil(timeoutMillis = 5000L) { onAllNodesWithTag("refresh_error_banner").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("dismiss_refresh_error").performClick()
        waitUntil(timeoutMillis = 3000L) { onAllNodesWithTag("refresh_error_banner").fetchSemanticsNodes().isEmpty() }
        onNodeWithTag("refresh_error_banner").assertDoesNotExist()
    }

    // ---- New feature tests ----

    @Test
    fun `connection form shows section header for default type`() = runComposeUiTest {
        val vm = ConnectionsViewModel(ConnectionApiClient(connClient("""{"connections":[]}""")), MutableStateFlow(TeamId("test-team")))
        setContent {
            MaterialTheme { ConnectionFormScreen(viewModel = vm, onBack = {}) }
        }

        // Default type is GitHub
        onNodeWithTag("section_header_github", useUnmergedTree = true).assertExists()
    }

    // ---- Sort Tests ----

    private val sortableConnectionsJson = """{"connections":[
        {"id":"c1","name":"Zebra Conn","type":"GITHUB","config":{"type":"github","token":"t","owner":"o","repo":"r"},"createdAt":"2026-03-10T00:00:00Z","updatedAt":"2026-03-10T00:00:00Z"},
        {"id":"c2","name":"Alpha Conn","type":"SLACK","config":{"type":"slack","webhookUrl":"https://hooks.slack.com/test"},"createdAt":"2026-03-12T00:00:00Z","updatedAt":"2026-03-12T00:00:00Z"}
    ]}"""

    @Test
    fun `connection list sort dropdown exists`() = runComposeUiTest {
        val vm = ConnectionsViewModel(ConnectionApiClient(connClient(sortableConnectionsJson)), MutableStateFlow(TeamId("test-team")))
        setContent { MaterialTheme { ConnectionListScreen(viewModel = vm, onCreateConnection = {}, onEditConnection = {}, onBack = {}) } }

        waitUntil(timeoutMillis = 3000L) { onAllNodesWithText("Zebra Conn").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("sort_dropdown_button").assertExists()
    }

    @Test
    fun `connection list sort dropdown shows all options`() = runComposeUiTest {
        val vm = ConnectionsViewModel(ConnectionApiClient(connClient(sortableConnectionsJson)), MutableStateFlow(TeamId("test-team")))
        setContent { MaterialTheme { ConnectionListScreen(viewModel = vm, onCreateConnection = {}, onEditConnection = {}, onBack = {}) } }

        waitUntil(timeoutMillis = 3000L) { onAllNodesWithText("Zebra Conn").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("sort_dropdown_button").performClick()
        waitUntil(timeoutMillis = 3000L) { onAllNodesWithTag("sort_option_NAME_ASC").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("sort_option_NAME_ASC").assertExists()
        onNodeWithTag("sort_option_NAME_DESC").assertExists()
        onNodeWithTag("sort_option_NEWEST").assertExists()
        onNodeWithTag("sort_option_OLDEST").assertExists()
    }

    @Test
    fun `connection list default sort is name ascending`() = runComposeUiTest {
        val vm = ConnectionsViewModel(ConnectionApiClient(connClient(sortableConnectionsJson)), MutableStateFlow(TeamId("test-team")))
        setContent { MaterialTheme { ConnectionListScreen(viewModel = vm, onCreateConnection = {}, onEditConnection = {}, onBack = {}) } }

        waitUntil(timeoutMillis = 3000L) { onAllNodesWithText("Zebra Conn").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("Name (A–Z)", substring = true).assertExists()
    }

    @Test
    fun `connection list sort by name descending changes label`() = runComposeUiTest {
        val vm = ConnectionsViewModel(ConnectionApiClient(connClient(sortableConnectionsJson)), MutableStateFlow(TeamId("test-team")))
        setContent { MaterialTheme { ConnectionListScreen(viewModel = vm, onCreateConnection = {}, onEditConnection = {}, onBack = {}) } }

        waitUntil(timeoutMillis = 3000L) { onAllNodesWithText("Zebra Conn").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("sort_dropdown_button").performClick()
        waitUntil(timeoutMillis = 3000L) { onAllNodesWithTag("sort_option_NAME_DESC").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("sort_option_NAME_DESC").performClick()
        waitForIdle()
        onNodeWithText("Name (Z–A)", substring = true).assertExists()
    }

    @Test
    fun `connection list sort by newest changes label`() = runComposeUiTest {
        val vm = ConnectionsViewModel(ConnectionApiClient(connClient(sortableConnectionsJson)), MutableStateFlow(TeamId("test-team")))
        setContent { MaterialTheme { ConnectionListScreen(viewModel = vm, onCreateConnection = {}, onEditConnection = {}, onBack = {}) } }

        waitUntil(timeoutMillis = 3000L) { onAllNodesWithText("Zebra Conn").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("sort_dropdown_button").performClick()
        waitUntil(timeoutMillis = 3000L) { onAllNodesWithTag("sort_option_NEWEST").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("sort_option_NEWEST").performClick()
        waitForIdle()
        onNodeWithText("Newest first", substring = true).assertExists()
    }

    @Test
    fun `password toggle reveals password text`() = runComposeUiTest {
        val vm = ConnectionsViewModel(ConnectionApiClient(connClient("""{"connections":[]}""")), MutableStateFlow(TeamId("test-team")))
        setContent {
            MaterialTheme { ConnectionFormScreen(viewModel = vm, onBack = {}) }
        }

        // Enter text in the github_token field (GitHub is default type)
        onNodeWithTag("github_token").performTextInput("secret123")
        waitForIdle()

        // Before toggling: the toggle icon should have "Show password" content description
        onNode(
            hasTestTag("github_token_toggle_visibility") and hasContentDescription("Show password"),
            useUnmergedTree = true,
        ).assertExists("Toggle should show 'Show password' before clicking")

        // Click the toggle visibility button
        onNodeWithTag("github_token_toggle_visibility", useUnmergedTree = true).performClick()
        waitForIdle()

        // After toggling: the toggle icon should have "Hide password" content description
        onNode(
            hasTestTag("github_token_toggle_visibility") and hasContentDescription("Hide password"),
            useUnmergedTree = true,
        ).assertExists("Toggle should show 'Hide password' after clicking")

        // Click again to re-hide
        onNodeWithTag("github_token_toggle_visibility", useUnmergedTree = true).performClick()
        waitForIdle()

        // Should revert back to "Show password"
        onNode(
            hasTestTag("github_token_toggle_visibility") and hasContentDescription("Show password"),
            useUnmergedTree = true,
        ).assertExists("Toggle should revert to 'Show password' after second click")
    }

    // ===========================================================================
    // QA-CONNLIST-1: Sorting reorders displayed list
    // ===========================================================================

    @Test
    fun `sort by name ascending puts Alpha before Zebra`() = runComposeUiTest {
        val vm = ConnectionsViewModel(ConnectionApiClient(connClient(sortableConnectionsJson)), MutableStateFlow(TeamId("test-team")))
        setContent { MaterialTheme { ConnectionListScreen(viewModel = vm, onCreateConnection = {}, onEditConnection = {}, onBack = {}) } }

        waitUntil(timeoutMillis = 3000L) { onAllNodesWithText("Alpha Conn").fetchSemanticsNodes().isNotEmpty() }
        // Default sort is NAME_ASC so Alpha should come first
        // Verify both items exist – in name-asc, Alpha comes before Zebra
        onNodeWithText("Alpha Conn").assertExists()
        onNodeWithText("Zebra Conn").assertExists()
        // Verify relative vertical positions: Alpha (c2) should appear above Zebra (c1)
        val alphaTop = onNodeWithTag("connection_item_c2", useUnmergedTree = true)
            .getUnclippedBoundsInRoot().top
        val zebraTop = onNodeWithTag("connection_item_c1", useUnmergedTree = true)
            .getUnclippedBoundsInRoot().top
        assert(alphaTop < zebraTop) { "Alpha should appear before Zebra in ascending sort" }
    }

    @Test
    fun `sort by name descending puts Zebra before Alpha`() = runComposeUiTest {
        val vm = ConnectionsViewModel(ConnectionApiClient(connClient(sortableConnectionsJson)), MutableStateFlow(TeamId("test-team")))
        setContent { MaterialTheme { ConnectionListScreen(viewModel = vm, onCreateConnection = {}, onEditConnection = {}, onBack = {}) } }

        waitUntil(timeoutMillis = 3000L) { onAllNodesWithText("Zebra Conn").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("sort_dropdown_button").performClick()
        waitUntil(timeoutMillis = 3000L) { onAllNodesWithTag("sort_option_NAME_DESC").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("sort_option_NAME_DESC").performClick()
        waitForIdle()
        // Both still exist after sorting
        onNodeWithText("Zebra Conn").assertExists()
        onNodeWithText("Alpha Conn").assertExists()
        onNodeWithText("Name (Z", substring = true).assertExists()
        // Verify relative vertical positions: Zebra (c1) should appear above Alpha (c2)
        val zebraTop = onNodeWithTag("connection_item_c1", useUnmergedTree = true)
            .getUnclippedBoundsInRoot().top
        val alphaTop = onNodeWithTag("connection_item_c2", useUnmergedTree = true)
            .getUnclippedBoundsInRoot().top
        assert(zebraTop < alphaTop) { "Zebra should appear before Alpha in descending sort" }
    }

    @Test
    fun `sort by newest reorders by date descending`() = runComposeUiTest {
        val vm = ConnectionsViewModel(ConnectionApiClient(connClient(sortableConnectionsJson)), MutableStateFlow(TeamId("test-team")))
        setContent { MaterialTheme { ConnectionListScreen(viewModel = vm, onCreateConnection = {}, onEditConnection = {}, onBack = {}) } }

        waitUntil(timeoutMillis = 3000L) { onAllNodesWithText("Zebra Conn").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("sort_dropdown_button").performClick()
        waitUntil(timeoutMillis = 3000L) { onAllNodesWithTag("sort_option_NEWEST").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("sort_option_NEWEST").performClick()
        waitForIdle()
        // Alpha (2026-03-12) is newer than Zebra (2026-03-10), so should be first
        onNodeWithText("Alpha Conn").assertExists()
        onNodeWithText("Zebra Conn").assertExists()
        onNodeWithText("Newest first", substring = true).assertExists()
        // Verify relative vertical positions: Alpha (c2, newer) should appear above Zebra (c1, older)
        val alphaTop = onNodeWithTag("connection_item_c2", useUnmergedTree = true)
            .getUnclippedBoundsInRoot().top
        val zebraTop = onNodeWithTag("connection_item_c1", useUnmergedTree = true)
            .getUnclippedBoundsInRoot().top
        assert(alphaTop < zebraTop) { "Alpha (newer) should appear before Zebra (older) in newest-first sort" }
    }

    @Test
    fun `sort by oldest reorders by date ascending`() = runComposeUiTest {
        val vm = ConnectionsViewModel(ConnectionApiClient(connClient(sortableConnectionsJson)), MutableStateFlow(TeamId("test-team")))
        setContent { MaterialTheme { ConnectionListScreen(viewModel = vm, onCreateConnection = {}, onEditConnection = {}, onBack = {}) } }

        waitUntil(timeoutMillis = 3000L) { onAllNodesWithText("Zebra Conn").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("sort_dropdown_button").performClick()
        waitUntil(timeoutMillis = 3000L) { onAllNodesWithTag("sort_option_OLDEST").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("sort_option_OLDEST").performClick()
        waitForIdle()
        // Zebra (2026-03-10) is oldest, so should be first
        onNodeWithText("Zebra Conn").assertExists()
        onNodeWithText("Alpha Conn").assertExists()
        onNodeWithText("Oldest first", substring = true).assertExists()
        // Verify relative vertical positions: Zebra (c1, older) should appear above Alpha (c2, newer)
        val zebraTop = onNodeWithTag("connection_item_c1", useUnmergedTree = true)
            .getUnclippedBoundsInRoot().top
        val alphaTop = onNodeWithTag("connection_item_c2", useUnmergedTree = true)
            .getUnclippedBoundsInRoot().top
        assert(zebraTop < alphaTop) { "Zebra (older) should appear before Alpha (newer) in oldest-first sort" }
    }

    // ===========================================================================
    // QA-CONNLIST-2: Test button shows spinner while in-progress
    // ===========================================================================

    @Test
    fun `test button shows spinner while testing`() = runComposeUiTest {
        // Use a client that delays the test response so we can see the spinner
        val testStarted = AtomicBoolean(false)
        val testCanFinish = AtomicBoolean(false)
        val client = HttpClient(MockEngine { request ->
            val path = request.url.encodedPath
            val jsonHeaders = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            when {
                path.endsWith("/test") -> {
                    testStarted.set(true)
                    // Block until allowed to finish
                    while (!testCanFinish.get()) { kotlinx.coroutines.yield() }
                    respond("""{"success":true,"message":"OK"}""", status = HttpStatusCode.OK, headers = jsonHeaders)
                }
                path.endsWith("/connections") -> {
                    respond(connectionsJson, status = HttpStatusCode.OK, headers = jsonHeaders)
                }
                else -> respond("{}", status = HttpStatusCode.OK, headers = jsonHeaders)
            }
        }) {
            install(ContentNegotiation) { json(AppJson) }
            install(HttpCookies)
            expectSuccess = true
        }
        val vm = ConnectionsViewModel(ConnectionApiClient(client), MutableStateFlow(TeamId("test-team")))

        setContent {
            MaterialTheme { ConnectionListScreen(viewModel = vm, onCreateConnection = {}, onEditConnection = {}, onBack = {}) }
        }

        waitUntil(timeoutMillis = 3000L) { onAllNodesWithText("My GitHub").fetchSemanticsNodes().isNotEmpty() }
        // Click test on first connection
        onNodeWithTag("test_connection_c1", useUnmergedTree = true).performClick()
        // Wait for spinner to appear - the test button should be disabled while testing
        waitUntil(timeoutMillis = 3000L) { testStarted.get() }
        onNodeWithTag("test_connection_c1", useUnmergedTree = true).assertIsNotEnabled()
        // Let the test complete
        testCanFinish.set(true)
        waitUntil(timeoutMillis = 3000L) {
            onAllNodes(hasTestTag("test_connection_c1") and isEnabled(), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithTag("test_connection_c1", useUnmergedTree = true).assertIsEnabled()
    }

    // ===========================================================================
    // QA-CONNLIST-3: Test success shows snackbar
    // ===========================================================================

    @Test
    fun `test success shows snackbar with success message`() = runComposeUiTest {
        val client = mockHttpClient(
            mapOf(
                "/connections" to json(connectionsJson),
                "/connections/c1/test" to json("""{"success":true,"message":"All good"}""", method = HttpMethod.Post),
            )
        )
        val vm = ConnectionsViewModel(ConnectionApiClient(client), MutableStateFlow(TeamId("test-team")))

        setContent {
            MaterialTheme { ConnectionListScreen(viewModel = vm, onCreateConnection = {}, onEditConnection = {}, onBack = {}) }
        }

        waitUntil(timeoutMillis = 3000L) { onAllNodesWithText("My GitHub").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("test_connection_c1", useUnmergedTree = true).performClick()
        // Snackbar should appear with the success message
        waitUntil(timeoutMillis = 5000L) {
            onAllNodesWithText("All good", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithText("All good", substring = true).assertExists()
    }

    // ===========================================================================
    // QA-CONNLIST-4: Test failure shows snackbar with error
    // ===========================================================================

    @Test
    fun `test failure shows error snackbar`() = runComposeUiTest {
        val client = mockHttpClient(
            mapOf(
                "/connections" to json(connectionsJson),
                "/connections/c1/test" to json("""{"success":false,"message":"Auth failed"}""", method = HttpMethod.Post),
            )
        )
        val vm = ConnectionsViewModel(ConnectionApiClient(client), MutableStateFlow(TeamId("test-team")))

        setContent {
            MaterialTheme { ConnectionListScreen(viewModel = vm, onCreateConnection = {}, onEditConnection = {}, onBack = {}) }
        }

        waitUntil(timeoutMillis = 3000L) { onAllNodesWithText("My GitHub").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("test_connection_c1", useUnmergedTree = true).performClick()
        // The error is set through vm.error -> snackbar via LaunchedEffect
        waitUntil(timeoutMillis = 5000L) {
            onAllNodesWithText("Auth failed", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithText("Auth failed", substring = true).assertExists()
    }

    // ===========================================================================
    // QA-CONNLIST-5: Confirm delete removes item
    // ===========================================================================

    @Test
    fun `confirm delete removes connection from list`() = runComposeUiTest {
        val deletePerformed = AtomicBoolean(false)
        val client = HttpClient(MockEngine { request ->
            val path = request.url.encodedPath
            val method = request.method
            val jsonHeaders = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            when {
                method == HttpMethod.Delete && path.contains("/connections/c1") -> {
                    deletePerformed.set(true)
                    respond("{}", status = HttpStatusCode.OK, headers = jsonHeaders)
                }
                path.endsWith("/connections") -> {
                    val data = if (deletePerformed.get()) {
                        """{"connections":[{"id":"c2","name":"Team Slack","type":"SLACK","config":{"type":"slack","webhookUrl":"https://hooks.slack.com/test"},"createdAt":"2026-03-13T00:00:00Z","updatedAt":"2026-03-13T00:00:00Z"}]}"""
                    } else {
                        connectionsJson
                    }
                    respond(data, status = HttpStatusCode.OK, headers = jsonHeaders)
                }
                else -> respond("{}", status = HttpStatusCode.OK, headers = jsonHeaders)
            }
        }) {
            install(ContentNegotiation) { json(AppJson) }
            install(HttpCookies)
            expectSuccess = true
        }
        val vm = ConnectionsViewModel(ConnectionApiClient(client), MutableStateFlow(TeamId("test-team")))

        setContent {
            MaterialTheme { ConnectionListScreen(viewModel = vm, onCreateConnection = {}, onEditConnection = {}, onBack = {}) }
        }

        waitUntil(timeoutMillis = 3000L) { onAllNodesWithText("My GitHub").fetchSemanticsNodes().isNotEmpty() }
        // Click Delete on first connection
        onNodeWithTag("delete_connection_btn_c1", useUnmergedTree = true).performClick()
        // Wait for inline confirmation
        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("delete_connection_confirm_c1", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        // Wait for confirm button to be enabled (debounce of 300ms)
        waitUntil(timeoutMillis = 3000L) {
            onAllNodes(hasTestTag("delete_connection_confirm_c1_confirm") and isEnabled(), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        // Click confirm
        onNodeWithTag("delete_connection_confirm_c1_confirm", useUnmergedTree = true).performClick()
        // Wait for the list to reload without the deleted item
        waitUntil(timeoutMillis = 5000L) {
            onAllNodesWithText("My GitHub").fetchSemanticsNodes().isEmpty()
        }
        onNodeWithText("My GitHub").assertDoesNotExist()
        onNodeWithText("Team Slack").assertExists()
    }

    // ===========================================================================
    // QA-CONNLIST-6: Type badges show correct type
    // ===========================================================================

    @Test
    fun `connection items show type badges`() = runComposeUiTest {
        val vm = ConnectionsViewModel(ConnectionApiClient(connClient()), MutableStateFlow(TeamId("test-team")))
        setContent {
            MaterialTheme { ConnectionListScreen(viewModel = vm, onCreateConnection = {}, onEditConnection = {}, onBack = {}) }
        }

        waitUntil(timeoutMillis = 3000L) { onAllNodesWithText("My GitHub").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("connection_type_badge_c1", useUnmergedTree = true).assertExists()
        onNodeWithTag("connection_type_badge_c2", useUnmergedTree = true).assertExists()
    }

    // ===========================================================================
    // QA-CONNLIST-7: Empty state with search shows "no results" message
    // ===========================================================================

    @Test
    fun `search with no results shows no results message`() = runComposeUiTest {
        // Return empty list when search has query
        val client = HttpClient(MockEngine { request ->
            val path = request.url.encodedPath
            val jsonHeaders = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            val hasQuery = request.url.parameters["q"]?.isNotBlank() == true
            when {
                path.endsWith("/connections") -> {
                    if (hasQuery) {
                        respond("""{"connections":[]}""", status = HttpStatusCode.OK, headers = jsonHeaders)
                    } else {
                        respond(connectionsJson, status = HttpStatusCode.OK, headers = jsonHeaders)
                    }
                }
                else -> respond("{}", status = HttpStatusCode.OK, headers = jsonHeaders)
            }
        }) {
            install(ContentNegotiation) { json(AppJson) }
            install(HttpCookies)
            expectSuccess = true
        }
        val vm = ConnectionsViewModel(ConnectionApiClient(client), MutableStateFlow(TeamId("test-team")))

        setContent {
            MaterialTheme { ConnectionListScreen(viewModel = vm, onCreateConnection = {}, onEditConnection = {}, onBack = {}) }
        }

        waitUntil(timeoutMillis = 3000L) { onAllNodesWithText("My GitHub").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("search_field").performTextInput("nonexistent")
        // Wait for debounced search and empty result
        waitUntil(timeoutMillis = 5000L) {
            onAllNodesWithText("No results match your search", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithText("No results match your search", substring = true).assertExists()
        onNodeWithText("Clear search").assertExists()
    }

    // ===========================================================================
    // QA-CONNLIST-8: Filter chips exist for all connection types
    // ===========================================================================

    @Test
    fun `filter chips exist for all types`() = runComposeUiTest {
        val vm = ConnectionsViewModel(ConnectionApiClient(connClient()), MutableStateFlow(TeamId("test-team")))
        setContent {
            MaterialTheme { ConnectionListScreen(viewModel = vm, onCreateConnection = {}, onEditConnection = {}, onBack = {}) }
        }

        waitUntil(timeoutMillis = 3000L) { onAllNodesWithText("My GitHub").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("filter_ALL").assertExists()
        onNodeWithTag("filter_GITHUB").assertExists()
        onNodeWithTag("filter_SLACK").assertExists()
        onNodeWithTag("filter_TEAMCITY").assertExists()
    }

    // ===========================================================================
    // QA-CONNLIST-9: Clicking type filter chip filters connections
    // ===========================================================================

    @Test
    fun `clicking type filter chip filters connections`() = runComposeUiTest {
        val currentTypeFilter = AtomicReference<String?>(null)
        val client = HttpClient(MockEngine { request ->
            val path = request.url.encodedPath
            val jsonHeaders = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            when {
                path.endsWith("/connections") -> {
                    val typeParam = request.url.parameters["type"]
                    currentTypeFilter.set(typeParam)
                    val data = if (typeParam == "GITHUB") {
                        """{"connections":[{"id":"c1","name":"My GitHub","type":"GITHUB","config":{"type":"github","token":"t","owner":"o","repo":"r"},"createdAt":"2026-03-13T00:00:00Z","updatedAt":"2026-03-13T00:00:00Z"}]}"""
                    } else {
                        connectionsJson
                    }
                    respond(data, status = HttpStatusCode.OK, headers = jsonHeaders)
                }
                else -> respond("{}", status = HttpStatusCode.OK, headers = jsonHeaders)
            }
        }) {
            install(ContentNegotiation) { json(AppJson) }
            install(HttpCookies)
            expectSuccess = true
        }
        val vm = ConnectionsViewModel(ConnectionApiClient(client), MutableStateFlow(TeamId("test-team")))

        setContent {
            MaterialTheme { ConnectionListScreen(viewModel = vm, onCreateConnection = {}, onEditConnection = {}, onBack = {}) }
        }

        waitUntil(timeoutMillis = 3000L) { onAllNodesWithText("My GitHub").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("Team Slack").assertExists()

        // Filter by GitHub
        onNodeWithTag("filter_GITHUB").performClick()
        waitUntil(timeoutMillis = 5000L) { currentTypeFilter.get() == "GITHUB" }
        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithText("Team Slack").fetchSemanticsNodes().isEmpty()
        }
        onNodeWithText("My GitHub").assertExists()
        onNodeWithText("Team Slack").assertDoesNotExist()
    }

    // ===========================================================================
    // QA-CONNLIST-10: Empty state has Create Connection button
    // ===========================================================================

    @Test
    fun `empty state has create connection button`() = runComposeUiTest {
        val vm = ConnectionsViewModel(ConnectionApiClient(connClient("""{"connections":[]}""")), MutableStateFlow(TeamId("test-team")))
        setContent {
            MaterialTheme { ConnectionListScreen(viewModel = vm, onCreateConnection = {}, onEditConnection = {}, onBack = {}) }
        }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithText("No connections yet", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithTag("empty_state_create_connection_button").assertExists()
    }

    // ===========================================================================
    // QA-CONNLIST-11: Empty state create button triggers callback
    // ===========================================================================

    @Test
    fun `empty state create connection button triggers callback`() = runComposeUiTest {
        var createClicked = false
        val vm = ConnectionsViewModel(ConnectionApiClient(connClient("""{"connections":[]}""")), MutableStateFlow(TeamId("test-team")))
        setContent {
            MaterialTheme { ConnectionListScreen(viewModel = vm, onCreateConnection = { createClicked = true }, onEditConnection = {}, onBack = {}) }
        }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithText("No connections yet", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithTag("empty_state_create_connection_button").performClick()
        assertTrue(createClicked)
    }

    // ===========================================================================
    // QA-CONNLIST-12: Search field exists
    // ===========================================================================

    @Test
    fun `search field exists on connection list`() = runComposeUiTest {
        val vm = ConnectionsViewModel(ConnectionApiClient(connClient()), MutableStateFlow(TeamId("test-team")))
        setContent {
            MaterialTheme { ConnectionListScreen(viewModel = vm, onCreateConnection = {}, onEditConnection = {}, onBack = {}) }
        }

        onNodeWithTag("search_field").assertExists()
    }

    // ===========================================================================
    // QA-CONNLIST-13: Clear search restores full list
    // ===========================================================================

    @Test
    fun `clear search button restores full list`() = runComposeUiTest {
        val client = HttpClient(MockEngine { request ->
            val path = request.url.encodedPath
            val jsonHeaders = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            val hasQuery = request.url.parameters["q"]?.isNotBlank() == true
            when {
                path.endsWith("/connections") -> {
                    if (hasQuery) {
                        respond("""{"connections":[]}""", status = HttpStatusCode.OK, headers = jsonHeaders)
                    } else {
                        respond(connectionsJson, status = HttpStatusCode.OK, headers = jsonHeaders)
                    }
                }
                else -> respond("{}", status = HttpStatusCode.OK, headers = jsonHeaders)
            }
        }) {
            install(ContentNegotiation) { json(AppJson) }
            install(HttpCookies)
            expectSuccess = true
        }
        val vm = ConnectionsViewModel(ConnectionApiClient(client), MutableStateFlow(TeamId("test-team")))

        setContent {
            MaterialTheme { ConnectionListScreen(viewModel = vm, onCreateConnection = {}, onEditConnection = {}, onBack = {}) }
        }

        waitUntil(timeoutMillis = 3000L) { onAllNodesWithText("My GitHub").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("search_field").performTextInput("nonexistent")
        waitUntil(timeoutMillis = 5000L) {
            onAllNodesWithText("No results match your search", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        // Click clear search
        onNodeWithText("Clear search").performClick()
        waitUntil(timeoutMillis = 5000L) {
            onAllNodesWithText("My GitHub").fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithText("My GitHub").assertExists()
        onNodeWithText("Team Slack").assertExists()
    }

    // ===========================================================================
    // QA-CONNLIST-14: FAB triggers create callback
    // ===========================================================================

    @Test
    fun `fab triggers create connection callback`() = runComposeUiTest {
        var createClicked = false
        val vm = ConnectionsViewModel(ConnectionApiClient(connClient()), MutableStateFlow(TeamId("test-team")))
        setContent {
            MaterialTheme { ConnectionListScreen(viewModel = vm, onCreateConnection = { createClicked = true }, onEditConnection = {}, onBack = {}) }
        }

        onNodeWithTag("create_connection_fab").performClick()
        assertTrue(createClicked)
    }

    // ===========================================================================
    // QA-CONNFORM-1: Dirty-state triggers discard confirmation on Back
    // ===========================================================================

    @Test
    fun `dirty form shows discard confirmation on back`() = runComposeUiTest {
        val vm = ConnectionsViewModel(ConnectionApiClient(connClient("""{"connections":[]}""")), MutableStateFlow(TeamId("test-team")))
        var backCalled = false
        setContent {
            MaterialTheme { ConnectionFormScreen(viewModel = vm, onBack = { backCalled = true }) }
        }

        // Make the form dirty by typing a name
        onNodeWithTag("connection_name_field").performTextInput("Dirty form")
        // Click back
        onNodeWithText("Back").performClick()
        // The discard confirmation should appear instead of navigating back
        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("discard_confirm", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithTag("discard_confirm", useUnmergedTree = true).assertExists()
        assertFalse(backCalled, "Back should not have been called yet")
    }

    // ===========================================================================
    // QA-CONNFORM-2: Discard confirmation "Discard" navigates away
    // ===========================================================================

    @Test
    fun `discard confirmation discard button navigates back`() = runComposeUiTest {
        val vm = ConnectionsViewModel(ConnectionApiClient(connClient("""{"connections":[]}""")), MutableStateFlow(TeamId("test-team")))
        var backCalled = false
        setContent {
            MaterialTheme { ConnectionFormScreen(viewModel = vm, onBack = { backCalled = true }) }
        }

        // Make dirty
        onNodeWithTag("connection_name_field").performTextInput("Dirty form")
        onNodeWithText("Back").performClick()
        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("discard_confirm", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        // Wait for confirm button to be enabled (debounce)
        waitUntil(timeoutMillis = 3000L) {
            onAllNodes(hasTestTag("discard_confirm_confirm") and isEnabled(), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        // Click Discard
        onNodeWithTag("discard_confirm_confirm", useUnmergedTree = true).performClick()
        waitUntil(timeoutMillis = 3000L) { backCalled }
        assertTrue(backCalled)
    }

    // ===========================================================================
    // QA-CONNFORM-3: Discard confirmation "Cancel" keeps user on form
    // ===========================================================================

    @Test
    fun `discard confirmation cancel keeps user on form`() = runComposeUiTest {
        val vm = ConnectionsViewModel(ConnectionApiClient(connClient("""{"connections":[]}""")), MutableStateFlow(TeamId("test-team")))
        var backCalled = false
        setContent {
            MaterialTheme { ConnectionFormScreen(viewModel = vm, onBack = { backCalled = true }) }
        }

        // Make dirty
        onNodeWithTag("connection_name_field").performTextInput("Dirty form")
        onNodeWithText("Back").performClick()
        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("discard_confirm", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        // Click Cancel
        onNodeWithTag("discard_confirm_cancel", useUnmergedTree = true).performClick()
        waitForIdle()
        // Confirmation should disappear
        onNodeWithTag("discard_confirm", useUnmergedTree = true).assertDoesNotExist()
        // Form should still be visible
        onNodeWithTag("connection_form_screen").assertExists()
        onNodeWithTag("connection_name_field").assertTextContains("Dirty form")
        assertFalse(backCalled, "Back should not have been called")
    }

    // ===========================================================================
    // QA-CONNFORM-4: Save error shows error banner
    // ===========================================================================

    @Test
    fun `save error shows error banner on form`() = runComposeUiTest {
        val client = HttpClient(MockEngine { request ->
            val path = request.url.encodedPath
            val method = request.method
            val jsonHeaders = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            when {
                method == HttpMethod.Post && path.endsWith("/connections") -> {
                    respond("""{"error":"Name already taken","code":"CONFLICT"}""", status = HttpStatusCode.Conflict, headers = jsonHeaders)
                }
                path.endsWith("/connections") -> {
                    respond("""{"connections":[]}""", status = HttpStatusCode.OK, headers = jsonHeaders)
                }
                else -> respond("{}", status = HttpStatusCode.OK, headers = jsonHeaders)
            }
        }) {
            install(ContentNegotiation) { json(AppJson) }
            install(HttpCookies)
            expectSuccess = true
        }
        val vm = ConnectionsViewModel(ConnectionApiClient(client), MutableStateFlow(TeamId("test-team")))
        setContent {
            MaterialTheme { ConnectionFormScreen(viewModel = vm, onBack = {}) }
        }

        // Fill out a complete GitHub form
        onNodeWithTag("connection_name_field").performTextInput("Duplicate")
        onNodeWithTag("github_token").performTextInput("ghp_test")
        onNodeWithTag("github_owner").performTextInput("owner")
        onNodeWithTag("github_repo").performTextInput("repo")
        onNodeWithTag("save_connection_button").performClick()
        // Error banner should appear
        waitUntil(timeoutMillis = 5000L) {
            onAllNodesWithTag("connection_form_error_banner").fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithTag("connection_form_error_banner").assertExists()
    }

    // ===========================================================================
    // QA-CONNFORM-5: Error banner dismiss button clears error
    // ===========================================================================

    @Test
    fun `error banner dismiss clears the error`() = runComposeUiTest {
        val client = HttpClient(MockEngine { request ->
            val path = request.url.encodedPath
            val method = request.method
            val jsonHeaders = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            when {
                method == HttpMethod.Post && path.endsWith("/connections") -> {
                    respond("""{"error":"Conflict","code":"CONFLICT"}""", status = HttpStatusCode.Conflict, headers = jsonHeaders)
                }
                path.endsWith("/connections") -> {
                    respond("""{"connections":[]}""", status = HttpStatusCode.OK, headers = jsonHeaders)
                }
                else -> respond("{}", status = HttpStatusCode.OK, headers = jsonHeaders)
            }
        }) {
            install(ContentNegotiation) { json(AppJson) }
            install(HttpCookies)
            expectSuccess = true
        }
        val vm = ConnectionsViewModel(ConnectionApiClient(client), MutableStateFlow(TeamId("test-team")))
        setContent {
            MaterialTheme { ConnectionFormScreen(viewModel = vm, onBack = {}) }
        }

        onNodeWithTag("connection_name_field").performTextInput("Duplicate")
        onNodeWithTag("github_token").performTextInput("ghp_test")
        onNodeWithTag("github_owner").performTextInput("owner")
        onNodeWithTag("github_repo").performTextInput("repo")
        onNodeWithTag("save_connection_button").performClick()
        waitUntil(timeoutMillis = 5000L) {
            onAllNodesWithTag("connection_form_error_banner").fetchSemanticsNodes().isNotEmpty()
        }
        // Click dismiss
        onNodeWithTag("connection_error_dismiss").performClick()
        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("connection_form_error_banner").fetchSemanticsNodes().isEmpty()
        }
        onNodeWithTag("connection_form_error_banner").assertDoesNotExist()
    }

    // ===========================================================================
    // QA-CONNFORM-6: Save button disabled while saving (isSaving)
    // ===========================================================================

    @Test
    fun `save button disabled while saving in progress`() = runComposeUiTest {
        val saveStarted = AtomicBoolean(false)
        val saveCanFinish = AtomicBoolean(false)
        val client = HttpClient(MockEngine { request ->
            val path = request.url.encodedPath
            val method = request.method
            val jsonHeaders = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            when {
                method == HttpMethod.Post && path.endsWith("/connections") -> {
                    saveStarted.set(true)
                    while (!saveCanFinish.get()) { kotlinx.coroutines.yield() }
                    respond("""{"connection":{"id":"c-new","name":"New","type":"GITHUB","config":{"type":"github","token":"t","owner":"o","repo":"r"},"createdAt":"2026-03-13T00:00:00Z","updatedAt":"2026-03-13T00:00:00Z"}}""", status = HttpStatusCode.Created, headers = jsonHeaders)
                }
                path.endsWith("/connections") -> {
                    respond("""{"connections":[]}""", status = HttpStatusCode.OK, headers = jsonHeaders)
                }
                else -> respond("{}", status = HttpStatusCode.OK, headers = jsonHeaders)
            }
        }) {
            install(ContentNegotiation) { json(AppJson) }
            install(HttpCookies)
            expectSuccess = true
        }
        val vm = ConnectionsViewModel(ConnectionApiClient(client), MutableStateFlow(TeamId("test-team")))
        setContent {
            MaterialTheme { ConnectionFormScreen(viewModel = vm, onBack = {}) }
        }

        onNodeWithTag("connection_name_field").performTextInput("New Conn")
        onNodeWithTag("github_token").performTextInput("ghp_test")
        onNodeWithTag("github_owner").performTextInput("owner")
        onNodeWithTag("github_repo").performTextInput("repo")
        onNodeWithTag("save_connection_button").assertIsEnabled()
        onNodeWithTag("save_connection_button").performClick()
        // Wait for saving to start
        waitUntil(timeoutMillis = 3000L) { saveStarted.get() }
        // Save button should be disabled during save
        onNodeWithTag("save_connection_button").assertIsNotEnabled()
        // Button should show "Saving..." text
        onNodeWithText("Saving", substring = true).assertExists()
        // Release the save
        saveCanFinish.set(true)
    }

    // ===========================================================================
    // QA-CONNFORM-7: Polling interval field exists for GitHub type
    // ===========================================================================

    @Test
    fun `github form has polling interval field`() = runComposeUiTest {
        val vm = ConnectionsViewModel(ConnectionApiClient(connClient("""{"connections":[]}""")), MutableStateFlow(TeamId("test-team")))
        setContent {
            MaterialTheme { ConnectionFormScreen(viewModel = vm, onBack = {}) }
        }

        onNodeWithTag("github_polling_interval").assertExists()
        // Default value should be 30
        onNodeWithTag("github_polling_interval").assertTextContains("30")
    }

    // ===========================================================================
    // QA-CONNFORM-8: Polling interval field exists for TeamCity type
    // ===========================================================================

    @Test
    fun `teamcity form has polling interval field`() = runComposeUiTest {
        val vm = ConnectionsViewModel(ConnectionApiClient(connClient("""{"connections":[]}""")), MutableStateFlow(TeamId("test-team")))
        setContent {
            MaterialTheme { ConnectionFormScreen(viewModel = vm, onBack = {}) }
        }

        onNodeWithTag("connection_type_selector").performClick()
        onNodeWithText("TeamCity").performClick()
        onNodeWithTag("teamcity_polling_interval").assertExists()
        onNodeWithTag("teamcity_polling_interval").assertTextContains("30")
    }

    // ===========================================================================
    // QA-CONNFORM-9: Polling interval only accepts digits
    // ===========================================================================

    @Test
    fun `polling interval field only accepts digits`() = runComposeUiTest {
        val vm = ConnectionsViewModel(ConnectionApiClient(connClient("""{"connections":[]}""")), MutableStateFlow(TeamId("test-team")))
        setContent {
            MaterialTheme { ConnectionFormScreen(viewModel = vm, onBack = {}) }
        }

        onNodeWithTag("github_polling_interval").performTextClearance()
        onNodeWithTag("github_polling_interval").performTextInput("abc45xyz")
        // Only digits should remain
        onNodeWithTag("github_polling_interval").assertTextContains("45")
    }

    // ===========================================================================
    // QA-CONNFORM-10: Section header changes when type changes
    // ===========================================================================

    @Test
    fun `section header changes when connection type changes`() = runComposeUiTest {
        val vm = ConnectionsViewModel(ConnectionApiClient(connClient("""{"connections":[]}""")), MutableStateFlow(TeamId("test-team")))
        setContent {
            MaterialTheme { ConnectionFormScreen(viewModel = vm, onBack = {}) }
        }

        // Default is GitHub
        onNodeWithTag("section_header_github", useUnmergedTree = true).assertExists()

        // Switch to Slack
        onNodeWithTag("connection_type_selector").performClick()
        onNodeWithText("Slack").performClick()
        onNodeWithTag("section_header_slack", useUnmergedTree = true).assertExists()
        onNodeWithTag("section_header_github", useUnmergedTree = true).assertDoesNotExist()

        // Switch to TeamCity
        onNodeWithTag("connection_type_selector").performClick()
        onNodeWithText("TeamCity").performClick()
        onNodeWithTag("section_header_teamcity", useUnmergedTree = true).assertExists()
        onNodeWithTag("section_header_slack", useUnmergedTree = true).assertDoesNotExist()
    }

    // ===========================================================================
    // QA-CONNFORM-11: Polling interval hint text is shown
    // ===========================================================================

    @Test
    fun `polling interval shows range hint`() = runComposeUiTest {
        val vm = ConnectionsViewModel(ConnectionApiClient(connClient("""{"connections":[]}""")), MutableStateFlow(TeamId("test-team")))
        setContent {
            MaterialTheme { ConnectionFormScreen(viewModel = vm, onBack = {}) }
        }

        // GitHub polling interval should have the hint
        onNodeWithText("300 seconds", substring = true, useUnmergedTree = true).assertExists()
    }

    // ===========================================================================
    // QA-CONNFORM-12: Clean form back does NOT show discard confirmation
    // ===========================================================================

    @Test
    fun `clean form back navigates without discard confirmation`() = runComposeUiTest {
        val vm = ConnectionsViewModel(ConnectionApiClient(connClient("""{"connections":[]}""")), MutableStateFlow(TeamId("test-team")))
        var backCalled = false
        setContent {
            MaterialTheme { ConnectionFormScreen(viewModel = vm, onBack = { backCalled = true }) }
        }

        // Do not modify any field — form is clean
        onNodeWithText("Back").performClick()
        // Should navigate back immediately
        assertTrue(backCalled, "Back should be called immediately on clean form")
        // Discard dialog should NOT appear
        onNodeWithTag("discard_confirm", useUnmergedTree = true).assertDoesNotExist()
    }

    // ===========================================================================
    // QA-CONNFORM-13: Edit mode dirty state triggers discard confirmation
    // ===========================================================================

    @Test
    fun `edit mode dirty state triggers discard confirmation`() = runComposeUiTest {
        val vm = ConnectionsViewModel(ConnectionApiClient(editClient(githubConnectionJson, "/connections/c1")), MutableStateFlow(TeamId("test-team")))
        var backCalled = false
        setContent {
            MaterialTheme {
                ConnectionFormScreen(viewModel = vm, connectionId = ConnectionId("c1"), onBack = { backCalled = true })
            }
        }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodes(hasTestTag("connection_name_field") and hasText("My GitHub")).fetchSemanticsNodes().isNotEmpty()
        }
        // Modify name to make dirty
        onNodeWithTag("connection_name_field").performTextClearance()
        onNodeWithTag("connection_name_field").performTextInput("Changed Name")
        // Click back
        onNodeWithText("Back").performClick()
        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("discard_confirm", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithTag("discard_confirm", useUnmergedTree = true).assertExists()
        assertFalse(backCalled, "Back should not have been called due to dirty state")
    }

    // ===========================================================================
    // QA-CONNFORM-14: Edit mode clean back navigates without confirmation
    // ===========================================================================

    @Test
    fun `edit mode clean form back navigates without confirmation`() = runComposeUiTest {
        val vm = ConnectionsViewModel(ConnectionApiClient(editClient(githubConnectionJson, "/connections/c1")), MutableStateFlow(TeamId("test-team")))
        var backCalled = false
        setContent {
            MaterialTheme {
                ConnectionFormScreen(viewModel = vm, connectionId = ConnectionId("c1"), onBack = { backCalled = true })
            }
        }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodes(hasTestTag("connection_name_field") and hasText("My GitHub")).fetchSemanticsNodes().isNotEmpty()
        }
        // Do NOT modify anything — form is clean
        onNodeWithText("Back").performClick()
        assertTrue(backCalled, "Back should be called immediately on clean edit form")
        onNodeWithTag("discard_confirm", useUnmergedTree = true).assertDoesNotExist()
    }
}
