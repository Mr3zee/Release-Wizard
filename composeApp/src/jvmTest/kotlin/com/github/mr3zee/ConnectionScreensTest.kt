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
import kotlin.test.Test
import kotlin.test.assertEquals
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
        waitUntil(timeoutMillis = 3000L) { onAllNodesWithText("Delete Connection").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("Delete Connection").assertExists()
    }

    @Test
    fun `connection list delete confirmation cancel dismisses`() = runComposeUiTest {
        val vm = ConnectionsViewModel(ConnectionApiClient(connClient()), MutableStateFlow(TeamId("test-team")))
        setContent {
            MaterialTheme { ConnectionListScreen(viewModel = vm, onCreateConnection = {}, onEditConnection = {}, onBack = {}) }
        }

        waitUntil(timeoutMillis = 3000L) { onAllNodesWithText("My GitHub").fetchSemanticsNodes().isNotEmpty() }
        onAllNodesWithText("Delete").onFirst().performClick()
        waitUntil(timeoutMillis = 3000L) { onAllNodesWithText("Delete Connection").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("Cancel").performClick()
        onNodeWithText("Delete Connection").assertDoesNotExist()
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

    @Test
    fun `password toggle reveals password text`() = runComposeUiTest {
        val vm = ConnectionsViewModel(ConnectionApiClient(connClient("""{"connections":[]}""")), MutableStateFlow(TeamId("test-team")))
        setContent {
            MaterialTheme { ConnectionFormScreen(viewModel = vm, onBack = {}) }
        }

        // Enter text in the github_token field (GitHub is default type)
        onNodeWithTag("github_token").performTextInput("secret123")
        waitForIdle()

        // Click the toggle visibility button
        onNodeWithTag("github_token_toggle_visibility", useUnmergedTree = true).performClick()
        waitForIdle()

        // After toggling, the field should show the actual text
        onNodeWithTag("github_token").assertTextContains("secret123")
    }
}
