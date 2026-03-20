package com.github.mr3zee

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import com.github.mr3zee.api.ProjectApiClient
import com.github.mr3zee.api.ReleaseApiClient
import com.github.mr3zee.model.*
import com.github.mr3zee.releases.*
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
import kotlin.time.Instant

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
        val vm = ReleaseListViewModel(ReleaseApiClient(client), ProjectApiClient(client), MutableStateFlow(TeamId("test-team")))

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
        onNodeWithTag("release_item_r1", useUnmergedTree = true).assertExists()
        onNodeWithTag("release_item_r2", useUnmergedTree = true).assertExists()
        onNodeWithTag("status_badge_RUNNING", useUnmergedTree = true).assertExists()
        onNodeWithTag("status_badge_SUCCEEDED", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `release list empty state`() = runComposeUiTest {
        val client = releaseListClient()
        val vm = ReleaseListViewModel(ReleaseApiClient(client), ProjectApiClient(client), MutableStateFlow(TeamId("test-team")))

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
        val vm = ReleaseListViewModel(ReleaseApiClient(client), ProjectApiClient(client), MutableStateFlow(TeamId("test-team")))

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
        val vm = ReleaseListViewModel(ReleaseApiClient(client), ProjectApiClient(client), MutableStateFlow(TeamId("test-team")))

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
        waitUntil(timeoutMillis = 3000L) { onAllNodesWithTag("start_release_form", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("start_release_form", useUnmergedTree = true).assertExists()
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
                    onStopRelease = {},
                    onResumeRelease = {},
                    onStopBlock = {},
                    onRerun = {},
                    onArchive = {},
                    onApproveBlock = {},
                    onBlockClick = {},
                )
            }
        }

        onNodeWithTag("release_detail_screen").assertExists()
        onNodeWithTag("status_badge_RUNNING", useUnmergedTree = true).assertExists()
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
                    onStopRelease = {},
                    onResumeRelease = {},
                    onStopBlock = {},
                    onRerun = {},
                    onArchive = {},
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
                    onStopRelease = {},
                    onResumeRelease = {},
                    onStopBlock = {},
                    onRerun = {},
                    onArchive = {},
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
                    onStopRelease = {},
                    onResumeRelease = {},
                    onStopBlock = {},
                    onRerun = {},
                    onArchive = {},
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
                    onStopRelease = {},
                    onResumeRelease = {},
                    onStopBlock = {},
                    onRerun = {},
                    onArchive = {},
                    onApproveBlock = {},
                    onBlockClick = {},
                )
            }
        }

        onNodeWithTag("disconnected_indicator").assertExists()
    }

    @Test
    fun `release detail reconnect indicator shows attempt number`() = runComposeUiTest {
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
                    reconnectAttempt = 3,
                    onBack = {},
                    onCancel = {},
                    onStopRelease = {},
                    onResumeRelease = {},
                    onStopBlock = {},
                    onRerun = {},
                    onArchive = {},
                    onApproveBlock = {},
                    onBlockClick = {},
                )
            }
        }

        onNodeWithTag("disconnected_indicator").assertExists()
        onNodeWithText("Reconnecting (attempt 3)...").assertExists()
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
                    onStopRelease = {},
                    onResumeRelease = {},
                    onStopBlock = {},
                    onRerun = {},
                    onArchive = {},
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
                    Block.ActionBlock(id = BlockId("b1"), name = "Approve Step", type = BlockType.SLACK_MESSAGE, preGate = Gate()),
                ),
                positions = mapOf(BlockId("b1") to BlockPosition(100f, 100f)),
            ),
        )
        val executions = listOf(
            BlockExecution(
                blockId = BlockId("b1"),
                releaseId = ReleaseId("r1"),
                status = BlockStatus.WAITING_FOR_INPUT,
                gatePhase = GatePhase.PRE,
                gateMessage = "Approve to start 'Approve Step'",
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
                    onStopRelease = {},
                    onResumeRelease = {},
                    onStopBlock = {},
                    onRerun = {},
                    onArchive = {},
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
                    onStopRelease = {},
                    onResumeRelease = {},
                    onStopBlock = {},
                    onRerun = {},
                    onArchive = {},
                    onApproveBlock = {},
                    onBlockClick = {},
                )
            }
        }

        onNodeWithTag("cancel_release_button").performClick()
        waitUntil(timeoutMillis = 3000L) { onAllNodesWithTag("confirm_cancel_release", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("confirm_cancel_release_confirm", useUnmergedTree = true).performClick()
        waitForIdle()
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
                    onStopRelease = {},
                    onResumeRelease = {},
                    onStopBlock = {},
                    onRerun = {},
                    onArchive = {},
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
                    onStopRelease = {},
                    onResumeRelease = {},
                    onStopBlock = {},
                    onRerun = {},
                    onArchive = {},
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
        onNodeWithText("Status: Running").assertExists()
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
                    onStopRelease = {},
                    onResumeRelease = {},
                    onStopBlock = {},
                    onRerun = {},
                    onArchive = {},
                    onApproveBlock = {},
                    onBlockClick = {},
                )
            }
        }

        onNodeWithTag("execution_dag_canvas").performTouchInput {
            click(Offset(190f, 135f))
        }
        waitUntil(timeoutMillis = 3000L) { onAllNodesWithTag("block_detail_panel").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("error_detail_section", useUnmergedTree = true).assertExists()
        // Error section is expanded by default — error text should be visible immediately
        onNodeWithText("Connection timeout", substring = true, useUnmergedTree = true).assertExists()
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
                    onStopRelease = {},
                    onResumeRelease = {},
                    onStopBlock = {},
                    onRerun = {},
                    onArchive = {},
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
                    onStopRelease = {},
                    onResumeRelease = {},
                    onStopBlock = {},
                    onRerun = {},
                    onArchive = {},
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
        val vm = ReleaseListViewModel(ReleaseApiClient(client), ProjectApiClient(client), MutableStateFlow(TeamId("test-team")))
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
        onNodeWithTag("release_item_r1", useUnmergedTree = true).performClick()
        assertEquals(ReleaseId("r1"), viewedId)
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
        val vm = ReleaseListViewModel(ReleaseApiClient(client), ProjectApiClient(client), MutableStateFlow(TeamId("test-team")))

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
                    Block.ActionBlock(id = BlockId("approve1"), name = "Manual Approve", type = BlockType.SLACK_MESSAGE, preGate = Gate()),
                ),
                positions = mapOf(BlockId("approve1") to BlockPosition(100f, 100f)),
            ),
        )
        val executions = listOf(
            BlockExecution(
                blockId = BlockId("approve1"),
                releaseId = ReleaseId("r1"),
                status = BlockStatus.WAITING_FOR_INPUT,
                gatePhase = GatePhase.PRE,
                gateMessage = "Approve to start 'Manual Approve'",
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
                    onStopRelease = {},
                    onResumeRelease = {},
                    onStopBlock = {},
                    onRerun = {},
                    onArchive = {},
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
        waitUntil(timeoutMillis = 3000L) { onAllNodesWithTag("confirm_approve_block", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("confirm_approve_block_confirm", useUnmergedTree = true).performClick()
        waitForIdle()
        assertEquals(BlockId("approve1"), approvedBlockId)
    }

    // ---- Artifact tree tests ----

    @Test
    fun `release detail block panel shows artifact tree when artifacts key present`() = runComposeUiTest {
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
                outputs = mapOf(
                    "buildNumber" to "42",
                    "artifacts" to """["lib/app.jar","lib/utils.jar","docs/readme.txt"]""",
                ),
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
                    onStopRelease = {},
                    onResumeRelease = {},
                    onStopBlock = {},
                    onRerun = {},
                    onArchive = {},
                    onApproveBlock = {},
                    onBlockClick = {},
                )
            }
        }

        onNodeWithTag("execution_dag_canvas").performTouchInput {
            click(Offset(190f, 135f))
        }
        waitUntil(timeoutMillis = 3000L) { onAllNodesWithTag("block_detail_panel").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("artifact_tree_section", useUnmergedTree = true).assertExists()
        onNodeWithTag("artifact_expand_all_button", useUnmergedTree = true).assertExists()
        onNodeWithTag("artifact_collapse_all_button", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `release detail block panel artifact tree collapsed by default`() = runComposeUiTest {
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
                outputs = mapOf(
                    "buildNumber" to "42",
                    "artifacts" to """["lib/app.jar","lib/utils.jar"]""",
                ),
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
                    onStopRelease = {},
                    onResumeRelease = {},
                    onStopBlock = {},
                    onRerun = {},
                    onArchive = {},
                    onApproveBlock = {},
                    onBlockClick = {},
                )
            }
        }

        onNodeWithTag("execution_dag_canvas").performTouchInput {
            click(Offset(190f, 135f))
        }
        waitUntil(timeoutMillis = 3000L) { onAllNodesWithTag("block_detail_panel").fetchSemanticsNodes().isNotEmpty() }
        // Directory node exists
        onNodeWithTag("artifact_node_lib", useUnmergedTree = true).assertExists()
        // Children should NOT be visible (collapsed by default)
        onNodeWithTag("artifact_node_lib/app.jar", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun `release detail block panel expand all shows directory children`() = runComposeUiTest {
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
                outputs = mapOf(
                    "buildNumber" to "42",
                    "artifacts" to """["lib/app.jar","docs/readme.txt"]""",
                ),
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
                    onStopRelease = {},
                    onResumeRelease = {},
                    onStopBlock = {},
                    onRerun = {},
                    onArchive = {},
                    onApproveBlock = {},
                    onBlockClick = {},
                )
            }
        }

        onNodeWithTag("execution_dag_canvas").performTouchInput {
            click(Offset(190f, 135f))
        }
        waitUntil(timeoutMillis = 3000L) { onAllNodesWithTag("block_detail_panel").fetchSemanticsNodes().isNotEmpty() }

        // Click Expand All
        onNodeWithTag("artifact_expand_all_button", useUnmergedTree = true).performClick()
        waitForIdle()

        // Now children should be visible
        onNodeWithTag("artifact_node_lib/app.jar", useUnmergedTree = true).assertExists()
        onNodeWithTag("artifact_node_docs/readme.txt", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `release detail block panel no artifact section when key absent`() = runComposeUiTest {
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
                outputs = mapOf("buildNumber" to "42"),
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
                    onStopRelease = {},
                    onResumeRelease = {},
                    onStopBlock = {},
                    onRerun = {},
                    onArchive = {},
                    onApproveBlock = {},
                    onBlockClick = {},
                )
            }
        }

        onNodeWithTag("execution_dag_canvas").performTouchInput {
            click(Offset(190f, 135f))
        }
        waitUntil(timeoutMillis = 3000L) { onAllNodesWithTag("block_detail_panel").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("artifact_tree_section", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun `release detail block panel artifacts key filtered from generic outputs`() = runComposeUiTest {
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
                outputs = mapOf(
                    "buildNumber" to "42",
                    "artifacts" to """["lib/app.jar"]""",
                ),
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
                    onStopRelease = {},
                    onResumeRelease = {},
                    onStopBlock = {},
                    onRerun = {},
                    onArchive = {},
                    onApproveBlock = {},
                    onBlockClick = {},
                )
            }
        }

        onNodeWithTag("execution_dag_canvas").performTouchInput {
            click(Offset(190f, 135f))
        }
        waitUntil(timeoutMillis = 3000L) { onAllNodesWithTag("block_detail_panel").fetchSemanticsNodes().isNotEmpty() }
        // buildNumber should be in outputs
        onNodeWithText("buildNumber: 42", substring = true).assertExists()
        // Raw JSON artifacts should NOT appear in generic outputs
        onNodeWithText("artifacts:", substring = true).assertDoesNotExist()
    }

    // ---- Phase 1A: Rerun button tests ----

    @Test
    fun `release detail rerun button visible for succeeded release`() = runComposeUiTest {
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
                    onStopRelease = {},
                    onResumeRelease = {},
                    onStopBlock = {},
                    onRerun = {},
                    onArchive = {},
                    onApproveBlock = {},
                    onBlockClick = {},
                )
            }
        }

        onNodeWithTag("rerun_release_button").assertExists()
    }

    @Test
    fun `release detail rerun button not visible for running release`() = runComposeUiTest {
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
                    onStopRelease = {},
                    onResumeRelease = {},
                    onStopBlock = {},
                    onRerun = {},
                    onArchive = {},
                    onApproveBlock = {},
                    onBlockClick = {},
                )
            }
        }

        onNodeWithTag("rerun_release_button").assertDoesNotExist()
    }

    // ---- Phase 1B: Archive button tests ----

    @Test
    fun `release detail archive button visible for succeeded release`() = runComposeUiTest {
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
                    onStopRelease = {},
                    onResumeRelease = {},
                    onStopBlock = {},
                    onRerun = {},
                    onArchive = {},
                    onApproveBlock = {},
                    onBlockClick = {},
                )
            }
        }

        onNodeWithTag("archive_release_button").assertExists()
    }

    @Test
    fun `release detail archive button hidden for archived release`() = runComposeUiTest {
        val release = Release(
            id = ReleaseId("r1"),
            projectTemplateId = ProjectId("p1"),
            status = ReleaseStatus.ARCHIVED,
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
                    onStopRelease = {},
                    onResumeRelease = {},
                    onStopBlock = {},
                    onRerun = {},
                    onArchive = {},
                    onApproveBlock = {},
                    onBlockClick = {},
                )
            }
        }

        // Archive button should be hidden for already-archived releases
        onNodeWithTag("archive_release_button").assertDoesNotExist()
        // But rerun button should still be visible (ARCHIVED is terminal)
        onNodeWithTag("rerun_release_button").assertExists()
    }

    @Test
    fun `release list archived status badge renders`() = runComposeUiTest {
        val releases = """[
            {"id":"r1","projectTemplateId":"p1","status":"ARCHIVED","dagSnapshot":{"blocks":[],"edges":[],"positions":{}},"parameters":[],"startedAt":"$now","finishedAt":"$now"}
        ]"""
        val client = releaseListClient(releases = releases)
        val vm = ReleaseListViewModel(ReleaseApiClient(client), ProjectApiClient(client), MutableStateFlow(TeamId("test-team")))

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
        onNodeWithTag("status_badge_ARCHIVED", useUnmergedTree = true).assertExists()
    }

    // ---- Refresh Tests ----

    private val pollingDisabled = Long.MAX_VALUE / 2

    @Test
    fun `refresh button exists on release list`() = runComposeUiTest {
        val client = releaseListClient()
        val vm = ReleaseListViewModel(ReleaseApiClient(client), ProjectApiClient(client), MutableStateFlow(TeamId("test-team")), pollingIntervalMs = pollingDisabled)

        setContent {
            MaterialTheme {
                ReleaseListScreen(viewModel = vm, onViewRelease = {}, onBack = {})
            }
        }

        onNodeWithTag("refresh_button").assertExists()
    }

    @Test
    fun `refresh icon idle tag when not refreshing`() = runComposeUiTest {
        val client = releaseListClient()
        val vm = ReleaseListViewModel(ReleaseApiClient(client), ProjectApiClient(client), MutableStateFlow(TeamId("test-team")), pollingIntervalMs = pollingDisabled)

        setContent {
            MaterialTheme {
                ReleaseListScreen(viewModel = vm, onViewRelease = {}, onBack = {})
            }
        }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("refresh_icon_idle", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithTag("refresh_icon_idle", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `refresh triggers re-fetch with new data`() = runComposeUiTest {
        val returnNewData = AtomicBoolean(false)
        val client = HttpClient(MockEngine { request ->
            val path = request.url.encodedPath
            val jsonHeaders = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            when {
                path.endsWith("/releases") -> {
                    val releases = if (!returnNewData.get()) {
                        """[{"id":"r1","projectTemplateId":"p1","status":"RUNNING","dagSnapshot":{"blocks":[],"edges":[],"positions":{}},"parameters":[],"startedAt":"$now"}]"""
                    } else {
                        """[{"id":"r1","projectTemplateId":"p1","status":"RUNNING","dagSnapshot":{"blocks":[],"edges":[],"positions":{}},"parameters":[],"startedAt":"$now"},{"id":"r-new","projectTemplateId":"p1","status":"PENDING","dagSnapshot":{"blocks":[],"edges":[],"positions":{}},"parameters":[],"startedAt":"$now"}]"""
                    }
                    respond("""{"releases":$releases}""", status = HttpStatusCode.OK, headers = jsonHeaders)
                }
                else -> respond("""{"projects":[]}""", status = HttpStatusCode.OK, headers = jsonHeaders)
            }
        }) {
            install(ContentNegotiation) { json(AppJson) }
            install(HttpCookies)
            expectSuccess = true
        }
        val vm = ReleaseListViewModel(ReleaseApiClient(client), ProjectApiClient(client), MutableStateFlow(TeamId("test-team")), pollingIntervalMs = pollingDisabled)

        setContent {
            MaterialTheme {
                ReleaseListScreen(viewModel = vm, onViewRelease = {}, onBack = {})
            }
        }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("release_item_r1", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithTag("release_item_r-new", useUnmergedTree = true).assertDoesNotExist()

        returnNewData.set(true)
        onNodeWithTag("refresh_button").performClick()

        waitUntil(timeoutMillis = 5000L) {
            onAllNodesWithTag("release_item_r-new", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithTag("release_item_r-new", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `refresh indicator absent during initial load`() = runComposeUiTest {
        val client = releaseListClient()
        val vm = ReleaseListViewModel(ReleaseApiClient(client), ProjectApiClient(client), MutableStateFlow(TeamId("test-team")), pollingIntervalMs = pollingDisabled)

        setContent {
            MaterialTheme {
                ReleaseListScreen(viewModel = vm, onViewRelease = {}, onBack = {})
            }
        }

        onNodeWithTag("refresh_indicator").assertDoesNotExist()
    }

    @Test
    fun `refresh error shows banner and keeps list visible`() = runComposeUiTest {
        val failOnRefresh = AtomicBoolean(false)
        val client = HttpClient(MockEngine { request ->
            val path = request.url.encodedPath
            val jsonHeaders = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            when {
                path.endsWith("/releases") -> {
                    if (!failOnRefresh.get()) {
                        val releases = """[{"id":"r1","projectTemplateId":"p1","status":"RUNNING","dagSnapshot":{"blocks":[],"edges":[],"positions":{}},"parameters":[],"startedAt":"$now"}]"""
                        respond("""{"releases":$releases}""", status = HttpStatusCode.OK, headers = jsonHeaders)
                    } else {
                        respond("Server error", status = HttpStatusCode.InternalServerError, headers = jsonHeaders)
                    }
                }
                else -> respond("""{"projects":[]}""", status = HttpStatusCode.OK, headers = jsonHeaders)
            }
        }) {
            install(ContentNegotiation) { json(AppJson) }
            install(HttpCookies)
            expectSuccess = true
        }
        val vm = ReleaseListViewModel(ReleaseApiClient(client), ProjectApiClient(client), MutableStateFlow(TeamId("test-team")), pollingIntervalMs = pollingDisabled)

        setContent {
            MaterialTheme {
                ReleaseListScreen(viewModel = vm, onViewRelease = {}, onBack = {})
            }
        }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("release_item_r1", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }

        failOnRefresh.set(true)
        onNodeWithTag("refresh_button").performClick()

        waitUntil(timeoutMillis = 5000L) {
            onAllNodesWithTag("refresh_error_banner").fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithTag("refresh_error_banner").assertExists()
        onNodeWithTag("release_item_r1", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `dismiss refresh error hides banner`() = runComposeUiTest {
        val failOnRefresh = AtomicBoolean(false)
        val client = HttpClient(MockEngine { request ->
            val path = request.url.encodedPath
            val jsonHeaders = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            when {
                path.endsWith("/releases") -> {
                    if (!failOnRefresh.get()) {
                        val releases = """[{"id":"r1","projectTemplateId":"p1","status":"RUNNING","dagSnapshot":{"blocks":[],"edges":[],"positions":{}},"parameters":[],"startedAt":"$now"}]"""
                        respond("""{"releases":$releases}""", status = HttpStatusCode.OK, headers = jsonHeaders)
                    } else {
                        respond("Server error", status = HttpStatusCode.InternalServerError, headers = jsonHeaders)
                    }
                }
                else -> respond("""{"projects":[]}""", status = HttpStatusCode.OK, headers = jsonHeaders)
            }
        }) {
            install(ContentNegotiation) { json(AppJson) }
            install(HttpCookies)
            expectSuccess = true
        }
        val vm = ReleaseListViewModel(ReleaseApiClient(client), ProjectApiClient(client), MutableStateFlow(TeamId("test-team")), pollingIntervalMs = pollingDisabled)

        setContent {
            MaterialTheme {
                ReleaseListScreen(viewModel = vm, onViewRelease = {}, onBack = {})
            }
        }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("release_item_r1", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        failOnRefresh.set(true)
        onNodeWithTag("refresh_button").performClick()
        waitUntil(timeoutMillis = 5000L) {
            onAllNodesWithTag("refresh_error_banner").fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithTag("dismiss_refresh_error").performClick()
        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("refresh_error_banner").fetchSemanticsNodes().isEmpty()
        }
        onNodeWithTag("refresh_error_banner").assertDoesNotExist()
    }

    @Test
    fun `updated text exists after load`() = runComposeUiTest {
        val releases = """[{"id":"r1","projectTemplateId":"p1","status":"RUNNING","dagSnapshot":{"blocks":[],"edges":[],"positions":{}},"parameters":[],"startedAt":"$now"}]"""
        val client = releaseListClient(releases = releases)
        val vm = ReleaseListViewModel(ReleaseApiClient(client), ProjectApiClient(client), MutableStateFlow(TeamId("test-team")), pollingIntervalMs = pollingDisabled)

        setContent {
            MaterialTheme {
                ReleaseListScreen(viewModel = vm, onViewRelease = {}, onBack = {})
            }
        }

        waitUntil(timeoutMillis = 5000L) {
            onAllNodesWithTag("last_updated_text", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithTag("last_updated_text", useUnmergedTree = true).assertExists()
        onNodeWithTag("last_updated_text", useUnmergedTree = true).assertTextContains("Updated", substring = true)
    }

    @Test
    fun `updated text absent before first load`() = runComposeUiTest {
        val client = mockHttpClient(mapOf(
            "/releases" to json("Server error", HttpStatusCode.InternalServerError),
        ))
        val vm = ReleaseListViewModel(ReleaseApiClient(client), ProjectApiClient(client), MutableStateFlow(TeamId("test-team")), pollingIntervalMs = pollingDisabled)

        setContent {
            MaterialTheme {
                ReleaseListScreen(viewModel = vm, onViewRelease = {}, onBack = {})
            }
        }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("retry_button").fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithTag("last_updated_text", useUnmergedTree = true).assertDoesNotExist()
    }

    // ---- QA-RELLIST: Release List Screen gap coverage ----

    /**
     * Helper that creates a mock client with server-side filtering support.
     * Returns different release lists based on query params (status, q, projectId).
     */
    private fun filteringReleaseListClient(
        allReleases: List<ReleaseTestData>,
        projects: String = "[]",
    ): HttpClient {
        return HttpClient(MockEngine { request ->
            val path = request.url.encodedPath
            val jsonHeaders = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            when {
                path.endsWith("/releases") && request.method == HttpMethod.Get -> {
                    val statusParam = request.url.parameters["status"]
                    val searchParam = request.url.parameters["q"]
                    val projectIdParam = request.url.parameters["projectId"]
                    val filtered = allReleases.filter { r ->
                        (statusParam == null || r.status == statusParam) &&
                            (searchParam.isNullOrBlank() || r.id.contains(searchParam, ignoreCase = true) || r.projectName.contains(searchParam, ignoreCase = true)) &&
                            (projectIdParam == null || r.projectTemplateId == projectIdParam)
                    }
                    val releasesJson = filtered.joinToString(",") { it.json }
                    respond("""{"releases":[$releasesJson]}""", status = HttpStatusCode.OK, headers = jsonHeaders)
                }
                path.endsWith("/projects") -> {
                    respond("""{"projects":$projects}""", status = HttpStatusCode.OK, headers = jsonHeaders)
                }
                else -> respond("{}", status = HttpStatusCode.OK, headers = jsonHeaders)
            }
        }) {
            install(ContentNegotiation) { json(AppJson) }
            install(HttpCookies)
            expectSuccess = true
        }
    }

    private data class ReleaseTestData(
        val id: String,
        val projectTemplateId: String,
        val status: String,
        val projectName: String = "",
        val json: String,
    )

    private fun releaseJson(id: String, projectTemplateId: String, status: String): String {
        val hasStartedAt = status != "PENDING"
        val hasFinishedAt = status in listOf("SUCCEEDED", "FAILED", "CANCELLED", "ARCHIVED")
        return buildString {
            append("""{"id":"$id","projectTemplateId":"$projectTemplateId","status":"$status","dagSnapshot":{"blocks":[],"edges":[],"positions":{}},"parameters":[]""")
            if (hasStartedAt) append(""","startedAt":"$now"""")
            if (hasFinishedAt) append(""","finishedAt":"$now"""")
            append("}")
        }
    }

    // QA-RELLIST-1: Status filter chip interaction filters list
    @Test
    fun `status filter chip filters release list`() = runComposeUiTest {
        val testData = listOf(
            ReleaseTestData("r1", "p1", "RUNNING", json = releaseJson("r1", "p1", "RUNNING")),
            ReleaseTestData("r2", "p1", "SUCCEEDED", json = releaseJson("r2", "p1", "SUCCEEDED")),
            ReleaseTestData("r3", "p1", "FAILED", json = releaseJson("r3", "p1", "FAILED")),
        )
        val client = filteringReleaseListClient(testData)
        val vm = ReleaseListViewModel(ReleaseApiClient(client), ProjectApiClient(client), MutableStateFlow(TeamId("test-team")), pollingIntervalMs = pollingDisabled)

        setContent {
            MaterialTheme {
                ReleaseListScreen(viewModel = vm, onViewRelease = {}, onBack = {})
            }
        }

        // Wait for all releases to load
        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("release_item_r1", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithTag("release_item_r2", useUnmergedTree = true).assertExists()
        onNodeWithTag("release_item_r3", useUnmergedTree = true).assertExists()

        // Click "Running" filter chip
        onNodeWithTag("filter_RUNNING").performClick()

        // Wait for filtered results — only RUNNING should remain
        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("release_item_r2", useUnmergedTree = true).fetchSemanticsNodes().isEmpty()
        }
        onNodeWithTag("release_item_r1", useUnmergedTree = true).assertExists()
        onNodeWithTag("release_item_r3", useUnmergedTree = true).assertDoesNotExist()
    }

    // QA-RELLIST-2: Status filter chip toggle (deselect)
    @Test
    fun `status filter chip toggle deselects and shows all releases`() = runComposeUiTest {
        val testData = listOf(
            ReleaseTestData("r1", "p1", "RUNNING", json = releaseJson("r1", "p1", "RUNNING")),
            ReleaseTestData("r2", "p1", "SUCCEEDED", json = releaseJson("r2", "p1", "SUCCEEDED")),
        )
        val client = filteringReleaseListClient(testData)
        val vm = ReleaseListViewModel(ReleaseApiClient(client), ProjectApiClient(client), MutableStateFlow(TeamId("test-team")), pollingIntervalMs = pollingDisabled)

        setContent {
            MaterialTheme {
                ReleaseListScreen(viewModel = vm, onViewRelease = {}, onBack = {})
            }
        }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("release_item_r1", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }

        // Select RUNNING filter
        onNodeWithTag("filter_RUNNING").performClick()
        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("release_item_r2", useUnmergedTree = true).fetchSemanticsNodes().isEmpty()
        }
        onNodeWithTag("release_item_r1", useUnmergedTree = true).assertExists()
        onNodeWithTag("release_item_r2", useUnmergedTree = true).assertDoesNotExist()

        // Click RUNNING again to deselect (toggle off)
        onNodeWithTag("filter_RUNNING").performClick()
        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("release_item_r2", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithTag("release_item_r1", useUnmergedTree = true).assertExists()
        onNodeWithTag("release_item_r2", useUnmergedTree = true).assertExists()
    }

    // QA-RELLIST-3: Search field interaction filters list
    @Test
    fun `search field filters release list`() = runComposeUiTest {
        val testData = listOf(
            ReleaseTestData("alpha-release", "p1", "RUNNING", projectName = "Alpha", json = releaseJson("alpha-release", "p1", "RUNNING")),
            ReleaseTestData("beta-release", "p1", "SUCCEEDED", projectName = "Beta", json = releaseJson("beta-release", "p1", "SUCCEEDED")),
        )
        val client = filteringReleaseListClient(testData)
        val vm = ReleaseListViewModel(ReleaseApiClient(client), ProjectApiClient(client), MutableStateFlow(TeamId("test-team")), pollingIntervalMs = pollingDisabled)

        setContent {
            MaterialTheme {
                ReleaseListScreen(viewModel = vm, onViewRelease = {}, onBack = {})
            }
        }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("release_item_alpha-release", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithTag("release_item_beta-release", useUnmergedTree = true).assertExists()

        // Type search query
        onNodeWithTag("search_field").performTextInput("alpha")

        // Wait for debounced search to filter — beta should disappear
        waitUntil(timeoutMillis = 5000L) {
            onAllNodesWithTag("release_item_beta-release", useUnmergedTree = true).fetchSemanticsNodes().isEmpty()
        }
        onNodeWithTag("release_item_alpha-release", useUnmergedTree = true).assertExists()
    }

    // QA-RELLIST-4: Empty state with active filter shows "Clear search"
    @Test
    fun `empty state with active filter shows clear search button`() = runComposeUiTest {
        val testData = listOf(
            ReleaseTestData("r1", "p1", "RUNNING", json = releaseJson("r1", "p1", "RUNNING")),
        )
        val client = filteringReleaseListClient(testData)
        val vm = ReleaseListViewModel(ReleaseApiClient(client), ProjectApiClient(client), MutableStateFlow(TeamId("test-team")), pollingIntervalMs = pollingDisabled)

        setContent {
            MaterialTheme {
                ReleaseListScreen(viewModel = vm, onViewRelease = {}, onBack = {})
            }
        }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("release_item_r1", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }

        // Apply a status filter that yields no results
        onNodeWithTag("filter_FAILED").performClick()

        // Wait for the empty filtered state — should show "No results" + "Clear search"
        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithText("No results match your search.").fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithText("Clear search").assertExists()
    }

    // QA-RELLIST-5: "Clear filters" button clears search and all filters
    @Test
    fun `clear search button resets search and filters`() = runComposeUiTest {
        val testData = listOf(
            ReleaseTestData("r1", "p1", "RUNNING", json = releaseJson("r1", "p1", "RUNNING")),
        )
        val client = filteringReleaseListClient(testData)
        val vm = ReleaseListViewModel(ReleaseApiClient(client), ProjectApiClient(client), MutableStateFlow(TeamId("test-team")), pollingIntervalMs = pollingDisabled)

        setContent {
            MaterialTheme {
                ReleaseListScreen(viewModel = vm, onViewRelease = {}, onBack = {})
            }
        }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("release_item_r1", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }

        // Apply status filter that yields nothing
        onNodeWithTag("filter_FAILED").performClick()
        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithText("No results match your search.").fetchSemanticsNodes().isNotEmpty()
        }

        // Click the "Clear search" button
        onNodeWithText("Clear search").performClick()

        // All releases should reappear
        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("release_item_r1", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithTag("release_item_r1", useUnmergedTree = true).assertExists()
    }

    // QA-RELLIST-6: Archive flow end-to-end
    @Test
    fun `archive release end-to-end via menu and confirmation`() = runComposeUiTest {
        val archiveCalled = AtomicBoolean(false)
        val succeededRelease = releaseJson("r1", "p1", "SUCCEEDED")
        val archivedRelease = releaseJson("r1", "p1", "ARCHIVED")
        val client = HttpClient(MockEngine { request ->
            val path = request.url.encodedPath
            val method = request.method
            val jsonHeaders = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            when {
                path.endsWith("/releases") && method == HttpMethod.Get -> {
                    val release = if (archiveCalled.get()) archivedRelease else succeededRelease
                    respond("""{"releases":[$release]}""", status = HttpStatusCode.OK, headers = jsonHeaders)
                }
                path.endsWith("/releases/r1/archive") && method == HttpMethod.Post -> {
                    archiveCalled.set(true)
                    respond("""{"release":$archivedRelease}""", status = HttpStatusCode.OK, headers = jsonHeaders)
                }
                path.endsWith("/projects") -> {
                    respond("""{"projects":[]}""", status = HttpStatusCode.OK, headers = jsonHeaders)
                }
                else -> respond("{}", status = HttpStatusCode.OK, headers = jsonHeaders)
            }
        }) {
            install(ContentNegotiation) { json(AppJson) }
            install(HttpCookies)
            expectSuccess = true
        }
        val vm = ReleaseListViewModel(ReleaseApiClient(client), ProjectApiClient(client), MutableStateFlow(TeamId("test-team")), pollingIntervalMs = pollingDisabled)

        setContent {
            MaterialTheme {
                ReleaseListScreen(viewModel = vm, onViewRelease = {}, onBack = {})
            }
        }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("release_item_r1", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }

        // Open the MoreVert menu for the terminal release
        onNodeWithTag("release_menu_r1", useUnmergedTree = true).performClick()
        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("archive_menu_item", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithTag("archive_menu_item", useUnmergedTree = true).performClick()

        // Inline confirmation should appear
        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("confirm_archive_r1", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }

        // Confirm the archive action (wait for debounce to enable button)
        waitUntil(timeoutMillis = 3000L) {
            try {
                onNodeWithTag("confirm_archive_r1_confirm", useUnmergedTree = true).assertIsEnabled()
                true
            } catch (_: AssertionError) { false }
        }
        onNodeWithTag("confirm_archive_r1_confirm", useUnmergedTree = true).performClick()

        // Wait for the list to reload with archived status
        waitUntil(timeoutMillis = 5000L) {
            archiveCalled.get()
        }
        waitUntil(timeoutMillis = 5000L) {
            onAllNodesWithTag("status_badge_ARCHIVED", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        assertTrue(archiveCalled.get())
    }

    // QA-RELLIST-7: Delete flow end-to-end
    @Test
    fun `delete release end-to-end via menu and confirmation`() = runComposeUiTest {
        val deleteCalled = AtomicBoolean(false)
        val failedRelease = releaseJson("r1", "p1", "FAILED")
        val client = HttpClient(MockEngine { request ->
            val path = request.url.encodedPath
            val method = request.method
            val jsonHeaders = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            when {
                path.endsWith("/releases") && method == HttpMethod.Get -> {
                    val releases = if (deleteCalled.get()) "[]" else "[$failedRelease]"
                    respond("""{"releases":$releases}""", status = HttpStatusCode.OK, headers = jsonHeaders)
                }
                path.endsWith("/releases/r1") && method == HttpMethod.Delete -> {
                    deleteCalled.set(true)
                    respond("{}", status = HttpStatusCode.OK, headers = jsonHeaders)
                }
                path.endsWith("/projects") -> {
                    respond("""{"projects":[]}""", status = HttpStatusCode.OK, headers = jsonHeaders)
                }
                else -> respond("{}", status = HttpStatusCode.OK, headers = jsonHeaders)
            }
        }) {
            install(ContentNegotiation) { json(AppJson) }
            install(HttpCookies)
            expectSuccess = true
        }
        val vm = ReleaseListViewModel(ReleaseApiClient(client), ProjectApiClient(client), MutableStateFlow(TeamId("test-team")), pollingIntervalMs = pollingDisabled)

        setContent {
            MaterialTheme {
                ReleaseListScreen(viewModel = vm, onViewRelease = {}, onBack = {})
            }
        }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("release_item_r1", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }

        // Open the MoreVert menu
        onNodeWithTag("release_menu_r1", useUnmergedTree = true).performClick()
        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("delete_menu_item", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithTag("delete_menu_item", useUnmergedTree = true).performClick()

        // Inline confirmation should appear
        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("confirm_delete_r1", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }

        // Confirm the delete (wait for debounce to enable confirm button)
        waitUntil(timeoutMillis = 3000L) {
            try {
                onNodeWithTag("confirm_delete_r1_confirm", useUnmergedTree = true).assertIsEnabled()
                true
            } catch (_: AssertionError) { false }
        }
        onNodeWithTag("confirm_delete_r1_confirm", useUnmergedTree = true).performClick()

        // Wait for the list to reload — release should be gone
        waitUntil(timeoutMillis = 5000L) {
            deleteCalled.get()
        }
        waitUntil(timeoutMillis = 5000L) {
            onAllNodesWithTag("release_item_r1", useUnmergedTree = true).fetchSemanticsNodes().isEmpty()
        }
        assertTrue(deleteCalled.get())
    }

    // QA-RELLIST-8: Archive menu item not visible for ARCHIVED releases
    @Test
    fun `archive menu item not shown for already archived release`() = runComposeUiTest {
        val releases = """[
            {"id":"r1","projectTemplateId":"p1","status":"ARCHIVED","dagSnapshot":{"blocks":[],"edges":[],"positions":{}},"parameters":[],"startedAt":"$now","finishedAt":"$now"}
        ]"""
        val client = releaseListClient(releases = releases)
        val vm = ReleaseListViewModel(ReleaseApiClient(client), ProjectApiClient(client), MutableStateFlow(TeamId("test-team")), pollingIntervalMs = pollingDisabled)

        setContent {
            MaterialTheme {
                ReleaseListScreen(viewModel = vm, onViewRelease = {}, onBack = {})
            }
        }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("release_item_r1", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }

        // ARCHIVED is terminal so MoreVert menu should exist
        onNodeWithTag("release_menu_r1", useUnmergedTree = true).performClick()
        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("delete_menu_item", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }

        // Delete should be available, but Archive should NOT be
        onNodeWithTag("delete_menu_item", useUnmergedTree = true).assertExists()
        onNodeWithTag("archive_menu_item", useUnmergedTree = true).assertDoesNotExist()
    }

    // QA-RELLIST-9: MoreVert menu visible only for terminal releases
    @Test
    fun `more vert menu hidden for non-terminal releases`() = runComposeUiTest {
        val releases = """[
            {"id":"r-running","projectTemplateId":"p1","status":"RUNNING","dagSnapshot":{"blocks":[],"edges":[],"positions":{}},"parameters":[],"startedAt":"$now"},
            {"id":"r-pending","projectTemplateId":"p1","status":"PENDING","dagSnapshot":{"blocks":[],"edges":[],"positions":{}},"parameters":[]},
            {"id":"r-succeeded","projectTemplateId":"p1","status":"SUCCEEDED","dagSnapshot":{"blocks":[],"edges":[],"positions":{}},"parameters":[],"startedAt":"$now","finishedAt":"$now"}
        ]"""
        val client = releaseListClient(releases = releases)
        val vm = ReleaseListViewModel(ReleaseApiClient(client), ProjectApiClient(client), MutableStateFlow(TeamId("test-team")), pollingIntervalMs = pollingDisabled)

        setContent {
            MaterialTheme {
                ReleaseListScreen(viewModel = vm, onViewRelease = {}, onBack = {})
            }
        }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("release_list").fetchSemanticsNodes().isNotEmpty()
        }

        // Non-terminal: RUNNING and PENDING should have no menu button
        onNodeWithTag("release_menu_r-running", useUnmergedTree = true).assertDoesNotExist()
        onNodeWithTag("release_menu_r-pending", useUnmergedTree = true).assertDoesNotExist()
        // Terminal: SUCCEEDED should have the menu button
        onNodeWithTag("release_menu_r-succeeded", useUnmergedTree = true).assertExists()
    }

    // QA-RELLIST-10: MoreVert menu visible for all terminal statuses
    @Test
    fun `more vert menu visible for all terminal statuses`() = runComposeUiTest {
        val releases = """[
            {"id":"r-succeeded","projectTemplateId":"p1","status":"SUCCEEDED","dagSnapshot":{"blocks":[],"edges":[],"positions":{}},"parameters":[],"startedAt":"$now","finishedAt":"$now"},
            {"id":"r-failed","projectTemplateId":"p1","status":"FAILED","dagSnapshot":{"blocks":[],"edges":[],"positions":{}},"parameters":[],"startedAt":"$now","finishedAt":"$now"},
            {"id":"r-cancelled","projectTemplateId":"p1","status":"CANCELLED","dagSnapshot":{"blocks":[],"edges":[],"positions":{}},"parameters":[],"startedAt":"$now","finishedAt":"$now"},
            {"id":"r-archived","projectTemplateId":"p1","status":"ARCHIVED","dagSnapshot":{"blocks":[],"edges":[],"positions":{}},"parameters":[],"startedAt":"$now","finishedAt":"$now"}
        ]"""
        val client = releaseListClient(releases = releases)
        val vm = ReleaseListViewModel(ReleaseApiClient(client), ProjectApiClient(client), MutableStateFlow(TeamId("test-team")), pollingIntervalMs = pollingDisabled)

        setContent {
            MaterialTheme {
                ReleaseListScreen(viewModel = vm, onViewRelease = {}, onBack = {})
            }
        }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("release_list").fetchSemanticsNodes().isNotEmpty()
        }

        onNodeWithTag("release_menu_r-succeeded", useUnmergedTree = true).assertExists()
        onNodeWithTag("release_menu_r-failed", useUnmergedTree = true).assertExists()
        onNodeWithTag("release_menu_r-cancelled", useUnmergedTree = true).assertExists()
        onNodeWithTag("release_menu_r-archived", useUnmergedTree = true).assertExists()
    }

    // QA-RELLIST-11: StartReleaseInlineForm shows "no projects" message when empty
    @Test
    fun `start release form shows no projects message when no projects`() = runComposeUiTest {
        val client = releaseListClient()
        val vm = ReleaseListViewModel(ReleaseApiClient(client), ProjectApiClient(client), MutableStateFlow(TeamId("test-team")), pollingIntervalMs = pollingDisabled)

        setContent {
            MaterialTheme {
                ReleaseListScreen(viewModel = vm, onViewRelease = {}, onBack = {})
            }
        }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("empty_state").fetchSemanticsNodes().isNotEmpty()
        }

        onNodeWithTag("start_release_fab").performClick()
        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("start_release_form", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithTag("no_projects_message", useUnmergedTree = true).assertExists()
    }

    // QA-RELLIST-12: StartReleaseInlineForm confirm button disabled without selection
    @Test
    fun `start release confirm button disabled when no project selected`() = runComposeUiTest {
        val projects = """[
            {"id":"p1","name":"Pipeline A","description":"","dagGraph":{"blocks":[],"edges":[],"positions":{}},"parameters":[],"createdAt":"$now","updatedAt":"$now"}
        ]"""
        val client = releaseListClient(projects = projects)
        val vm = ReleaseListViewModel(ReleaseApiClient(client), ProjectApiClient(client), MutableStateFlow(TeamId("test-team")), pollingIntervalMs = pollingDisabled)

        setContent {
            MaterialTheme {
                ReleaseListScreen(viewModel = vm, onViewRelease = {}, onBack = {})
            }
        }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("empty_state").fetchSemanticsNodes().isNotEmpty()
        }

        onNodeWithTag("start_release_fab").performClick()
        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("start_release_form", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        // Confirm should be disabled before selecting a project
        onNodeWithTag("start_release_confirm", useUnmergedTree = true).assertIsNotEnabled()
    }

    // QA-RELLIST-13: StartReleaseInlineForm confirm enabled after selecting project
    @Test
    fun `start release confirm button enabled after selecting project`() = runComposeUiTest {
        val projects = """[
            {"id":"p1","name":"Pipeline A","description":"","dagGraph":{"blocks":[],"edges":[],"positions":{}},"parameters":[],"createdAt":"$now","updatedAt":"$now"}
        ]"""
        val client = releaseListClient(projects = projects)
        val vm = ReleaseListViewModel(ReleaseApiClient(client), ProjectApiClient(client), MutableStateFlow(TeamId("test-team")), pollingIntervalMs = pollingDisabled)

        setContent {
            MaterialTheme {
                ReleaseListScreen(viewModel = vm, onViewRelease = {}, onBack = {})
            }
        }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("empty_state").fetchSemanticsNodes().isNotEmpty()
        }

        onNodeWithTag("start_release_fab").performClick()
        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("start_release_form", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }

        // Click the project dropdown to open it, then select a project
        onNodeWithTag("project_dropdown", useUnmergedTree = true).performClick()
        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("project_option_p1", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithTag("project_option_p1", useUnmergedTree = true).performClick()
        waitForIdle()

        // Confirm should now be enabled
        onNodeWithTag("start_release_confirm", useUnmergedTree = true).assertIsEnabled()
    }

    // QA-RELLIST-14: Project filter chips appear when projects are loaded
    @Test
    fun `project filter chips shown when projects loaded`() = runComposeUiTest {
        val projects = """[
            {"id":"p1","name":"Pipeline A","description":"","dagGraph":{"blocks":[],"edges":[],"positions":{}},"parameters":[],"createdAt":"$now","updatedAt":"$now"},
            {"id":"p2","name":"Pipeline B","description":"","dagGraph":{"blocks":[],"edges":[],"positions":{}},"parameters":[],"createdAt":"$now","updatedAt":"$now"}
        ]"""
        val releases = """[
            {"id":"r1","projectTemplateId":"p1","status":"RUNNING","dagSnapshot":{"blocks":[],"edges":[],"positions":{}},"parameters":[],"startedAt":"$now"}
        ]"""
        val client = releaseListClient(releases = releases, projects = projects)
        val vm = ReleaseListViewModel(ReleaseApiClient(client), ProjectApiClient(client), MutableStateFlow(TeamId("test-team")), pollingIntervalMs = pollingDisabled)

        setContent {
            MaterialTheme {
                ReleaseListScreen(viewModel = vm, onViewRelease = {}, onBack = {})
            }
        }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("release_list").fetchSemanticsNodes().isNotEmpty()
        }

        // Project filter chips should be present
        onNodeWithTag("filter_all_projects").assertExists()
        onNodeWithTag("filter_project_p1").assertExists()
        onNodeWithTag("filter_project_p2").assertExists()
    }

    // QA-RELLIST-15: Project filter chip interaction filters list
    @Test
    fun `project filter chip filters release list by project`() = runComposeUiTest {
        val testData = listOf(
            ReleaseTestData("r1", "p1", "RUNNING", json = releaseJson("r1", "p1", "RUNNING")),
            ReleaseTestData("r2", "p2", "SUCCEEDED", json = releaseJson("r2", "p2", "SUCCEEDED")),
        )
        val projects = """[
            {"id":"p1","name":"Pipeline A","description":"","dagGraph":{"blocks":[],"edges":[],"positions":{}},"parameters":[],"createdAt":"$now","updatedAt":"$now"},
            {"id":"p2","name":"Pipeline B","description":"","dagGraph":{"blocks":[],"edges":[],"positions":{}},"parameters":[],"createdAt":"$now","updatedAt":"$now"}
        ]"""
        val client = filteringReleaseListClient(testData, projects = projects)
        val vm = ReleaseListViewModel(ReleaseApiClient(client), ProjectApiClient(client), MutableStateFlow(TeamId("test-team")), pollingIntervalMs = pollingDisabled)

        setContent {
            MaterialTheme {
                ReleaseListScreen(viewModel = vm, onViewRelease = {}, onBack = {})
            }
        }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("release_item_r1", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithTag("release_item_r2", useUnmergedTree = true).assertExists()

        // Click project filter for Pipeline A (p1)
        onNodeWithTag("filter_project_p1").performClick()

        // Wait for only p1 releases to show
        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("release_item_r2", useUnmergedTree = true).fetchSemanticsNodes().isEmpty()
        }
        onNodeWithTag("release_item_r1", useUnmergedTree = true).assertExists()
    }

    // Additional: Inline confirmation cancel dismisses the archive prompt
    @Test
    fun `archive confirmation cancel dismisses prompt`() = runComposeUiTest {
        val releases = """[
            {"id":"r1","projectTemplateId":"p1","status":"SUCCEEDED","dagSnapshot":{"blocks":[],"edges":[],"positions":{}},"parameters":[],"startedAt":"$now","finishedAt":"$now"}
        ]"""
        val client = releaseListClient(releases = releases)
        val vm = ReleaseListViewModel(ReleaseApiClient(client), ProjectApiClient(client), MutableStateFlow(TeamId("test-team")), pollingIntervalMs = pollingDisabled)

        setContent {
            MaterialTheme {
                ReleaseListScreen(viewModel = vm, onViewRelease = {}, onBack = {})
            }
        }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("release_item_r1", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }

        // Open MoreVert and click Archive
        onNodeWithTag("release_menu_r1", useUnmergedTree = true).performClick()
        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("archive_menu_item", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithTag("archive_menu_item", useUnmergedTree = true).performClick()

        // Inline confirmation should appear
        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("confirm_archive_r1", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }

        // Cancel the confirmation
        onNodeWithTag("confirm_archive_r1_cancel", useUnmergedTree = true).performClick()
        waitForIdle()

        // Confirmation should be dismissed
        onNodeWithTag("confirm_archive_r1", useUnmergedTree = true).assertDoesNotExist()
        // Release item should still be there
        onNodeWithTag("release_item_r1", useUnmergedTree = true).assertExists()
    }

    // Additional: Delete confirmation cancel dismisses the prompt
    @Test
    fun `delete confirmation cancel dismisses prompt`() = runComposeUiTest {
        val releases = """[
            {"id":"r1","projectTemplateId":"p1","status":"FAILED","dagSnapshot":{"blocks":[],"edges":[],"positions":{}},"parameters":[],"startedAt":"$now","finishedAt":"$now"}
        ]"""
        val client = releaseListClient(releases = releases)
        val vm = ReleaseListViewModel(ReleaseApiClient(client), ProjectApiClient(client), MutableStateFlow(TeamId("test-team")), pollingIntervalMs = pollingDisabled)

        setContent {
            MaterialTheme {
                ReleaseListScreen(viewModel = vm, onViewRelease = {}, onBack = {})
            }
        }

        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("release_item_r1", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }

        // Open MoreVert and click Delete
        onNodeWithTag("release_menu_r1", useUnmergedTree = true).performClick()
        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("delete_menu_item", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithTag("delete_menu_item", useUnmergedTree = true).performClick()

        // Inline confirmation should appear
        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("confirm_delete_r1", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }

        // Cancel the confirmation
        onNodeWithTag("confirm_delete_r1_cancel", useUnmergedTree = true).performClick()
        waitForIdle()

        // Confirmation should be dismissed
        onNodeWithTag("confirm_delete_r1", useUnmergedTree = true).assertDoesNotExist()
        // Release item should still be there
        onNodeWithTag("release_item_r1", useUnmergedTree = true).assertExists()
    }

    // ---- New feature tests ----

    @Test
    fun `block detail panel shows block type label`() = runComposeUiTest {
        val release = Release(
            id = ReleaseId("r1"),
            projectTemplateId = ProjectId("p1"),
            status = ReleaseStatus.SUCCEEDED,
            dagSnapshot = DagGraph(
                blocks = listOf(
                    Block.ActionBlock(id = BlockId("b1"), name = "Build Step", type = BlockType.TEAMCITY_BUILD),
                ),
                positions = mapOf(BlockId("b1") to BlockPosition(100f, 100f)),
            ),
        )
        val executions = listOf(
            BlockExecution(
                blockId = BlockId("b1"),
                releaseId = ReleaseId("r1"),
                status = BlockStatus.SUCCEEDED,
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
                    onStopRelease = {},
                    onResumeRelease = {},
                    onStopBlock = {},
                    onRerun = {},
                    onArchive = {},
                    onApproveBlock = {},
                    onBlockClick = {},
                )
            }
        }

        onNodeWithTag("execution_dag_canvas").performTouchInput {
            click(Offset(190f, 135f))
        }
        waitUntil(timeoutMillis = 3000L) { onAllNodesWithTag("block_detail_panel").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("block_type_label", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `gate approval shows progress bar`() = runComposeUiTest {
        val release = Release(
            id = ReleaseId("r1"),
            projectTemplateId = ProjectId("p1"),
            status = ReleaseStatus.RUNNING,
            dagSnapshot = DagGraph(
                blocks = listOf(
                    Block.ActionBlock(
                        id = BlockId("b1"),
                        name = "Gated Step",
                        type = BlockType.SLACK_MESSAGE,
                        preGate = Gate(approvalRule = ApprovalRule(requiredCount = 3)),
                    ),
                ),
                positions = mapOf(BlockId("b1") to BlockPosition(100f, 100f)),
            ),
        )
        val executions = listOf(
            BlockExecution(
                blockId = BlockId("b1"),
                releaseId = ReleaseId("r1"),
                status = BlockStatus.WAITING_FOR_INPUT,
                gatePhase = GatePhase.PRE,
                gateMessage = "Approve to start 'Gated Step'",
                approvals = listOf(
                    BlockApproval("u1", "alice", 1710500000000L),
                ),
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
                    onStopRelease = {},
                    onResumeRelease = {},
                    onStopBlock = {},
                    onRerun = {},
                    onArchive = {},
                    onApproveBlock = {},
                    onBlockClick = {},
                )
            }
        }

        onNodeWithTag("execution_dag_canvas").performTouchInput {
            click(Offset(190f, 135f))
        }
        waitUntil(timeoutMillis = 3000L) { onAllNodesWithTag("block_detail_panel").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("gate_approval_progress_bar", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `gate approval shows approver names`() = runComposeUiTest {
        val release = Release(
            id = ReleaseId("r1"),
            projectTemplateId = ProjectId("p1"),
            status = ReleaseStatus.RUNNING,
            dagSnapshot = DagGraph(
                blocks = listOf(
                    Block.ActionBlock(
                        id = BlockId("b1"),
                        name = "Approval Step",
                        type = BlockType.SLACK_MESSAGE,
                        preGate = Gate(approvalRule = ApprovalRule(requiredCount = 3)),
                    ),
                ),
                positions = mapOf(BlockId("b1") to BlockPosition(100f, 100f)),
            ),
        )
        val executions = listOf(
            BlockExecution(
                blockId = BlockId("b1"),
                releaseId = ReleaseId("r1"),
                status = BlockStatus.WAITING_FOR_INPUT,
                gatePhase = GatePhase.PRE,
                gateMessage = "Approve to start 'Approval Step'",
                approvals = listOf(
                    BlockApproval("u1", "alice", 1710500000000L),
                    BlockApproval("u2", "bob", 1710500060000L),
                ),
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
                    onStopRelease = {},
                    onResumeRelease = {},
                    onStopBlock = {},
                    onRerun = {},
                    onArchive = {},
                    onApproveBlock = {},
                    onBlockClick = {},
                )
            }
        }

        onNodeWithTag("execution_dag_canvas").performTouchInput {
            click(Offset(190f, 135f))
        }
        waitUntil(timeoutMillis = 3000L) { onAllNodesWithTag("block_detail_panel").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("approval_list", useUnmergedTree = true).assertExists()
        onNodeWithText("alice", substring = true, useUnmergedTree = true).assertExists()
    }

    @Test
    fun `block detail shows duration for finished block`() = runComposeUiTest {
        val startInstant = kotlin.time.Instant.parse("2026-03-13T00:00:00Z")
        val endInstant = kotlin.time.Instant.parse("2026-03-13T00:01:00Z")
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
                startedAt = startInstant,
                finishedAt = endInstant,
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
                    onStopRelease = {},
                    onResumeRelease = {},
                    onStopBlock = {},
                    onRerun = {},
                    onArchive = {},
                    onApproveBlock = {},
                    onBlockClick = {},
                )
            }
        }

        onNodeWithTag("execution_dag_canvas").performTouchInput {
            click(Offset(190f, 135f))
        }
        waitUntil(timeoutMillis = 3000L) { onAllNodesWithTag("block_detail_panel").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("block_duration_text", useUnmergedTree = true).assertExists()
        onNodeWithText("1m", substring = true, useUnmergedTree = true).assertExists()
    }

    // ---- QA Phase 4: ReleaseDetail HIGH priority gaps ----

    // --- Helper to create a standard ReleaseDetailScreen for tests ---
    private fun ComposeUiTest.setReleaseDetailContent(
        release: Release?,
        blockExecutions: List<BlockExecution> = emptyList(),
        isConnected: Boolean = true,
        reconnectAttempt: Int = 0,
        error: com.github.mr3zee.util.UiMessage? = null,
        onBack: () -> Unit = {},
        onCancel: () -> Unit = {},
        onStopRelease: () -> Unit = {},
        onResumeRelease: () -> Unit = {},
        onStopBlock: (BlockId) -> Unit = {},
        onRerun: () -> Unit = {},
        onArchive: () -> Unit = {},
        onApproveBlock: (BlockId) -> Unit = {},
        onBlockClick: (BlockId) -> Unit = {},
        onDismissError: () -> Unit = {},
    ) {
        setContent {
            MaterialTheme {
                ReleaseDetailScreen(
                    release = release,
                    blockExecutions = blockExecutions,
                    isConnected = isConnected,
                    reconnectAttempt = reconnectAttempt,
                    error = error,
                    onBack = onBack,
                    onCancel = onCancel,
                    onStopRelease = onStopRelease,
                    onResumeRelease = onResumeRelease,
                    onStopBlock = onStopBlock,
                    onRerun = onRerun,
                    onArchive = onArchive,
                    onApproveBlock = onApproveBlock,
                    onBlockClick = onBlockClick,
                    onDismissError = onDismissError,
                )
            }
        }
    }

    private fun runningRelease(
        blocks: List<Block> = emptyList(),
        positions: Map<BlockId, BlockPosition> = emptyMap(),
        edges: List<Edge> = emptyList(),
        status: ReleaseStatus = ReleaseStatus.RUNNING,
    ) = Release(
        id = ReleaseId("r1"),
        projectTemplateId = ProjectId("p1"),
        status = status,
        dagSnapshot = DagGraph(blocks = blocks, positions = positions, edges = edges),
    )

    private fun singleBlockRelease(
        blockId: String = "b1",
        blockName: String = "Build",
        blockType: BlockType = BlockType.TEAMCITY_BUILD,
        releaseStatus: ReleaseStatus = ReleaseStatus.RUNNING,
        preGate: Gate? = null,
        injectWebhookUrl: Boolean = false,
    ): Release {
        val bid = BlockId(blockId)
        return Release(
            id = ReleaseId("r1"),
            projectTemplateId = ProjectId("p1"),
            status = releaseStatus,
            dagSnapshot = DagGraph(
                blocks = listOf(
                    Block.ActionBlock(
                        id = bid,
                        name = blockName,
                        type = blockType,
                        preGate = preGate,
                        injectWebhookUrl = injectWebhookUrl,
                    ),
                ),
                positions = mapOf(bid to BlockPosition(100f, 100f)),
            ),
        )
    }

    private fun clickBlock(scope: ComposeUiTest) {
        scope.onNodeWithTag("execution_dag_canvas").performTouchInput {
            click(Offset(190f, 135f))
        }
        scope.waitUntil(timeoutMillis = 3000L) {
            scope.onAllNodesWithTag("block_detail_panel").fetchSemanticsNodes().isNotEmpty()
        }
    }

    // QA-RELDETAIL-1: Stop release flow end-to-end
    @Test
    fun `stop release flow end-to-end`() = runComposeUiTest {
        var stopReleaseCalled = false
        val release = runningRelease()

        setReleaseDetailContent(
            release = release,
            onStopRelease = { stopReleaseCalled = true },
        )

        // Stop button visible for RUNNING release
        onNodeWithTag("stop_release_button").assertExists()
        onNodeWithTag("stop_release_button").performClick()

        // Confirmation appears
        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("confirm_stop_release", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithTag("confirm_stop_release", useUnmergedTree = true).assertExists()

        // Confirm the stop
        onNodeWithTag("confirm_stop_release_confirm", useUnmergedTree = true).performClick()
        waitForIdle()

        assertTrue(stopReleaseCalled, "onStopRelease callback should have been called")
    }

    // QA-RELDETAIL-2: Resume button visible for STOPPED release
    @Test
    fun `resume button visible for STOPPED release`() = runComposeUiTest {
        val release = runningRelease(status = ReleaseStatus.STOPPED)

        setReleaseDetailContent(release = release)

        onNodeWithTag("resume_release_button").assertExists()
        onNodeWithTag("status_badge_STOPPED", useUnmergedTree = true).assertExists()
        // Stop button should NOT be visible on a STOPPED release
        onNodeWithTag("stop_release_button").assertDoesNotExist()
    }

    // QA-RELDETAIL-3: Resume release fires callback
    @Test
    fun `resume release fires callback`() = runComposeUiTest {
        var resumeCalled = false
        val release = runningRelease(status = ReleaseStatus.STOPPED)

        setReleaseDetailContent(
            release = release,
            onResumeRelease = { resumeCalled = true },
        )

        onNodeWithTag("resume_release_button").performClick()
        waitForIdle()
        assertTrue(resumeCalled, "onResumeRelease callback should have been called")
    }

    // QA-RELDETAIL-4: Cancel button for PENDING release
    @Test
    fun `cancel button visible for PENDING release`() = runComposeUiTest {
        var cancelCalled = false
        val release = runningRelease(status = ReleaseStatus.PENDING)

        setReleaseDetailContent(
            release = release,
            onCancel = { cancelCalled = true },
        )

        onNodeWithTag("cancel_release_button").assertExists()
        // No stop or resume buttons for PENDING
        onNodeWithTag("stop_release_button").assertDoesNotExist()
        onNodeWithTag("resume_release_button").assertDoesNotExist()

        // Click cancel -> confirmation -> confirm
        onNodeWithTag("cancel_release_button").performClick()
        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("confirm_cancel_release", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithTag("confirm_cancel_release_confirm", useUnmergedTree = true).performClick()
        waitForIdle()
        assertTrue(cancelCalled, "onCancel callback should have been called")
    }

    // QA-RELDETAIL-5: Stop block flow for RUNNING block
    @Test
    fun `stop block flow for RUNNING block`() = runComposeUiTest {
        var stoppedBlockId: BlockId? = null
        val release = singleBlockRelease()
        val executions = listOf(
            BlockExecution(
                blockId = BlockId("b1"),
                releaseId = ReleaseId("r1"),
                status = BlockStatus.RUNNING,
            ),
        )

        setReleaseDetailContent(
            release = release,
            blockExecutions = executions,
            onStopBlock = { stoppedBlockId = it },
        )

        clickBlock(this)

        // Stop button visible in block detail panel for a RUNNING block
        onNodeWithTag("stop_block_button").assertExists()
        onNodeWithTag("stop_block_button").performClick()

        // Confirmation appears
        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("confirm_stop_block", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithTag("confirm_stop_block", useUnmergedTree = true).assertExists()

        // Confirm
        onNodeWithTag("confirm_stop_block_confirm", useUnmergedTree = true).performClick()
        waitForIdle()
        assertEquals(BlockId("b1"), stoppedBlockId)
    }

    // QA-RELDETAIL-6: Stop block button for WAITING_FOR_INPUT block
    @Test
    fun `stop block button visible for WAITING_FOR_INPUT block`() = runComposeUiTest {
        var stoppedBlockId: BlockId? = null
        val release = singleBlockRelease(preGate = Gate())
        val executions = listOf(
            BlockExecution(
                blockId = BlockId("b1"),
                releaseId = ReleaseId("r1"),
                status = BlockStatus.WAITING_FOR_INPUT,
                gatePhase = GatePhase.PRE,
                gateMessage = "Approve to start 'Build'",
            ),
        )

        setReleaseDetailContent(
            release = release,
            blockExecutions = executions,
            onStopBlock = { stoppedBlockId = it },
        )

        clickBlock(this)

        // Both approve and stop buttons should be visible for WAITING_FOR_INPUT block
        onNodeWithTag("approve_block_button").assertExists()
        onNodeWithTag("stop_block_button").assertExists()

        // Click stop -> confirmation -> confirm
        onNodeWithTag("stop_block_button").performClick()
        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("confirm_stop_block", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithTag("confirm_stop_block_confirm", useUnmergedTree = true).performClick()
        waitForIdle()
        assertEquals(BlockId("b1"), stoppedBlockId)
    }

    // QA-RELDETAIL-7: Rerun callback fires
    @Test
    fun `rerun callback fires when confirmed`() = runComposeUiTest {
        var rerunCalled = false
        val release = runningRelease(status = ReleaseStatus.SUCCEEDED)

        setReleaseDetailContent(
            release = release,
            onRerun = { rerunCalled = true },
        )

        onNodeWithTag("rerun_release_button").assertExists()
        onNodeWithTag("rerun_release_button").performClick()
        waitForIdle()
        assertTrue(rerunCalled, "onRerun callback should have been called")
    }

    // QA-RELDETAIL-8: Archive callback fires
    @Test
    fun `archive callback fires when clicked`() = runComposeUiTest {
        var archiveCalled = false
        val release = runningRelease(status = ReleaseStatus.FAILED)

        setReleaseDetailContent(
            release = release,
            onArchive = { archiveCalled = true },
        )

        onNodeWithTag("archive_release_button").assertExists()
        onNodeWithTag("archive_release_button").performClick()
        waitForIdle()
        assertTrue(archiveCalled, "onArchive callback should have been called")
    }

    // QA-RELDETAIL-9: Error snackbar when error prop non-null
    @Test
    fun `error snackbar shown when error prop is non-null`() = runComposeUiTest {
        val release = runningRelease()
        var errorDismissed = false

        setReleaseDetailContent(
            release = release,
            error = com.github.mr3zee.util.UiMessage.Raw("Something went wrong"),
            onDismissError = { errorDismissed = true },
        )

        // Snackbar should appear with the error message
        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithText("Something went wrong", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithText("Something went wrong", substring = true).assertExists()
    }

    // ---- QA Phase 4: ReleaseDetail MEDIUM priority gaps ----

    // QA-RELDETAIL-10: Loading state shows spinner when release is null
    @Test
    fun `loading state shows progress indicator when release is null`() = runComposeUiTest {
        setReleaseDetailContent(release = null)

        onNodeWithTag("release_detail_screen").assertExists()
        // No action buttons should appear
        onNodeWithTag("cancel_release_button").assertDoesNotExist()
        onNodeWithTag("stop_release_button").assertDoesNotExist()
        onNodeWithTag("resume_release_button").assertDoesNotExist()
        onNodeWithTag("rerun_release_button").assertDoesNotExist()
        onNodeWithTag("archive_release_button").assertDoesNotExist()
        // Loading text should be present
        onNodeWithText("Loading release", substring = true).assertExists()
    }

    // QA-RELDETAIL-11: Stop release confirmation dismissal
    @Test
    fun `stop release confirmation dismissal hides confirmation`() = runComposeUiTest {
        val release = runningRelease()

        setReleaseDetailContent(release = release)

        // Open stop confirmation
        onNodeWithTag("stop_release_button").performClick()
        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("confirm_stop_release", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }

        // Dismiss it
        onNodeWithTag("confirm_stop_release_cancel", useUnmergedTree = true).performClick()
        waitForIdle()

        // Confirmation should disappear
        onNodeWithTag("confirm_stop_release", useUnmergedTree = true).assertDoesNotExist()
    }

    // QA-RELDETAIL-12: Cancel release confirmation dismissal
    @Test
    fun `cancel release confirmation dismissal hides confirmation`() = runComposeUiTest {
        val release = runningRelease()

        setReleaseDetailContent(release = release)

        // Open cancel confirmation
        onNodeWithTag("cancel_release_button").performClick()
        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("confirm_cancel_release", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }

        // Dismiss it
        onNodeWithTag("confirm_cancel_release_cancel", useUnmergedTree = true).performClick()
        waitForIdle()

        // Confirmation should disappear
        onNodeWithTag("confirm_cancel_release", useUnmergedTree = true).assertDoesNotExist()
    }

    // QA-RELDETAIL-13: Stop block confirmation dismissal
    @Test
    fun `stop block confirmation dismissal hides confirmation`() = runComposeUiTest {
        var stoppedBlockId: BlockId? = null
        val release = singleBlockRelease()
        val executions = listOf(
            BlockExecution(
                blockId = BlockId("b1"),
                releaseId = ReleaseId("r1"),
                status = BlockStatus.RUNNING,
            ),
        )

        setReleaseDetailContent(
            release = release,
            blockExecutions = executions,
            onStopBlock = { stoppedBlockId = it },
        )

        clickBlock(this)

        // Open stop block confirmation
        onNodeWithTag("stop_block_button").performClick()
        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("confirm_stop_block", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }

        // Dismiss it
        onNodeWithTag("confirm_stop_block_cancel", useUnmergedTree = true).performClick()
        waitForIdle()

        // Confirmation should disappear but the callback should NOT have been called
        onNodeWithTag("confirm_stop_block", useUnmergedTree = true).assertDoesNotExist()
        assertEquals(null, stoppedBlockId)
    }

    // QA-RELDETAIL-14: Approve block confirmation dismissal
    @Test
    fun `approve block confirmation dismissal hides confirmation`() = runComposeUiTest {
        var approvedBlockId: BlockId? = null
        val release = singleBlockRelease(preGate = Gate())
        val executions = listOf(
            BlockExecution(
                blockId = BlockId("b1"),
                releaseId = ReleaseId("r1"),
                status = BlockStatus.WAITING_FOR_INPUT,
                gatePhase = GatePhase.PRE,
                gateMessage = "Approve to start 'Build'",
            ),
        )

        setReleaseDetailContent(
            release = release,
            blockExecutions = executions,
            onApproveBlock = { approvedBlockId = it },
        )

        clickBlock(this)

        // Open approve confirmation
        onNodeWithTag("approve_block_button").performClick()
        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("confirm_approve_block", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }

        // Dismiss it
        onNodeWithTag("confirm_approve_block_cancel", useUnmergedTree = true).performClick()
        waitForIdle()

        // Confirmation should disappear and callback should NOT have been called
        onNodeWithTag("confirm_approve_block", useUnmergedTree = true).assertDoesNotExist()
        assertEquals(null, approvedBlockId)
    }

    // QA-RELDETAIL-15: Block waiting panel when block has no execution entry
    @Test
    fun `block waiting panel shown when block has no execution`() = runComposeUiTest {
        val release = singleBlockRelease()
        // No executions — the block has not started yet

        setReleaseDetailContent(
            release = release,
            blockExecutions = emptyList(),
        )

        clickBlock(this)

        // Panel should show with waiting status
        onNodeWithTag("block_detail_panel").assertExists()
        onNodeWithTag("block_status_text").assertExists()
        onNodeWithText("Waiting", substring = true).assertExists()
        onNodeWithTag("block_waiting_info").assertExists()
        onNodeWithText("waiting to execute", substring = true, useUnmergedTree = true).assertExists()
        // Type label should still be visible
        onNodeWithTag("block_type_label", useUnmergedTree = true).assertExists()
    }

    // QA-RELDETAIL-16: Stopped block shows stopped context message
    @Test
    fun `stopped block shows stopped context message`() = runComposeUiTest {
        val release = singleBlockRelease(releaseStatus = ReleaseStatus.STOPPED)
        val executions = listOf(
            BlockExecution(
                blockId = BlockId("b1"),
                releaseId = ReleaseId("r1"),
                status = BlockStatus.STOPPED,
                startedAt = Instant.parse("2026-03-13T00:00:00Z"),
            ),
        )

        setReleaseDetailContent(
            release = release,
            blockExecutions = executions,
        )

        clickBlock(this)

        onNodeWithTag("block_status_text").assertExists()
        onNodeWithText("Stopped", substring = true).assertExists()
        onNodeWithTag("stopped_context_text", useUnmergedTree = true).assertExists()
        onNodeWithText("re-execute when the release is resumed", substring = true, useUnmergedTree = true).assertExists()
        // Duration text should show "Stopped" label
        onNodeWithTag("block_duration_text", useUnmergedTree = true).assertExists()
    }

    // QA-RELDETAIL-17: Stopped block has no stop button (release is already STOPPED)
    @Test
    fun `stopped block has no stop button when release is stopped`() = runComposeUiTest {
        val release = singleBlockRelease(releaseStatus = ReleaseStatus.STOPPED)
        val executions = listOf(
            BlockExecution(
                blockId = BlockId("b1"),
                releaseId = ReleaseId("r1"),
                status = BlockStatus.STOPPED,
            ),
        )

        setReleaseDetailContent(
            release = release,
            blockExecutions = executions,
        )

        clickBlock(this)

        // Stop block button should not be present since the block is already stopped
        onNodeWithTag("stop_block_button").assertDoesNotExist()
    }

    // QA-RELDETAIL-18: Webhook status card appears when webhookStatus is set
    @Test
    fun `webhook status card shown when webhookStatus is present`() = runComposeUiTest {
        val release = singleBlockRelease(injectWebhookUrl = true)
        val executions = listOf(
            BlockExecution(
                blockId = BlockId("b1"),
                releaseId = ReleaseId("r1"),
                status = BlockStatus.RUNNING,
                webhookStatus = WebhookStatusUpdate(
                    status = "Build in progress",
                    description = "Step 3 of 5 running",
                    receivedAt = Instant.parse("2026-03-13T01:23:45Z"),
                ),
            ),
        )

        setReleaseDetailContent(
            release = release,
            blockExecutions = executions,
        )

        clickBlock(this)

        onNodeWithTag("webhook_status_card", useUnmergedTree = true).assertExists()
        onNodeWithTag("webhook_status_text", useUnmergedTree = true).assertExists()
        onNodeWithText("Build in progress", substring = true, useUnmergedTree = true).assertExists()
        onNodeWithText("Step 3 of 5 running", substring = true, useUnmergedTree = true).assertExists()
    }

    // QA-RELDETAIL-19: Webhook placeholder when webhook enabled but no status received (running)
    @Test
    fun `webhook placeholder shown when webhook enabled but no status running`() = runComposeUiTest {
        val release = singleBlockRelease(injectWebhookUrl = true)
        val executions = listOf(
            BlockExecution(
                blockId = BlockId("b1"),
                releaseId = ReleaseId("r1"),
                status = BlockStatus.RUNNING,
            ),
        )

        setReleaseDetailContent(
            release = release,
            blockExecutions = executions,
        )

        clickBlock(this)

        onNodeWithTag("webhook_status_placeholder", useUnmergedTree = true).assertExists()
        onNodeWithText("No status updates received yet", substring = true, useUnmergedTree = true).assertExists()
    }

    // QA-RELDETAIL-20: Webhook placeholder for finished build with no updates
    @Test
    fun `webhook placeholder for finished build with no status updates`() = runComposeUiTest {
        val release = singleBlockRelease(releaseStatus = ReleaseStatus.SUCCEEDED, injectWebhookUrl = true)
        val executions = listOf(
            BlockExecution(
                blockId = BlockId("b1"),
                releaseId = ReleaseId("r1"),
                status = BlockStatus.SUCCEEDED,
            ),
        )

        setReleaseDetailContent(
            release = release,
            blockExecutions = executions,
        )

        clickBlock(this)

        onNodeWithTag("webhook_status_placeholder", useUnmergedTree = true).assertExists()
        onNodeWithText("No status updates were reported", substring = true, useUnmergedTree = true).assertExists()
    }

    // QA-RELDETAIL-21: Sub-builds section appears when subBuilds is non-empty
    @Test
    fun `sub-builds section appears when execution has sub-builds`() = runComposeUiTest {
        val release = singleBlockRelease()
        val executions = listOf(
            BlockExecution(
                blockId = BlockId("b1"),
                releaseId = ReleaseId("r1"),
                status = BlockStatus.RUNNING,
                subBuilds = listOf(
                    SubBuild(id = "sb1", name = "Compile", status = SubBuildStatus.SUCCEEDED, durationSeconds = 30, dependencyLevel = 0),
                    SubBuild(id = "sb2", name = "Test", status = SubBuildStatus.RUNNING, dependencyLevel = 1),
                ),
            ),
        )

        setReleaseDetailContent(
            release = release,
            blockExecutions = executions,
        )

        clickBlock(this)

        onNodeWithTag("sub_builds_section", useUnmergedTree = true).assertExists()
        onNodeWithTag("sub_builds_header", useUnmergedTree = true).assertExists()
        // Summary shows "1/2" (1 succeeded out of 2 total)
        onNodeWithText("1", substring = true, useUnmergedTree = true).assertExists()
    }

    // QA-RELDETAIL-22: Sub-builds expanding shows individual rows
    @Test
    fun `sub-builds section expanding shows individual rows`() = runComposeUiTest {
        val release = singleBlockRelease()
        val executions = listOf(
            BlockExecution(
                blockId = BlockId("b1"),
                releaseId = ReleaseId("r1"),
                status = BlockStatus.RUNNING,
                subBuilds = listOf(
                    SubBuild(id = "sb1", name = "Compile", status = SubBuildStatus.SUCCEEDED, durationSeconds = 30, dependencyLevel = 0),
                    SubBuild(id = "sb2", name = "Test", status = SubBuildStatus.RUNNING, dependencyLevel = 0),
                ),
            ),
        )

        setReleaseDetailContent(
            release = release,
            blockExecutions = executions,
        )

        clickBlock(this)

        // Initially collapsed — rows not visible
        onNodeWithTag("sub_build_row_sb1", useUnmergedTree = true).assertDoesNotExist()

        // Click to expand
        onNodeWithTag("sub_builds_header", useUnmergedTree = true).performClick()
        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("sub_builds_list", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }

        // Individual rows should now be visible
        onNodeWithTag("sub_build_row_sb1", useUnmergedTree = true).assertExists()
        onNodeWithTag("sub_build_row_sb2", useUnmergedTree = true).assertExists()
        onNodeWithText("Compile", substring = true, useUnmergedTree = true).assertExists()
        onNodeWithText("Test", substring = true, useUnmergedTree = true).assertExists()
    }

    // QA-RELDETAIL-23: Sub-builds discovering placeholder for running TC build with no sub-builds
    @Test
    fun `sub-builds discovering placeholder for running TC build without sub-builds`() = runComposeUiTest {
        val release = singleBlockRelease()
        val executions = listOf(
            BlockExecution(
                blockId = BlockId("b1"),
                releaseId = ReleaseId("r1"),
                status = BlockStatus.RUNNING,
                // No sub-builds yet
            ),
        )

        setReleaseDetailContent(
            release = release,
            blockExecutions = executions,
        )

        clickBlock(this)

        onNodeWithTag("sub_builds_discovering", useUnmergedTree = true).assertExists()
    }

    // QA-RELDETAIL-24: Cancel button also present for STOPPED release
    @Test
    fun `cancel button present for STOPPED release alongside resume`() = runComposeUiTest {
        var cancelCalled = false
        val release = runningRelease(status = ReleaseStatus.STOPPED)

        setReleaseDetailContent(
            release = release,
            onCancel = { cancelCalled = true },
        )

        // Both resume and cancel should be visible for STOPPED release
        onNodeWithTag("resume_release_button").assertExists()
        onNodeWithTag("cancel_release_button").assertExists()

        // Cancel flow
        onNodeWithTag("cancel_release_button").performClick()
        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("confirm_cancel_release", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithTag("confirm_cancel_release_confirm", useUnmergedTree = true).performClick()
        waitForIdle()
        assertTrue(cancelCalled)
    }

    // QA-RELDETAIL-25: Rerun and archive buttons visible for FAILED release
    @Test
    fun `rerun and archive buttons visible for FAILED release`() = runComposeUiTest {
        val release = runningRelease(status = ReleaseStatus.FAILED)

        setReleaseDetailContent(release = release)

        onNodeWithTag("rerun_release_button").assertExists()
        onNodeWithTag("archive_release_button").assertExists()
        // No cancel or stop buttons for terminal state
        onNodeWithTag("cancel_release_button").assertDoesNotExist()
        onNodeWithTag("stop_release_button").assertDoesNotExist()
        onNodeWithTag("resume_release_button").assertDoesNotExist()
    }

    // QA-RELDETAIL-26: Rerun and archive buttons visible for CANCELLED release
    @Test
    fun `rerun and archive buttons visible for CANCELLED release`() = runComposeUiTest {
        val release = runningRelease(status = ReleaseStatus.CANCELLED)

        setReleaseDetailContent(release = release)

        onNodeWithTag("rerun_release_button").assertExists()
        onNodeWithTag("archive_release_button").assertExists()
    }

    // QA-RELDETAIL-27: Error snackbar with UiMessage.ServerError
    @Test
    fun `error snackbar with ServerError UiMessage`() = runComposeUiTest {
        val release = runningRelease()

        setReleaseDetailContent(
            release = release,
            error = com.github.mr3zee.util.UiMessage.ServerError,
        )

        // Should show snackbar with server error message
        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithText("error", substring = true, ignoreCase = true).fetchSemanticsNodes().isNotEmpty()
        }
    }

    // QA-RELDETAIL-28: Stop block button NOT visible when release is NOT running
    @Test
    fun `stop block button not visible when release is not RUNNING`() = runComposeUiTest {
        // STOPPED release with a WAITING_FOR_INPUT block — stop block button should NOT appear
        // because releaseStatus != RUNNING
        val release = singleBlockRelease(releaseStatus = ReleaseStatus.STOPPED, preGate = Gate())
        val executions = listOf(
            BlockExecution(
                blockId = BlockId("b1"),
                releaseId = ReleaseId("r1"),
                status = BlockStatus.WAITING_FOR_INPUT,
                gatePhase = GatePhase.PRE,
                gateMessage = "Approve to start 'Build'",
            ),
        )

        setReleaseDetailContent(
            release = release,
            blockExecutions = executions,
        )

        clickBlock(this)

        // Approve button should still be visible
        onNodeWithTag("approve_block_button").assertExists()
        // But stop block button should NOT be visible (release is STOPPED, not RUNNING)
        onNodeWithTag("stop_block_button").assertDoesNotExist()
    }

    // QA-RELDETAIL-29: Webhook status card NOT shown when webhook not enabled and no status
    @Test
    fun `webhook status card not shown when webhook not enabled`() = runComposeUiTest {
        val release = singleBlockRelease(injectWebhookUrl = false)
        val executions = listOf(
            BlockExecution(
                blockId = BlockId("b1"),
                releaseId = ReleaseId("r1"),
                status = BlockStatus.RUNNING,
            ),
        )

        setReleaseDetailContent(
            release = release,
            blockExecutions = executions,
        )

        clickBlock(this)

        // Neither webhook card nor placeholder should appear
        onNodeWithTag("webhook_status_card", useUnmergedTree = true).assertDoesNotExist()
        onNodeWithTag("webhook_status_placeholder", useUnmergedTree = true).assertDoesNotExist()
    }
}
