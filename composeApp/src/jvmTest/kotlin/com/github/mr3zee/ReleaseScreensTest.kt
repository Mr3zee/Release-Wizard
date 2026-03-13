package com.github.mr3zee

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import com.github.mr3zee.api.ProjectApiClient
import com.github.mr3zee.api.ReleaseApiClient
import com.github.mr3zee.model.*
import com.github.mr3zee.releases.*
import io.ktor.http.*
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class ReleaseScreensTest {

    private val now = "2026-03-13T00:00:00Z"

    private fun releaseListClient(
        releases: String = "[]",
        projects: String = "[]",
    ) = mockHttpClient(mapOf(
        "/releases" to json("""{"releases":$releases}"""),
        "/projects" to json("""{"projects":$projects}"""),
    ))

    // ---- Release List Screen ----

    @Test
    fun `release list shows releases with status badges`() = runComposeUiTest {
        val releases = """[
            {"id":"r1","projectTemplateId":"p1","status":"RUNNING","dagSnapshot":{"blocks":[],"edges":[],"positions":{}},"parameters":[],"startedAt":"$now"},
            {"id":"r2","projectTemplateId":"p1","status":"SUCCEEDED","dagSnapshot":{"blocks":[],"edges":[],"positions":{}},"parameters":[],"startedAt":"$now","finishedAt":"$now"}
        ]"""
        val client = releaseListClient(releases = releases)
        val vm = ReleaseListViewModel(ReleaseApiClient(client), ProjectApiClient(client))

        setContent {
            MaterialTheme {
                ReleaseListScreen(
                    viewModel = vm,
                    onViewRelease = {},
                    onBack = {},
                )
            }
        }

        waitUntil(timeoutMillis = 3000L) { onAllNodesWithTag("release_list").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("release_list").assertExists()
        onNodeWithTag("release_item_r1").assertExists()
        onNodeWithTag("release_item_r2").assertExists()
        onNodeWithTag("status_badge_RUNNING", useUnmergedTree = true).assertExists()
        onNodeWithTag("status_badge_SUCCEEDED", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `release list empty state`() = runComposeUiTest {
        val client = releaseListClient()
        val vm = ReleaseListViewModel(ReleaseApiClient(client), ProjectApiClient(client))

        setContent {
            MaterialTheme {
                ReleaseListScreen(
                    viewModel = vm,
                    onViewRelease = {},
                    onBack = {},
                )
            }
        }

        waitUntil(timeoutMillis = 3000L) { onAllNodesWithTag("empty_state").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("empty_state").assertExists()
    }

    @Test
    fun `release list error with retry`() = runComposeUiTest {
        val client = mockHttpClient(mapOf(
            "/releases" to json("Server error", HttpStatusCode.InternalServerError),
        ))
        val vm = ReleaseListViewModel(ReleaseApiClient(client), ProjectApiClient(client))

        setContent {
            MaterialTheme {
                ReleaseListScreen(
                    viewModel = vm,
                    onViewRelease = {},
                    onBack = {},
                )
            }
        }

        waitUntil(timeoutMillis = 3000L) { onAllNodesWithTag("retry_button").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("retry_button").assertExists()
    }

    @Test
    fun `start release FAB opens dialog`() = runComposeUiTest {
        val projects = """[
            {"id":"p1","name":"Pipeline A","description":"","dagGraph":{"blocks":[],"edges":[],"positions":{}},"parameters":[],"createdAt":"$now","updatedAt":"$now"}
        ]"""
        val client = releaseListClient(projects = projects)
        val vm = ReleaseListViewModel(ReleaseApiClient(client), ProjectApiClient(client))

        setContent {
            MaterialTheme {
                ReleaseListScreen(
                    viewModel = vm,
                    onViewRelease = {},
                    onBack = {},
                )
            }
        }

        waitUntil(timeoutMillis = 3000L) { onAllNodesWithTag("empty_state").fetchSemanticsNodes().isNotEmpty() }

        onNodeWithTag("start_release_fab").performClick()
        waitUntil(timeoutMillis = 3000L) { onAllNodesWithTag("start_release_dialog").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("start_release_dialog").assertExists()
    }

    // ---- Release Detail Screen ----

    @Test
    fun `release detail shows release info`() = runComposeUiTest {
        val release = Release(
            id = ReleaseId("r1"),
            projectTemplateId = ProjectId("p1"),
            status = ReleaseStatus.RUNNING,
            dagSnapshot = DagGraph(
                blocks = listOf(
                    Block.ActionBlock(id = BlockId("b1"), name = "Build", type = BlockType.TEAMCITY_BUILD),
                ),
                positions = mapOf(BlockId("b1") to BlockPosition(100f, 100f)),
            ),
        )
        val executions = listOf(
            BlockExecution(
                blockId = BlockId("b1"),
                releaseId = ReleaseId("r1"),
                status = BlockStatus.RUNNING,
            ),
        )

        setContent {
            MaterialTheme {
                ReleaseDetailScreen(
                    release = release,
                    blockExecutions = executions,
                    isConnected = true,
                    onBack = {},
                    onCancel = {},
                    onApproveBlock = {},
                    onBlockClick = {},
                )
            }
        }

        onNodeWithTag("release_detail_screen").assertExists()
        onNodeWithTag("status_badge_RUNNING").assertExists()
        onNodeWithTag("execution_dag_canvas").assertExists()
    }

    @Test
    fun `release detail shows DAG with block executions`() = runComposeUiTest {
        val release = Release(
            id = ReleaseId("r1"),
            projectTemplateId = ProjectId("p1"),
            status = ReleaseStatus.SUCCEEDED,
            dagSnapshot = DagGraph(
                blocks = listOf(
                    Block.ActionBlock(id = BlockId("b1"), name = "Build", type = BlockType.TEAMCITY_BUILD),
                    Block.ActionBlock(id = BlockId("b2"), name = "Deploy", type = BlockType.GITHUB_ACTION),
                ),
                edges = listOf(Edge(fromBlockId = BlockId("b1"), toBlockId = BlockId("b2"))),
                positions = mapOf(
                    BlockId("b1") to BlockPosition(100f, 100f),
                    BlockId("b2") to BlockPosition(350f, 100f),
                ),
            ),
        )
        val executions = listOf(
            BlockExecution(blockId = BlockId("b1"), releaseId = ReleaseId("r1"), status = BlockStatus.SUCCEEDED),
            BlockExecution(blockId = BlockId("b2"), releaseId = ReleaseId("r1"), status = BlockStatus.SUCCEEDED),
        )

        setContent {
            MaterialTheme {
                ReleaseDetailScreen(
                    release = release,
                    blockExecutions = executions,
                    isConnected = true,
                    onBack = {},
                    onCancel = {},
                    onApproveBlock = {},
                    onBlockClick = {},
                )
            }
        }

        onNodeWithTag("release_detail_screen").assertExists()
        onNodeWithTag("execution_dag_canvas").assertExists()
    }

    @Test
    fun `release detail cancel button visible when RUNNING`() = runComposeUiTest {
        val release = Release(
            id = ReleaseId("r1"),
            projectTemplateId = ProjectId("p1"),
            status = ReleaseStatus.RUNNING,
            dagSnapshot = DagGraph(),
        )

        setContent {
            MaterialTheme {
                ReleaseDetailScreen(
                    release = release,
                    blockExecutions = emptyList(),
                    isConnected = true,
                    onBack = {},
                    onCancel = {},
                    onApproveBlock = {},
                    onBlockClick = {},
                )
            }
        }

        onNodeWithTag("cancel_release_button").assertExists()
    }

    @Test
    fun `release detail completed state has no cancel button`() = runComposeUiTest {
        val release = Release(
            id = ReleaseId("r1"),
            projectTemplateId = ProjectId("p1"),
            status = ReleaseStatus.SUCCEEDED,
            dagSnapshot = DagGraph(),
        )

        setContent {
            MaterialTheme {
                ReleaseDetailScreen(
                    release = release,
                    blockExecutions = emptyList(),
                    isConnected = true,
                    onBack = {},
                    onCancel = {},
                    onApproveBlock = {},
                    onBlockClick = {},
                )
            }
        }

        onNodeWithTag("release_detail_screen").assertExists()
        onNodeWithTag("cancel_release_button").assertDoesNotExist()
    }

    @Test
    fun `release detail connection indicator when disconnected`() = runComposeUiTest {
        val release = Release(
            id = ReleaseId("r1"),
            projectTemplateId = ProjectId("p1"),
            status = ReleaseStatus.RUNNING,
            dagSnapshot = DagGraph(),
        )

        setContent {
            MaterialTheme {
                ReleaseDetailScreen(
                    release = release,
                    blockExecutions = emptyList(),
                    isConnected = false,
                    onBack = {},
                    onCancel = {},
                    onApproveBlock = {},
                    onBlockClick = {},
                )
            }
        }

        onNodeWithTag("disconnected_indicator").assertExists()
    }

    @Test
    fun `release detail loading state when no release data`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                ReleaseDetailScreen(
                    release = null,
                    blockExecutions = emptyList(),
                    isConnected = true,
                    onBack = {},
                    onCancel = {},
                    onApproveBlock = {},
                    onBlockClick = {},
                )
            }
        }

        onNodeWithTag("release_detail_screen").assertExists()
        // No cancel button when release is null
        onNodeWithTag("cancel_release_button").assertDoesNotExist()
    }

    @Test
    fun `release detail approve button visible for WAITING_FOR_INPUT block`() = runComposeUiTest {
        val release = Release(
            id = ReleaseId("r1"),
            projectTemplateId = ProjectId("p1"),
            status = ReleaseStatus.RUNNING,
            dagSnapshot = DagGraph(
                blocks = listOf(
                    Block.ActionBlock(id = BlockId("b1"), name = "Approve Step", type = BlockType.USER_ACTION),
                ),
                positions = mapOf(BlockId("b1") to BlockPosition(100f, 100f)),
            ),
        )
        val executions = listOf(
            BlockExecution(
                blockId = BlockId("b1"),
                releaseId = ReleaseId("r1"),
                status = BlockStatus.WAITING_FOR_INPUT,
            ),
        )

        setContent {
            MaterialTheme {
                ReleaseDetailScreen(
                    release = release,
                    blockExecutions = executions,
                    isConnected = true,
                    onBack = {},
                    onCancel = {},
                    onApproveBlock = {},
                    onBlockClick = {},
                )
            }
        }

        onNodeWithTag("execution_dag_canvas").performTouchInput {
            click(Offset(190f, 135f))
        }
        waitUntil(timeoutMillis = 3000L) { onAllNodesWithTag("block_detail_panel").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("approve_block_button").assertExists()
    }

    @Test
    fun `release detail cancel button triggers callback`() = runComposeUiTest {
        val release = Release(
            id = ReleaseId("r1"),
            projectTemplateId = ProjectId("p1"),
            status = ReleaseStatus.RUNNING,
            dagSnapshot = DagGraph(),
        )
        var cancelClicked = false

        setContent {
            MaterialTheme {
                ReleaseDetailScreen(
                    release = release,
                    blockExecutions = emptyList(),
                    isConnected = true,
                    onBack = {},
                    onCancel = { cancelClicked = true },
                    onApproveBlock = {},
                    onBlockClick = {},
                )
            }
        }

        onNodeWithTag("cancel_release_button").performClick()
        assertTrue(cancelClicked)
    }

    @Test
    fun `release detail back button triggers callback`() = runComposeUiTest {
        val release = Release(
            id = ReleaseId("r1"),
            projectTemplateId = ProjectId("p1"),
            status = ReleaseStatus.RUNNING,
            dagSnapshot = DagGraph(),
        )
        var backClicked = false

        setContent {
            MaterialTheme {
                ReleaseDetailScreen(
                    release = release,
                    blockExecutions = emptyList(),
                    isConnected = true,
                    onBack = { backClicked = true },
                    onCancel = {},
                    onApproveBlock = {},
                    onBlockClick = {},
                )
            }
        }

        onNodeWithText("Back").performClick()
        assertTrue(backClicked)
    }

    // ---- Block detail panel ----

    @Test
    fun `release detail block panel shows status text`() = runComposeUiTest {
        val release = Release(
            id = ReleaseId("r1"),
            projectTemplateId = ProjectId("p1"),
            status = ReleaseStatus.RUNNING,
            dagSnapshot = DagGraph(
                blocks = listOf(
                    Block.ActionBlock(id = BlockId("b1"), name = "Build", type = BlockType.TEAMCITY_BUILD),
                ),
                positions = mapOf(BlockId("b1") to BlockPosition(100f, 100f)),
            ),
        )
        val executions = listOf(
            BlockExecution(
                blockId = BlockId("b1"),
                releaseId = ReleaseId("r1"),
                status = BlockStatus.RUNNING,
            ),
        )

        setContent {
            MaterialTheme {
                ReleaseDetailScreen(
                    release = release,
                    blockExecutions = executions,
                    isConnected = true,
                    onBack = {},
                    onCancel = {},
                    onApproveBlock = {},
                    onBlockClick = {},
                )
            }
        }

        // Click block on canvas
        onNodeWithTag("execution_dag_canvas").performTouchInput {
            click(Offset(190f, 135f))
        }
        waitUntil(timeoutMillis = 3000L) { onAllNodesWithTag("block_detail_panel").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("block_status_text").assertExists()
        onNodeWithText("Status: RUNNING").assertExists()
    }

    @Test
    fun `release detail block panel shows error text`() = runComposeUiTest {
        val release = Release(
            id = ReleaseId("r1"),
            projectTemplateId = ProjectId("p1"),
            status = ReleaseStatus.FAILED,
            dagSnapshot = DagGraph(
                blocks = listOf(
                    Block.ActionBlock(id = BlockId("b1"), name = "Build", type = BlockType.TEAMCITY_BUILD),
                ),
                positions = mapOf(BlockId("b1") to BlockPosition(100f, 100f)),
            ),
        )
        val executions = listOf(
            BlockExecution(
                blockId = BlockId("b1"),
                releaseId = ReleaseId("r1"),
                status = BlockStatus.FAILED,
                error = "Connection timeout",
            ),
        )

        setContent {
            MaterialTheme {
                ReleaseDetailScreen(
                    release = release,
                    blockExecutions = executions,
                    isConnected = true,
                    onBack = {},
                    onCancel = {},
                    onApproveBlock = {},
                    onBlockClick = {},
                )
            }
        }

        onNodeWithTag("execution_dag_canvas").performTouchInput {
            click(Offset(190f, 135f))
        }
        waitUntil(timeoutMillis = 3000L) { onAllNodesWithTag("block_detail_panel").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("block_error_text").assertExists()
        onNodeWithText("Error: Connection timeout", substring = true).assertExists()
    }

    @Test
    fun `release detail block panel close button dismisses panel`() = runComposeUiTest {
        val release = Release(
            id = ReleaseId("r1"),
            projectTemplateId = ProjectId("p1"),
            status = ReleaseStatus.RUNNING,
            dagSnapshot = DagGraph(
                blocks = listOf(
                    Block.ActionBlock(id = BlockId("b1"), name = "Build", type = BlockType.TEAMCITY_BUILD),
                ),
                positions = mapOf(BlockId("b1") to BlockPosition(100f, 100f)),
            ),
        )
        val executions = listOf(
            BlockExecution(blockId = BlockId("b1"), releaseId = ReleaseId("r1"), status = BlockStatus.RUNNING),
        )

        setContent {
            MaterialTheme {
                ReleaseDetailScreen(
                    release = release,
                    blockExecutions = executions,
                    isConnected = true,
                    onBack = {},
                    onCancel = {},
                    onApproveBlock = {},
                    onBlockClick = {},
                )
            }
        }

        onNodeWithTag("execution_dag_canvas").performTouchInput {
            click(Offset(190f, 135f))
        }
        waitUntil(timeoutMillis = 3000L) { onAllNodesWithTag("block_detail_panel").fetchSemanticsNodes().isNotEmpty() }

        // Click close
        onNodeWithText("Close").performClick()
        waitForIdle()
        onNodeWithTag("block_detail_panel").assertDoesNotExist()
    }

    @Test
    fun `release detail block panel shows outputs`() = runComposeUiTest {
        val release = Release(
            id = ReleaseId("r1"),
            projectTemplateId = ProjectId("p1"),
            status = ReleaseStatus.SUCCEEDED,
            dagSnapshot = DagGraph(
                blocks = listOf(
                    Block.ActionBlock(id = BlockId("b1"), name = "Build", type = BlockType.TEAMCITY_BUILD),
                ),
                positions = mapOf(BlockId("b1") to BlockPosition(100f, 100f)),
            ),
        )
        val executions = listOf(
            BlockExecution(
                blockId = BlockId("b1"),
                releaseId = ReleaseId("r1"),
                status = BlockStatus.SUCCEEDED,
                outputs = mapOf("buildNumber" to "42", "buildUrl" to "https://example.com"),
            ),
        )

        setContent {
            MaterialTheme {
                ReleaseDetailScreen(
                    release = release,
                    blockExecutions = executions,
                    isConnected = true,
                    onBack = {},
                    onCancel = {},
                    onApproveBlock = {},
                    onBlockClick = {},
                )
            }
        }

        onNodeWithTag("execution_dag_canvas").performTouchInput {
            click(Offset(190f, 135f))
        }
        waitUntil(timeoutMillis = 3000L) { onAllNodesWithTag("block_detail_panel").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("Outputs:").assertExists()
        onNodeWithText("buildNumber: 42", substring = true).assertExists()
    }

    @Test
    fun `release list item click triggers callback`() = runComposeUiTest {
        val releases = """[
            {"id":"r1","projectTemplateId":"p1","status":"SUCCEEDED","dagSnapshot":{"blocks":[],"edges":[],"positions":{}},"parameters":[],"startedAt":"$now","finishedAt":"$now"}
        ]"""
        val client = releaseListClient(releases = releases)
        val vm = ReleaseListViewModel(ReleaseApiClient(client), ProjectApiClient(client))
        var viewedId: ReleaseId? = null

        setContent {
            MaterialTheme {
                ReleaseListScreen(
                    viewModel = vm,
                    onViewRelease = { viewedId = it },
                    onBack = {},
                )
            }
        }

        waitUntil(timeoutMillis = 3000L) { onAllNodesWithTag("release_list").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("release_item_r1").performClick()
        assertTrue(viewedId?.value == "r1")
    }

    @Test
    fun `release list all status badges render correctly`() = runComposeUiTest {
        val releases = """[
            {"id":"r1","projectTemplateId":"p1","status":"PENDING","dagSnapshot":{"blocks":[],"edges":[],"positions":{}},"parameters":[]},
            {"id":"r2","projectTemplateId":"p1","status":"RUNNING","dagSnapshot":{"blocks":[],"edges":[],"positions":{}},"parameters":[],"startedAt":"$now"},
            {"id":"r3","projectTemplateId":"p1","status":"SUCCEEDED","dagSnapshot":{"blocks":[],"edges":[],"positions":{}},"parameters":[],"startedAt":"$now","finishedAt":"$now"},
            {"id":"r4","projectTemplateId":"p1","status":"FAILED","dagSnapshot":{"blocks":[],"edges":[],"positions":{}},"parameters":[],"startedAt":"$now","finishedAt":"$now"},
            {"id":"r5","projectTemplateId":"p1","status":"CANCELLED","dagSnapshot":{"blocks":[],"edges":[],"positions":{}},"parameters":[],"startedAt":"$now","finishedAt":"$now"}
        ]"""
        val client = releaseListClient(releases = releases)
        val vm = ReleaseListViewModel(ReleaseApiClient(client), ProjectApiClient(client))

        setContent {
            MaterialTheme {
                ReleaseListScreen(
                    viewModel = vm,
                    onViewRelease = {},
                    onBack = {},
                )
            }
        }

        waitUntil(timeoutMillis = 3000L) { onAllNodesWithTag("release_list").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("status_badge_PENDING", useUnmergedTree = true).assertExists()
        onNodeWithTag("status_badge_RUNNING", useUnmergedTree = true).assertExists()
        onNodeWithTag("status_badge_SUCCEEDED", useUnmergedTree = true).assertExists()
        onNodeWithTag("status_badge_FAILED", useUnmergedTree = true).assertExists()
        onNodeWithTag("status_badge_CANCELLED", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `release detail approve callback fires with block id`() = runComposeUiTest {
        val release = Release(
            id = ReleaseId("r1"),
            projectTemplateId = ProjectId("p1"),
            status = ReleaseStatus.RUNNING,
            dagSnapshot = DagGraph(
                blocks = listOf(
                    Block.ActionBlock(id = BlockId("approve1"), name = "Manual Approve", type = BlockType.USER_ACTION),
                ),
                positions = mapOf(BlockId("approve1") to BlockPosition(100f, 100f)),
            ),
        )
        val executions = listOf(
            BlockExecution(
                blockId = BlockId("approve1"),
                releaseId = ReleaseId("r1"),
                status = BlockStatus.WAITING_FOR_INPUT,
            ),
        )
        var approvedBlockId: BlockId? = null

        setContent {
            MaterialTheme {
                ReleaseDetailScreen(
                    release = release,
                    blockExecutions = executions,
                    isConnected = true,
                    onBack = {},
                    onCancel = {},
                    onApproveBlock = { approvedBlockId = it },
                    onBlockClick = {},
                )
            }
        }

        onNodeWithTag("execution_dag_canvas").performTouchInput {
            click(Offset(190f, 135f))
        }
        waitUntil(timeoutMillis = 3000L) { onAllNodesWithTag("approve_block_button").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("approve_block_button").performClick()
        assertTrue(approvedBlockId?.value == "approve1")
    }
}
