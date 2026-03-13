package com.github.mr3zee

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import com.github.mr3zee.api.ConnectionApiClient
import com.github.mr3zee.connections.ConnectionFormScreen
import com.github.mr3zee.connections.ConnectionListScreen
import com.github.mr3zee.connections.ConnectionsViewModel
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
}
