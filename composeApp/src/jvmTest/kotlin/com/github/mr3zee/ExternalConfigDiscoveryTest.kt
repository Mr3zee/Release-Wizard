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
        {"id":"conn-1","name":"TC Production","type":"TEAMCITY","config":{"type":"teamcity","serverUrl":"https://tc.example.com","token":"****1234","pollingIntervalSeconds":30},"createdAt":"2026-03-13T00:00:00Z","updatedAt":"2026-03-13T00:00:00Z"}
    ],"webhookUrls":{},"pagination":{"totalCount":1,"offset":0,"limit":20}}"""

    private val buildTypesJson = """{"configs":[
        {"id":"Proj_Build","name":"Build","path":"Project / Build"},
        {"id":"Proj_Deploy","name":"Deploy","path":"Project / Deploy"}
    ]}"""

    private val parametersJson = """{"parameters":[
        {"name":"env.VERSION","value":"1.0.0","type":"text","label":"Version","description":"Release version"},
        {"name":"env.TARGET","value":"staging","type":"select","label":"","description":"Deploy target"}
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
        // Fetched params should be merged with label/description
        val versionParam = block.parameters.find { it.key == "env.VERSION" }
            ?: error("Expected env.VERSION parameter")
        assertEquals("1.0.0", versionParam.value)
        assertEquals("Version", versionParam.label)
        assertEquals("Release version", versionParam.description)

        val targetParam = block.parameters.find { it.key == "env.TARGET" }
            ?: error("Expected env.TARGET parameter")
        assertEquals("staging", targetParam.value)
        assertEquals("", targetParam.label)
        assertEquals("Deploy target", targetParam.description)
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

    @Test
    fun `re-fetch preserves user-edited values while updating label and description`() = runComposeUiTest {
        // Use a parameters response with updated metadata on re-fetch
        val updatedParametersJson = """{"parameters":[
            {"name":"env.VERSION","value":"1.0.0","type":"text","label":"Release Version","description":"Updated release version description"},
            {"name":"env.TARGET","value":"staging","type":"select","label":"Target Env","description":"Updated deploy target"},
            {"name":"env.NEW_PARAM","value":"default","type":"text","label":"New","description":"Brand new param"}
        ]}"""

        var fetchCount = 0
        val client = mockHttpClient(
            mapOf(
                "/projects/p1" to json(tcBlockProjectJson, method = null),
                "/projects/p1/lock" to json(lockJson, method = HttpMethod.Post),
                "/projects/p1/lock/heartbeat" to json(lockJson, method = HttpMethod.Put),
                "/connections" to json(connectionsJson),
                "/connections/conn-1/teamcity/build-types" to json(buildTypesJson),
                // First fetch returns original params, second returns updated metadata + new param
                "/connections/conn-1/teamcity/build-types/Proj_Build/parameters" to json(parametersJson),
            )
        )

        // We need a custom client that switches responses — but the mock client
        // doesn't support that. Instead, we set up initial state, edit values,
        // then swap to a new VM with updated response.
        // Actually, the simplest approach: use one VM, edit values, then re-fetch.
        // The mock always returns the same parametersJson, so label/description
        // will stay the same but user values should be preserved.
        // Let's use the updatedParametersJson instead for the route.
        val refetchClient = mockHttpClient(
            mapOf(
                "/projects/p1" to json(tcBlockProjectJson, method = null),
                "/projects/p1/lock" to json(lockJson, method = HttpMethod.Post),
                "/projects/p1/lock/heartbeat" to json(lockJson, method = HttpMethod.Put),
                "/connections" to json(connectionsJson),
                "/connections/conn-1/teamcity/build-types" to json(buildTypesJson),
                "/connections/conn-1/teamcity/build-types/Proj_Build/parameters" to json(updatedParametersJson),
            )
        )

        val vm = DagEditorViewModel(
            projectId = ProjectId("p1"),
            apiClient = ProjectApiClient(client),
            connectionApiClient = ConnectionApiClient(refetchClient),
        )
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

        // Select config to trigger initial param fetch
        vm.selectExternalConfig(BlockId("b1"), "Proj_Build")
        waitForIdle()

        waitUntil(timeoutMillis = 3000L) {
            val block = vm.graph.value.blocks.filterIsInstance<Block.ActionBlock>().find { it.id == BlockId("b1") }
            block?.parameters?.any { it.key == "env.VERSION" } == true
        }

        // Edit user values
        val block1 = vm.graph.value.blocks.filterIsInstance<Block.ActionBlock>().find { it.id == BlockId("b1") }
            ?: error("Expected block b1")
        val editedParams = block1.parameters.map { p ->
            when (p.key) {
                "env.VERSION" -> p.copy(value = "2.0.0-user-edit")
                "env.TARGET" -> p.copy(value = "production")
                else -> p
            }
        }
        vm.updateBlockParameters(BlockId("b1"), editedParams)
        waitForIdle()

        // Re-fetch parameters (simulating refresh button click)
        vm.fetchExternalConfigParameters(BlockId("b1"))
        waitForIdle()

        waitUntil(timeoutMillis = 3000L) {
            val b = vm.graph.value.blocks.filterIsInstance<Block.ActionBlock>().find { it.id == BlockId("b1") }
            // Wait until we see the new param from the updated response
            b?.parameters?.any { it.key == "env.NEW_PARAM" } == true
        }

        val finalBlock = vm.graph.value.blocks.filterIsInstance<Block.ActionBlock>().find { it.id == BlockId("b1") }
            ?: error("Expected block b1 after re-fetch")

        // User-edited values should be preserved
        val version = finalBlock.parameters.find { it.key == "env.VERSION" }
            ?: error("Expected env.VERSION parameter")
        assertEquals("2.0.0-user-edit", version.value)
        // Label and description should be updated from the re-fetch
        assertEquals("Release Version", version.label)
        assertEquals("Updated release version description", version.description)

        val target = finalBlock.parameters.find { it.key == "env.TARGET" }
            ?: error("Expected env.TARGET parameter")
        assertEquals("production", target.value)
        assertEquals("Target Env", target.label)
        assertEquals("Updated deploy target", target.description)

        // New param from re-fetch should be appended
        val newParam = finalBlock.parameters.find { it.key == "env.NEW_PARAM" }
            ?: error("Expected env.NEW_PARAM parameter")
        assertEquals("default", newParam.value)
        assertEquals("New", newParam.label)
        assertEquals("Brand new param", newParam.description)

        // Verify ordering: existing params should come before newly appended ones
        val paramKeys = finalBlock.parameters.map { it.key }
        val versionIdx = paramKeys.indexOf("env.VERSION")
        val targetIdx = paramKeys.indexOf("env.TARGET")
        val newParamIdx = paramKeys.indexOf("env.NEW_PARAM")
        assertTrue(versionIdx < newParamIdx, "Existing params should precede new params")
        assertTrue(targetIdx < newParamIdx, "Existing params should precede new params")
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
