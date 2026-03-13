package com.github.mr3zee

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import com.github.mr3zee.api.ConnectionApiClient
import com.github.mr3zee.connections.ConnectionFormScreen
import com.github.mr3zee.connections.ConnectionListScreen
import com.github.mr3zee.connections.ConnectionsViewModel
import com.github.mr3zee.model.ConnectionId
import io.ktor.http.*
import kotlin.test.Test
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
        val vm = ConnectionsViewModel(ConnectionApiClient(connClient()))
        setContent {
            MaterialTheme { ConnectionListScreen(viewModel = vm, onCreateConnection = {}, onEditConnection = {}, onBack = {}) }
        }

        waitUntil(timeoutMillis = 3000L) { onAllNodesWithText("My GitHub").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("connection_list_screen").assertExists()
        onNodeWithText("My GitHub").assertExists()
        onNodeWithText("GITHUB").assertExists()
        onNodeWithText("Team Slack").assertExists()
        onNodeWithText("SLACK").assertExists()
    }

    @Test
    fun `connection list shows empty state`() = runComposeUiTest {
        val vm = ConnectionsViewModel(ConnectionApiClient(connClient("""{"connections":[]}""")))
        setContent {
            MaterialTheme { ConnectionListScreen(viewModel = vm, onCreateConnection = {}, onEditConnection = {}, onBack = {}) }
        }

        waitUntil(timeoutMillis = 3000L) { onAllNodesWithText("No connections yet", substring = true).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("No connections yet. Add one to get started.").assertExists()
    }

    @Test
    fun `connection list has create fab and back button`() = runComposeUiTest {
        val vm = ConnectionsViewModel(ConnectionApiClient(connClient("""{"connections":[]}""")))
        setContent {
            MaterialTheme { ConnectionListScreen(viewModel = vm, onCreateConnection = {}, onEditConnection = {}, onBack = {}) }
        }

        onNodeWithTag("create_connection_fab").assertExists()
        onNodeWithText("Back").assertExists()
    }

    @Test
    fun `connection items have test and delete buttons`() = runComposeUiTest {
        val vm = ConnectionsViewModel(ConnectionApiClient(connClient()))
        setContent {
            MaterialTheme { ConnectionListScreen(viewModel = vm, onCreateConnection = {}, onEditConnection = {}, onBack = {}) }
        }

        waitUntil(timeoutMillis = 3000L) { onAllNodesWithText("My GitHub").fetchSemanticsNodes().isNotEmpty() }
        onAllNodesWithText("Test").assertCountEquals(2)
        onAllNodesWithText("Delete").assertCountEquals(2)
    }

    @Test
    fun `connection form renders with GitHub fields by default`() = runComposeUiTest {
        val vm = ConnectionsViewModel(ConnectionApiClient(connClient("""{"connections":[]}""")))
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
        val vm = ConnectionsViewModel(ConnectionApiClient(connClient("""{"connections":[]}""")))
        setContent {
            MaterialTheme { ConnectionFormScreen(viewModel = vm, onBack = {}) }
        }

        onNodeWithTag("save_connection_button").assertIsNotEnabled()
        onNodeWithTag("connection_name_field").performTextInput("My Conn")
        onNodeWithTag("save_connection_button").assertIsNotEnabled()
    }

    @Test
    fun `save button enables when form complete`() = runComposeUiTest {
        val vm = ConnectionsViewModel(ConnectionApiClient(connClient("""{"connections":[]}""")))
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
        val vm = ConnectionsViewModel(ConnectionApiClient(connClient("""{"connections":[]}""")))
        var backClicked = false
        setContent {
            MaterialTheme { ConnectionListScreen(viewModel = vm, onCreateConnection = {}, onEditConnection = {}, onBack = { backClicked = true }) }
        }

        onNodeWithText("Back").performClick()
        assertTrue(backClicked)
    }

    @Test
    fun `connection list delete opens confirmation dialog`() = runComposeUiTest {
        val vm = ConnectionsViewModel(ConnectionApiClient(connClient()))
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
        val vm = ConnectionsViewModel(ConnectionApiClient(connClient()))
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
        val vm = ConnectionsViewModel(ConnectionApiClient(connClient("""{"connections":[]}""")))
        setContent {
            MaterialTheme { ConnectionFormScreen(viewModel = vm, onBack = {}) }
        }

        onNodeWithTag("connection_type_selector").performClick()
        onNodeWithText("SLACK").performClick()
        onNodeWithTag("slack_webhook_url").assertExists()
        onNodeWithTag("github_token").assertDoesNotExist()
    }

    @Test
    fun `connection form TeamCity type shows server and token fields`() = runComposeUiTest {
        val vm = ConnectionsViewModel(ConnectionApiClient(connClient("""{"connections":[]}""")))
        setContent {
            MaterialTheme { ConnectionFormScreen(viewModel = vm, onBack = {}) }
        }

        onNodeWithTag("connection_type_selector").performClick()
        onNodeWithText("TEAMCITY").performClick()
        onNodeWithTag("teamcity_server_url").assertExists()
        onNodeWithTag("teamcity_token").assertExists()
    }

    @Test
    fun `connection form Maven Central type shows username and password`() = runComposeUiTest {
        val vm = ConnectionsViewModel(ConnectionApiClient(connClient("""{"connections":[]}""")))
        setContent {
            MaterialTheme { ConnectionFormScreen(viewModel = vm, onBack = {}) }
        }

        onNodeWithTag("connection_type_selector").performClick()
        onNodeWithText("MAVEN_CENTRAL").performClick()
        onNodeWithTag("maven_username").assertExists()
        onNodeWithTag("maven_password").assertExists()
    }

    @Test
    fun `connection form Slack validation requires webhook URL`() = runComposeUiTest {
        val vm = ConnectionsViewModel(ConnectionApiClient(connClient("""{"connections":[]}""")))
        setContent {
            MaterialTheme { ConnectionFormScreen(viewModel = vm, onBack = {}) }
        }

        onNodeWithTag("connection_type_selector").performClick()
        onNodeWithText("SLACK").performClick()

        onNodeWithTag("connection_name_field").performTextInput("My Slack")
        onNodeWithTag("save_connection_button").assertIsNotEnabled()

        onNodeWithTag("slack_webhook_url").performTextInput("https://hooks.slack.com/test")
        onNodeWithTag("save_connection_button").assertIsEnabled()
    }

    @Test
    fun `connection form TeamCity validation requires both fields`() = runComposeUiTest {
        val vm = ConnectionsViewModel(ConnectionApiClient(connClient("""{"connections":[]}""")))
        setContent {
            MaterialTheme { ConnectionFormScreen(viewModel = vm, onBack = {}) }
        }

        onNodeWithTag("connection_type_selector").performClick()
        onNodeWithText("TEAMCITY").performClick()

        onNodeWithTag("connection_name_field").performTextInput("My TC")
        onNodeWithTag("teamcity_server_url").performTextInput("https://tc.example.com")
        // Only server URL — save still disabled
        onNodeWithTag("save_connection_button").assertIsNotEnabled()

        onNodeWithTag("teamcity_token").performTextInput("token123")
        onNodeWithTag("save_connection_button").assertIsEnabled()
    }

    @Test
    fun `connection form Maven Central validation requires both fields`() = runComposeUiTest {
        val vm = ConnectionsViewModel(ConnectionApiClient(connClient("""{"connections":[]}""")))
        setContent {
            MaterialTheme { ConnectionFormScreen(viewModel = vm, onBack = {}) }
        }

        onNodeWithTag("connection_type_selector").performClick()
        onNodeWithText("MAVEN_CENTRAL").performClick()

        onNodeWithTag("connection_name_field").performTextInput("Maven")
        onNodeWithTag("maven_username").performTextInput("user")
        onNodeWithTag("save_connection_button").assertIsNotEnabled()

        onNodeWithTag("maven_password").performTextInput("pass")
        onNodeWithTag("save_connection_button").assertIsEnabled()
    }

    @Test
    fun `connection form back button triggers callback`() = runComposeUiTest {
        val vm = ConnectionsViewModel(ConnectionApiClient(connClient("""{"connections":[]}""")))
        var backClicked = false
        setContent {
            MaterialTheme { ConnectionFormScreen(viewModel = vm, onBack = { backClicked = true }) }
        }

        onNodeWithText("Back").performClick()
        assertTrue(backClicked)
    }

    @Test
    fun `connection list error state shows snackbar`() = runComposeUiTest {
        val vm = ConnectionsViewModel(ConnectionApiClient(connClient("error", HttpStatusCode.InternalServerError)))
        setContent {
            MaterialTheme { ConnectionListScreen(viewModel = vm, onCreateConnection = {}, onEditConnection = {}, onBack = {}) }
        }

        waitUntil(timeoutMillis = 3000L) { onAllNodesWithText("Retry").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("Retry").assertExists()
    }

    @Test
    fun `connection form GitHub requires all three fields`() = runComposeUiTest {
        val vm = ConnectionsViewModel(ConnectionApiClient(connClient("""{"connections":[]}""")))
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

    private val githubConnectionJson = """{"connection":{"id":"c1","name":"My GitHub","type":"GITHUB","config":{"type":"github","token":"ghp_secret","owner":"mr3zee","repo":"release-wizard","webhookSecret":""},"createdAt":"2026-03-13T00:00:00Z","updatedAt":"2026-03-13T00:00:00Z"}}"""

    private val slackConnectionJson = """{"connection":{"id":"c2","name":"Team Slack","type":"SLACK","config":{"type":"slack","webhookUrl":"https://hooks.slack.com/test"},"createdAt":"2026-03-13T00:00:00Z","updatedAt":"2026-03-13T00:00:00Z"}}"""

    private val teamCityConnectionJson = """{"connection":{"id":"c3","name":"My TC","type":"TEAMCITY","config":{"type":"teamcity","serverUrl":"https://tc.example.com","token":"tc_token","webhookSecret":"secret123"},"createdAt":"2026-03-13T00:00:00Z","updatedAt":"2026-03-13T00:00:00Z"}}"""

    private val mavenConnectionJson = """{"connection":{"id":"c4","name":"Maven Repo","type":"MAVEN_CENTRAL","config":{"type":"maven_central","username":"user1","password":"pass1","baseUrl":"https://central.sonatype.com"},"createdAt":"2026-03-13T00:00:00Z","updatedAt":"2026-03-13T00:00:00Z"}}"""

    private fun editClient(connectionJson: String, connectionPath: String) = mockHttpClient(
        mapOf(
            "/connections" to json("""{"connections":[]}"""),
            connectionPath to json(connectionJson),
        )
    )

    @Test
    fun `edit form shows Edit Connection title`() = runComposeUiTest {
        val vm = ConnectionsViewModel(ConnectionApiClient(editClient(githubConnectionJson, "/connections/c1")))
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
        val vm = ConnectionsViewModel(ConnectionApiClient(editClient(githubConnectionJson, "/connections/c1")))
        setContent {
            MaterialTheme {
                ConnectionFormScreen(viewModel = vm, connectionId = ConnectionId("c1"), onBack = {})
            }
        }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodes(hasTestTag("connection_name_field") and hasText("My GitHub")).fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithTag("connection_name_field").assertTextContains("My GitHub")
        onNodeWithTag("connection_type_selector").assertTextContains("GITHUB")
        onNodeWithTag("github_owner").assertTextContains("mr3zee")
        onNodeWithTag("github_repo").assertTextContains("release-wizard")
    }

    @Test
    fun `edit form pre-populates Slack connection fields`() = runComposeUiTest {
        val vm = ConnectionsViewModel(ConnectionApiClient(editClient(slackConnectionJson, "/connections/c2")))
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
        val vm = ConnectionsViewModel(ConnectionApiClient(editClient(teamCityConnectionJson, "/connections/c3")))
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
    fun `edit form pre-populates Maven Central connection fields`() = runComposeUiTest {
        val vm = ConnectionsViewModel(ConnectionApiClient(editClient(mavenConnectionJson, "/connections/c4")))
        setContent {
            MaterialTheme {
                ConnectionFormScreen(viewModel = vm, connectionId = ConnectionId("c4"), onBack = {})
            }
        }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodes(hasTestTag("connection_name_field") and hasText("Maven Repo")).fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithTag("connection_name_field").assertTextContains("Maven Repo")
        onNodeWithTag("maven_username").assertTextContains("user1")
        onNodeWithTag("maven_base_url").assertTextContains("https://central.sonatype.com")
    }

    @Test
    fun `edit form disables type selector`() = runComposeUiTest {
        val vm = ConnectionsViewModel(ConnectionApiClient(editClient(githubConnectionJson, "/connections/c1")))
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
        val vm = ConnectionsViewModel(ConnectionApiClient(editClient(githubConnectionJson, "/connections/c1")))
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
        val vm = ConnectionsViewModel(ConnectionApiClient(editClient(githubConnectionJson, "/connections/c1")))
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
        assertTrue(backCalled)
    }

    @Test
    fun `edit form allows editing name`() = runComposeUiTest {
        val vm = ConnectionsViewModel(ConnectionApiClient(editClient(githubConnectionJson, "/connections/c1")))
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
        val vm = ConnectionsViewModel(ConnectionApiClient(connClient("""{"connections":[]}""")))
        setContent {
            MaterialTheme { ConnectionFormScreen(viewModel = vm, onBack = {}) }
        }

        onNodeWithText("New Connection").assertExists()
    }

    @Test
    fun `create form type selector is enabled`() = runComposeUiTest {
        val vm = ConnectionsViewModel(ConnectionApiClient(connClient("""{"connections":[]}""")))
        setContent {
            MaterialTheme { ConnectionFormScreen(viewModel = vm, onBack = {}) }
        }

        onNodeWithTag("connection_type_selector").assertIsEnabled()
    }

    @Test
    fun `clicking connection item triggers edit callback`() = runComposeUiTest {
        val vm = ConnectionsViewModel(ConnectionApiClient(connClient()))
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
        assertTrue(editedId?.value == "c1")
    }
}
