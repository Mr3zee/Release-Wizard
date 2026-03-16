package com.github.mr3zee

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import com.github.mr3zee.api.ConnectionApiClient
import com.github.mr3zee.api.ProjectApiClient
import com.github.mr3zee.editor.DagEditorScreen
import com.github.mr3zee.editor.DagEditorViewModel
import com.github.mr3zee.model.*
import io.ktor.http.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class ExternalConfigDiscoveryTest {

    private val tcBlockProjectJson = """{"project":{"id":"p1","name":"Test Project","description":"","dagGraph":{"blocks":[
        {"kind":"action","id":"b1","name":"TC Build","type":"TEAMCITY_BUILD","parameters":[],"outputs":[],"timeoutSeconds":null,"connectionId":null}
    ],"edges":[],"positions":{"b1":{"x":100,"y":100}}},"parameters":[],"createdAt":"2026-03-13T00:00:00Z","updatedAt":"2026-03-13T00:00:00Z"}}"""

    private val lockJson = """{"userId":"u1","username":"testuser","acquiredAt":"2026-03-13T00:00:00Z","expiresAt":"2026-03-13T00:05:00Z"}"""

    private val connectionsJson = """{"connections":[
        {"id":"conn-1","name":"TC Production","type":"TEAMCITY","config":{"type":"teamcity","serverUrl":"https://tc.example.com","token":"****1234","webhookSecret":""},"createdAt":"2026-03-13T00:00:00Z","updatedAt":"2026-03-13T00:00:00Z"}
    ],"webhookUrls":{},"pagination":{"totalCount":1,"offset":0,"limit":20}}"""

    private val buildTypesJson = """{"configs":[
        {"id":"Proj_Build","name":"Build","path":"Project / Build"},
        {"id":"Proj_Deploy","name":"Deploy","path":"Project / Deploy"}
    ]}"""

    private val parametersJson = """{"parameters":[
        {"name":"env.VERSION","value":"1.0.0","type":"text"},
        {"name":"env.TARGET","value":"staging","type":"select"}
    ]}"""

    private fun editorClient() = mockHttpClient(
        mapOf(
            "/projects/p1" to json(tcBlockProjectJson, method = null),
            "/projects/p1/lock" to json(lockJson, method = HttpMethod.Post),
            "/projects/p1/lock/heartbeat" to json(lockJson, method = HttpMethod.Put),
            "/connections" to json(connectionsJson),
            "/connections/conn-1/teamcity/build-types" to json(buildTypesJson),
            "/connections/conn-1/teamcity/build-types/Proj_Build/parameters" to json(parametersJson),
        )
    )

    private fun editorViewModel(): DagEditorViewModel {
        val client = editorClient()
        return DagEditorViewModel(
            projectId = ProjectId("p1"),
            apiClient = ProjectApiClient(client),
            connectionApiClient = ConnectionApiClient(client),
        )
    }

    // --- ViewModel Unit Tests ---

    @Test
    fun `updateBlockConnectionId clears config state and triggers fetch`() = runComposeUiTest {
        val vm = editorViewModel()
        setContent { MaterialTheme { DagEditorScreen(viewModel = vm, onBack = {}) } }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithText("Test Project").fetchSemanticsNodes().isNotEmpty()
        }

        // Select block
        vm.selectBlock(BlockId("b1"))
        waitForIdle()

        // Set connection
        vm.updateBlockConnectionId(BlockId("b1"), ConnectionId("conn-1"))
        waitForIdle()

        // Should have auto-fetched build types
        waitUntil(timeoutMillis = 3000L) {
            vm.externalConfigs.value[BlockId("b1")]?.isNotEmpty() == true
        }
        val configs = vm.externalConfigs.value[BlockId("b1")]
            ?: error("Expected external configs for block b1")
        assertEquals(2, configs.size)
        assertEquals("Proj_Build", configs[0].id)
    }

    @Test
    fun `selectExternalConfig sets buildTypeId param and fetches parameters`() = runComposeUiTest {
        val vm = editorViewModel()
        setContent { MaterialTheme { DagEditorScreen(viewModel = vm, onBack = {}) } }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithText("Test Project").fetchSemanticsNodes().isNotEmpty()
        }

        vm.selectBlock(BlockId("b1"))
        vm.updateBlockConnectionId(BlockId("b1"), ConnectionId("conn-1"))
        waitForIdle()

        // Wait for configs to load
        waitUntil(timeoutMillis = 3000L) {
            vm.externalConfigs.value[BlockId("b1")]?.isNotEmpty() == true
        }

        // Select a build type
        vm.selectExternalConfig(BlockId("b1"), "Proj_Build")
        waitForIdle()

        // Wait for params to be fetched and merged
        waitUntil(timeoutMillis = 3000L) {
            val block = vm.graph.value.blocks.filterIsInstance<Block.ActionBlock>().find { it.id == BlockId("b1") }
            block?.parameters?.any { it.key == "env.VERSION" } == true
        }

        val block = vm.graph.value.blocks.filterIsInstance<Block.ActionBlock>().find { it.id == BlockId("b1") }
            ?: error("Expected block b1")
        // buildTypeId param should be set
        assertEquals("Proj_Build", block.parameters.find { it.key == "buildTypeId" }?.value)
        // Fetched params should be merged
        assertTrue(block.parameters.any { it.key == "env.VERSION" && it.value == "1.0.0" })
        assertTrue(block.parameters.any { it.key == "env.TARGET" && it.value == "staging" })
    }

    @Test
    fun `changing connection clears build types and config selection`() = runComposeUiTest {
        val vm = editorViewModel()
        setContent { MaterialTheme { DagEditorScreen(viewModel = vm, onBack = {}) } }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithText("Test Project").fetchSemanticsNodes().isNotEmpty()
        }

        vm.selectBlock(BlockId("b1"))
        vm.updateBlockConnectionId(BlockId("b1"), ConnectionId("conn-1"))
        waitForIdle()

        waitUntil(timeoutMillis = 3000L) {
            vm.externalConfigs.value[BlockId("b1")]?.isNotEmpty() == true
        }

        // Select a config
        vm.selectExternalConfig(BlockId("b1"), "Proj_Build")
        waitForIdle()

        // Now clear the connection
        vm.updateBlockConnectionId(BlockId("b1"), null)
        waitForIdle()

        // External configs should be cleared
        assertTrue(vm.externalConfigs.value[BlockId("b1")].isNullOrEmpty())

        // buildTypeId param should be removed
        val block = vm.graph.value.blocks.filterIsInstance<Block.ActionBlock>().find { it.id == BlockId("b1") }
            ?: error("Expected block b1")
        assertNull(block.parameters.find { it.key == "buildTypeId" })
    }

    @Test
    fun `removeSelectedBlocks cleans up external config state`() = runComposeUiTest {
        val vm = editorViewModel()
        setContent { MaterialTheme { DagEditorScreen(viewModel = vm, onBack = {}) } }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithText("Test Project").fetchSemanticsNodes().isNotEmpty()
        }

        vm.selectBlock(BlockId("b1"))
        vm.updateBlockConnectionId(BlockId("b1"), ConnectionId("conn-1"))
        waitForIdle()

        waitUntil(timeoutMillis = 3000L) {
            vm.externalConfigs.value[BlockId("b1")]?.isNotEmpty() == true
        }

        // Delete the block
        vm.removeSelectedBlocks()
        waitForIdle()

        // Config state should be cleaned up
        assertTrue(vm.externalConfigs.value[BlockId("b1")].isNullOrEmpty())
        assertTrue(BlockId("b1") !in vm.isFetchingConfigs.value)
        assertTrue(BlockId("b1") !in vm.isFetchingConfigParams.value)
    }

    // --- UI Tests ---

    @Test
    fun `connection selector appears for teamcity block`() = runComposeUiTest {
        val vm = editorViewModel()
        setContent { MaterialTheme { DagEditorScreen(viewModel = vm, onBack = {}) } }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithText("Test Project").fetchSemanticsNodes().isNotEmpty()
        }

        // Select the TC block programmatically
        vm.selectBlock(BlockId("b1"))
        waitForIdle()

        // Connection selector should appear
        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("block_connection_selector").fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithTag("block_connection_selector").assertExists()
    }

    @Test
    fun `config selector appears after connection is set`() = runComposeUiTest {
        val vm = editorViewModel()
        setContent { MaterialTheme { DagEditorScreen(viewModel = vm, onBack = {}) } }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithText("Test Project").fetchSemanticsNodes().isNotEmpty()
        }

        // Set connection programmatically (simulating dropdown selection)
        vm.selectBlock(BlockId("b1"))
        vm.updateBlockConnectionId(BlockId("b1"), ConnectionId("conn-1"))
        waitForIdle()

        // Config selector should appear
        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("config_selector_field", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithTag("config_selector_field", useUnmergedTree = true).assertExists()
        onNodeWithTag("refresh_configs_button", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `parameter refresh button appears when config is selected`() = runComposeUiTest {
        val vm = editorViewModel()
        setContent { MaterialTheme { DagEditorScreen(viewModel = vm, onBack = {}) } }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithText("Test Project").fetchSemanticsNodes().isNotEmpty()
        }

        vm.selectBlock(BlockId("b1"))
        vm.updateBlockConnectionId(BlockId("b1"), ConnectionId("conn-1"))
        waitForIdle()

        waitUntil(timeoutMillis = 3000L) {
            vm.externalConfigs.value[BlockId("b1")]?.isNotEmpty() == true
        }

        vm.selectExternalConfig(BlockId("b1"), "Proj_Build")
        waitForIdle()

        // Refresh parameters button should appear
        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("refresh_parameters_button", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithTag("refresh_parameters_button", useUnmergedTree = true).assertExists()
    }
}
