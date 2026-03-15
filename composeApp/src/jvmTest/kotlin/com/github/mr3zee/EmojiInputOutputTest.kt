package com.github.mr3zee

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import com.github.mr3zee.api.AuthApiClient
import com.github.mr3zee.api.ConnectionApiClient
import com.github.mr3zee.auth.AuthViewModel
import com.github.mr3zee.auth.LoginScreen
import com.github.mr3zee.connections.ConnectionFormScreen
import com.github.mr3zee.connections.ConnectionsViewModel
import com.github.mr3zee.editor.TemplateAutocompleteField
import com.github.mr3zee.model.*
import io.ktor.http.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class EmojiInputOutputTest {

    // ---- Login screen emoji tests ----

    @Test
    fun `login username accepts emoji characters`() = runComposeUiTest {
        val client = mockHttpClient(
            mapOf("/auth/login" to json("""{"username":"admin"}"""))
        )
        val viewModel = AuthViewModel(AuthApiClient(client))
        setContent { MaterialTheme { LoginScreen(viewModel = viewModel) } }

        onNodeWithTag("login_username").performTextInput("admin\uD83D\uDE80")
        onNodeWithTag("login_username").assertTextContains("admin\uD83D\uDE80")
    }

    @Test
    fun `login password accepts emoji characters`() = runComposeUiTest {
        val client = mockHttpClient(
            mapOf("/auth/login" to json("""{"username":"admin"}"""))
        )
        val viewModel = AuthViewModel(AuthApiClient(client))
        setContent { MaterialTheme { LoginScreen(viewModel = viewModel) } }

        onNodeWithTag("login_username").performTextInput("user")
        onNodeWithTag("login_password").performTextInput("pass\uD83D\uDD12")
        onNodeWithTag("login_button").assertIsEnabled()
    }

    @Test
    fun `login enables button with emoji-only username and password`() = runComposeUiTest {
        val client = mockHttpClient(
            mapOf("/auth/login" to json("""{"username":"admin"}"""))
        )
        val viewModel = AuthViewModel(AuthApiClient(client))
        setContent { MaterialTheme { LoginScreen(viewModel = viewModel) } }

        onNodeWithTag("login_username").performTextInput("\uD83D\uDC64")
        onNodeWithTag("login_password").performTextInput("\uD83D\uDD11")
        onNodeWithTag("login_button").assertIsEnabled()
    }

    // ---- Connection form emoji tests ----

    @Test
    fun `connection name accepts emoji characters`() = runComposeUiTest {
        val vm = ConnectionsViewModel(
            ConnectionApiClient(mockHttpClient(mapOf("/connections" to json("""{"connections":[]}""")))),
            MutableStateFlow(TeamId("test-team")),
        )
        setContent { MaterialTheme { ConnectionFormScreen(viewModel = vm, onBack = {}) } }

        onNodeWithTag("connection_name_field").performTextInput("My \uD83D\uDE80 Deploy")
        onNodeWithTag("connection_name_field").assertTextContains("My \uD83D\uDE80 Deploy")
    }

    @Test
    fun `connection form validates with emoji name`() = runComposeUiTest {
        val vm = ConnectionsViewModel(
            ConnectionApiClient(mockHttpClient(mapOf("/connections" to json("""{"connections":[]}""")))),
            MutableStateFlow(TeamId("test-team")),
        )
        setContent { MaterialTheme { ConnectionFormScreen(viewModel = vm, onBack = {}) } }

        // Select Slack type
        onNodeWithTag("connection_type_selector").performClick()
        onNodeWithText("Slack").performClick()

        onNodeWithTag("connection_name_field").performTextInput("\uD83D\uDCE2 Alerts")
        onNodeWithTag("slack_webhook_url").performTextInput("https://hooks.slack.com/test")
        onNodeWithTag("save_connection_button").assertIsEnabled()
    }

    @Test
    fun `connection list displays emoji in connection name`() = runComposeUiTest {
        val connectionsJson = """{"connections":[
            {"id":"c1","name":"\uD83D\uDE80 Deploy Bot","type":"SLACK","config":{"type":"slack","webhookUrl":"https://hooks.slack.com/test"},"createdAt":"2026-03-13T00:00:00Z","updatedAt":"2026-03-13T00:00:00Z"}
        ]}"""
        val vm = ConnectionsViewModel(
            ConnectionApiClient(mockHttpClient(mapOf("/connections" to json(connectionsJson)))),
            MutableStateFlow(TeamId("test-team")),
        )
        setContent {
            MaterialTheme {
                com.github.mr3zee.connections.ConnectionListScreen(
                    viewModel = vm,
                    onCreateConnection = {},
                    onEditConnection = {},
                    onBack = {},
                )
            }
        }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithText("\uD83D\uDE80 Deploy Bot").fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithText("\uD83D\uDE80 Deploy Bot").assertExists()
    }

    // ---- Template autocomplete emoji tests ----

    @Test
    fun `plain emoji text does not trigger dropdown`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                var value by remember { mutableStateOf("") }
                TemplateAutocompleteField(
                    value = value,
                    onValueChange = { value = it },
                    projectParameters = listOf(Parameter(key = "version", value = "1.0")),
                    predecessors = emptyList(),
                    testTag = "field",
                )
            }
        }

        onNodeWithTag("field").performTextInput("\uD83C\uDF89 release party")
        waitForIdle()

        onNodeWithTag("field_autocomplete_dropdown", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun `trigger after emoji text opens dropdown`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                var value by remember { mutableStateOf("") }
                TemplateAutocompleteField(
                    value = value,
                    onValueChange = { value = it },
                    projectParameters = listOf(Parameter(key = "version", value = "1.0")),
                    predecessors = emptyList(),
                    testTag = "field",
                )
            }
        }

        onNodeWithTag("field").performTextInput("\uD83D\uDE80 \${")
        waitForIdle()

        onNodeWithTag("field_autocomplete_dropdown", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `selecting suggestion after emoji preserves emoji prefix`() = runComposeUiTest {
        var currentValue = ""
        setContent {
            MaterialTheme {
                var value by remember { mutableStateOf("") }
                TemplateAutocompleteField(
                    value = value,
                    onValueChange = {
                        value = it
                        currentValue = it
                    },
                    projectParameters = listOf(Parameter(key = "version", value = "1.0")),
                    predecessors = emptyList(),
                    testTag = "field",
                )
            }
        }

        onNodeWithTag("field").performTextInput("\uD83D\uDE80 \${")
        waitForIdle()

        onNodeWithTag("field_suggestion_0", useUnmergedTree = true).performClick()
        waitForIdle()

        assertEquals("\uD83D\uDE80 \${param.version}", currentValue)
    }

    @Test
    fun `parameters with emoji keys appear in suggestions`() = runComposeUiTest {
        val emojiParams = listOf(
            Parameter(key = "\uD83D\uDE80 deploy_env", value = "staging"),
            Parameter(key = "version", value = "2.0"),
        )
        setContent {
            MaterialTheme {
                var value by remember { mutableStateOf("") }
                TemplateAutocompleteField(
                    value = value,
                    onValueChange = { value = it },
                    projectParameters = emojiParams,
                    predecessors = emptyList(),
                    testTag = "field",
                )
            }
        }

        onNodeWithTag("field").performTextInput("\${")
        waitForIdle()

        onNodeWithTag("field_autocomplete_dropdown", useUnmergedTree = true).assertExists()
        // Both suggestions should be visible
        onNodeWithTag("field_suggestion_0", useUnmergedTree = true).assertExists()
        onNodeWithTag("field_suggestion_1", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `selecting parameter with emoji key inserts correctly`() = runComposeUiTest {
        var currentValue = ""
        val emojiParams = listOf(
            Parameter(key = "\uD83D\uDE80 deploy_env", value = "staging"),
        )
        setContent {
            MaterialTheme {
                var value by remember { mutableStateOf("") }
                TemplateAutocompleteField(
                    value = value,
                    onValueChange = {
                        value = it
                        currentValue = it
                    },
                    projectParameters = emojiParams,
                    predecessors = emptyList(),
                    testTag = "field",
                )
            }
        }

        onNodeWithTag("field").performTextInput("\${")
        waitForIdle()

        onNodeWithTag("field_suggestion_0", useUnmergedTree = true).performClick()
        waitForIdle()

        assertEquals("\${param.\uD83D\uDE80 deploy_env}", currentValue)
    }

    @Test
    fun `block outputs with emoji names shown in suggestions`() = runComposeUiTest {
        val predecessors = listOf(
            Block.ActionBlock(
                id = BlockId("b1"),
                name = "\uD83D\uDEE0\uFE0F Build Step",
                type = BlockType.TEAMCITY_BUILD,
                outputs = listOf("\uD83D\uDCE6 artifact"),
            ),
        )
        setContent {
            MaterialTheme {
                var value by remember { mutableStateOf("") }
                TemplateAutocompleteField(
                    value = value,
                    onValueChange = { value = it },
                    projectParameters = emptyList(),
                    predecessors = predecessors,
                    testTag = "field",
                )
            }
        }

        onNodeWithTag("field").performTextInput("\${")
        waitForIdle()

        onNodeWithTag("field_autocomplete_dropdown", useUnmergedTree = true).assertExists()
        onNodeWithText("Block Outputs", useUnmergedTree = true).assertExists()
        onNodeWithTag("field_suggestion_0", useUnmergedTree = true).assertExists()
    }

    // ---- Mixed emoji + template interpolation ----

    @Test
    fun `complex emoji string with multiple surrogates accepted in field`() = runComposeUiTest {
        var currentValue = ""
        setContent {
            MaterialTheme {
                var value by remember { mutableStateOf("") }
                TemplateAutocompleteField(
                    value = value,
                    onValueChange = {
                        value = it
                        currentValue = it
                    },
                    projectParameters = emptyList(),
                    predecessors = emptyList(),
                    testTag = "field",
                )
            }
        }

        // String with multiple emoji: rocket, fire, sparkles, check mark
        val emojiString = "\uD83D\uDE80\uD83D\uDD25\u2728\u2705 Release v2.0"
        onNodeWithTag("field").performTextInput(emojiString)
        waitForIdle()

        assertEquals(emojiString, currentValue)
    }

    @Test
    fun `emoji-only value accepted in field`() = runComposeUiTest {
        var currentValue = ""
        setContent {
            MaterialTheme {
                var value by remember { mutableStateOf("") }
                TemplateAutocompleteField(
                    value = value,
                    onValueChange = {
                        value = it
                        currentValue = it
                    },
                    projectParameters = emptyList(),
                    predecessors = emptyList(),
                    testTag = "field",
                )
            }
        }

        val emojis = "\uD83C\uDF89\uD83C\uDF8A\uD83C\uDF88"
        onNodeWithTag("field").performTextInput(emojis)
        waitForIdle()

        assertEquals(emojis, currentValue)
    }

    // ---- DAG editor emoji tests ----

    private val projectJson = """{"project":{"id":"p1","name":"Test Project","description":"","dagGraph":{"blocks":[
        {"kind":"action","id":"b1","name":"Build","type":"TEAMCITY_BUILD","parameters":[],"outputs":[],"timeoutSeconds":null,"connectionId":null}
    ],"edges":[],"positions":{"b1":{"x":100,"y":100}}},"parameters":[],"createdAt":"2026-03-13T00:00:00Z","updatedAt":"2026-03-13T00:00:00Z"}}"""

    private val lockJson = """{"userId":"u1","username":"testuser","acquiredAt":"2026-03-13T00:00:00Z","expiresAt":"2026-03-13T00:05:00Z"}"""

    private fun editorClient() = mockHttpClient(
        mapOf(
            "/projects/p1" to json(projectJson, method = null),
            "/projects/p1/lock" to json(lockJson, HttpStatusCode.OK, method = HttpMethod.Post),
            "/projects/p1/lock/heartbeat" to json(lockJson, HttpStatusCode.OK, method = HttpMethod.Put),
        )
    )

    @Test
    fun `editing block name with emoji in DAG editor`() = runComposeUiTest {
        val vm = com.github.mr3zee.editor.DagEditorViewModel(
            ProjectId("p1"),
            com.github.mr3zee.api.ProjectApiClient(editorClient()),
        )
        setContent {
            MaterialTheme {
                com.github.mr3zee.editor.DagEditorScreen(viewModel = vm, onBack = {})
            }
        }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithText("Test Project").fetchSemanticsNodes().isNotEmpty()
        }

        // Select block b1 by clicking on canvas at its position
        onNodeWithTag("dag_canvas").performTouchInput {
            click(androidx.compose.ui.geometry.Offset(190f, 135f))
        }
        waitForIdle()

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("block_name_field").fetchSemanticsNodes().isNotEmpty()
        }

        onNodeWithTag("block_name_field").performTextClearance()
        onNodeWithTag("block_name_field").performTextInput("\uD83D\uDEE0\uFE0F Build Step")
        waitForIdle()

        onNodeWithTag("block_name_field").assertTextContains("\uD83D\uDEE0\uFE0F Build Step")
        // Graph should be dirty
        onNodeWithTag("save_button").assertIsEnabled()
    }

    // ---- Project/release display with emoji ----

    @Test
    fun `project with emoji name displays correctly`() = runComposeUiTest {
        val emojiProjectJson = """{"project":{"id":"p1","name":"\uD83D\uDE80 Awesome Release","description":"\uD83C\uDF1F Star project","dagGraph":{"blocks":[],"edges":[],"positions":{}},"parameters":[],"createdAt":"2026-03-13T00:00:00Z","updatedAt":"2026-03-13T00:00:00Z"}}"""
        val vm = com.github.mr3zee.editor.DagEditorViewModel(
            ProjectId("p1"),
            com.github.mr3zee.api.ProjectApiClient(
                mockHttpClient(
                    mapOf(
                        "/projects/p1" to json(emojiProjectJson, method = null),
                        "/projects/p1/lock" to json(lockJson, HttpStatusCode.OK, method = HttpMethod.Post),
                        "/projects/p1/lock/heartbeat" to json(lockJson, HttpStatusCode.OK, method = HttpMethod.Put),
                    )
                )
            ),
        )
        setContent {
            MaterialTheme {
                com.github.mr3zee.editor.DagEditorScreen(viewModel = vm, onBack = {})
            }
        }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithText("\uD83D\uDE80 Awesome Release").fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithText("\uD83D\uDE80 Awesome Release").assertExists()
    }
}
