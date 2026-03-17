package com.github.mr3zee

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import com.github.mr3zee.editor.BlockPropertiesPanel
import com.github.mr3zee.model.*
import com.github.mr3zee.releases.ReleaseDetailScreen
import kotlin.test.Test
import kotlin.time.Instant

@OptIn(ExperimentalTestApi::class)
class WebhookStatusUiTest {

    // ---- BlockPropertiesPanel: inject webhook checkbox ----

    @Test
    fun `inject webhook checkbox appears for TeamCity build blocks`() = runComposeUiTest {
        val block = Block.ActionBlock(
            id = BlockId("b1"),
            name = "TC Build",
            type = BlockType.TEAMCITY_BUILD,
        )

        setContent {
            MaterialTheme {
                BlockPropertiesPanel(
                    block = block,
                    graph = DagGraph(),
                    projectParameters = emptyList(),
                    onUpdateName = { _, _ -> },
                    onUpdateType = { _, _ -> },
                    onUpdateParameters = { _, _ -> },
                    onUpdateTimeout = { _, _ -> },
                    onUpdatePreGate = { _, _ -> },
                    onUpdatePostGate = { _, _ -> },
                    onUpdateInjectWebhookUrl = { _, _ -> },
                )
            }
        }

        onNodeWithTag("inject_webhook_url_checkbox", useUnmergedTree = true).assertExists()
        onNodeWithText("Enable build status reporting").assertExists()
    }

    @Test
    fun `inject webhook checkbox does NOT appear for GitHub Action blocks`() = runComposeUiTest {
        val block = Block.ActionBlock(
            id = BlockId("b1"),
            name = "GH Action",
            type = BlockType.GITHUB_ACTION,
        )

        setContent {
            MaterialTheme {
                BlockPropertiesPanel(
                    block = block,
                    graph = DagGraph(),
                    projectParameters = emptyList(),
                    onUpdateName = { _, _ -> },
                    onUpdateType = { _, _ -> },
                    onUpdateParameters = { _, _ -> },
                    onUpdateTimeout = { _, _ -> },
                    onUpdatePreGate = { _, _ -> },
                    onUpdatePostGate = { _, _ -> },
                    onUpdateInjectWebhookUrl = { _, _ -> },
                )
            }
        }

        onNodeWithTag("inject_webhook_url_checkbox", useUnmergedTree = true).assertDoesNotExist()
    }

    // ---- ReleaseDetailScreen: webhook status display ----
    // WebhookStatusSection is private inside ReleaseDetailScreen.kt,
    // so we test it indirectly through ReleaseDetailScreen by clicking
    // on a block in the execution DAG canvas to open the BlockDetailPanel.

    @Test
    fun `webhook status card appears when webhookStatus is present`() = runComposeUiTest {
        val release = Release(
            id = ReleaseId("r1"),
            projectTemplateId = ProjectId("p1"),
            status = ReleaseStatus.RUNNING,
            dagSnapshot = DagGraph(
                blocks = listOf(
                    Block.ActionBlock(
                        id = BlockId("b1"),
                        name = "Build",
                        type = BlockType.TEAMCITY_BUILD,
                        injectWebhookUrl = true,
                    ),
                ),
                positions = mapOf(BlockId("b1") to BlockPosition(100f, 100f)),
            ),
        )
        val executions = listOf(
            BlockExecution(
                blockId = BlockId("b1"),
                releaseId = ReleaseId("r1"),
                status = BlockStatus.RUNNING,
                webhookStatus = WebhookStatusUpdate(
                    status = "Build is compiling",
                    description = "Step 3 of 5",
                    receivedAt = Instant.fromEpochMilliseconds(1710288000000L),
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
                    onRerun = {},
                    onArchive = {},
                    onApproveBlock = {},
                    onBlockClick = {},
                )
            }
        }

        // Click block on canvas to open detail panel.
        // Block "b1" is positioned at (100, 100) in the DAG. Block width=180, height=70.
        // Click at center of block: x=100+90=190, y=100+35=135.
        // If block layout dimensions change, update these coordinates accordingly.
        onNodeWithTag("execution_dag_canvas").performTouchInput {
            click(Offset(190f, 135f))
        }
        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("block_detail_panel").fetchSemanticsNodes().isNotEmpty()
        }

        onNodeWithTag("webhook_status_card", useUnmergedTree = true).assertExists()
        onNodeWithTag("webhook_status_text", useUnmergedTree = true).assertExists()
        onNodeWithText("Build is compiling", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `webhook status placeholder shows when RUNNING with no updates`() = runComposeUiTest {
        val release = Release(
            id = ReleaseId("r1"),
            projectTemplateId = ProjectId("p1"),
            status = ReleaseStatus.RUNNING,
            dagSnapshot = DagGraph(
                blocks = listOf(
                    Block.ActionBlock(
                        id = BlockId("b1"),
                        name = "Build",
                        type = BlockType.TEAMCITY_BUILD,
                        injectWebhookUrl = true,
                    ),
                ),
                positions = mapOf(BlockId("b1") to BlockPosition(100f, 100f)),
            ),
        )
        val executions = listOf(
            BlockExecution(
                blockId = BlockId("b1"),
                releaseId = ReleaseId("r1"),
                status = BlockStatus.RUNNING,
                webhookStatus = null,
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
                    onRerun = {},
                    onArchive = {},
                    onApproveBlock = {},
                    onBlockClick = {},
                )
            }
        }

        // Block "b1" is positioned at (100, 100) in the DAG. Block width=180, height=70.
        // Click at center of block: x=100+90=190, y=100+35=135.
        // If block layout dimensions change, update these coordinates accordingly.
        onNodeWithTag("execution_dag_canvas").performTouchInput {
            click(Offset(190f, 135f))
        }
        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("block_detail_panel").fetchSemanticsNodes().isNotEmpty()
        }

        onNodeWithTag("webhook_status_placeholder", useUnmergedTree = true).assertExists()
        onNodeWithText("No status updates received yet", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `webhook status placeholder shows finished message when SUCCEEDED`() = runComposeUiTest {
        val release = Release(
            id = ReleaseId("r1"),
            projectTemplateId = ProjectId("p1"),
            status = ReleaseStatus.SUCCEEDED,
            dagSnapshot = DagGraph(
                blocks = listOf(
                    Block.ActionBlock(
                        id = BlockId("b1"),
                        name = "Build",
                        type = BlockType.TEAMCITY_BUILD,
                        injectWebhookUrl = true,
                    ),
                ),
                positions = mapOf(BlockId("b1") to BlockPosition(100f, 100f)),
            ),
        )
        val executions = listOf(
            BlockExecution(
                blockId = BlockId("b1"),
                releaseId = ReleaseId("r1"),
                status = BlockStatus.SUCCEEDED,
                webhookStatus = null,
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
                    onRerun = {},
                    onArchive = {},
                    onApproveBlock = {},
                    onBlockClick = {},
                )
            }
        }

        // Block "b1" is positioned at (100, 100) in the DAG. Block width=180, height=70.
        // Click at center of block: x=100+90=190, y=100+35=135.
        // If block layout dimensions change, update these coordinates accordingly.
        onNodeWithTag("execution_dag_canvas").performTouchInput {
            click(Offset(190f, 135f))
        }
        waitUntil(timeoutMillis = 3000L) {
            onAllNodesWithTag("block_detail_panel").fetchSemanticsNodes().isNotEmpty()
        }

        onNodeWithTag("webhook_status_placeholder", useUnmergedTree = true).assertExists()
        onNodeWithText("No status updates were reported by this build", useUnmergedTree = true).assertExists()
    }
}
