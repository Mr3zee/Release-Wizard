package com.github.mr3zee

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import com.github.mr3zee.api.ProjectApiClient
import com.github.mr3zee.api.ReleaseApiClient
import com.github.mr3zee.model.*
import com.github.mr3zee.releases.*
import io.ktor.http.*
import kotlin.test.Test

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
}
