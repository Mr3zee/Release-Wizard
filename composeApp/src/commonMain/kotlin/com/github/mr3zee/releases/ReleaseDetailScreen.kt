package com.github.mr3zee.releases

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.github.mr3zee.model.*
import com.github.mr3zee.model.isTerminal

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReleaseDetailScreen(
    release: Release?,
    blockExecutions: List<BlockExecution>,
    isConnected: Boolean,
    reconnectAttempt: Int = 0,
    onBack: () -> Unit,
    onCancel: () -> Unit,
    onRerun: () -> Unit,
    onArchive: () -> Unit,
    onApproveBlock: (BlockId) -> Unit,
    onBlockClick: (BlockId) -> Unit,
) {
    var selectedBlockId by remember { mutableStateOf<BlockId?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text("Release")
                        if (release != null) {
                            StatusBadge(release.status)
                        }
                    }
                },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text("Back")
                    }
                },
                actions = {
                    if (!isConnected) {
                        val disconnectedText = if (reconnectAttempt > 0) {
                            "Reconnecting (attempt $reconnectAttempt)..."
                        } else {
                            "Disconnected"
                        }
                        Text(
                            text = disconnectedText,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier
                                .padding(end = 8.dp)
                                .testTag("disconnected_indicator"),
                        )
                    }
                    if (release?.status == ReleaseStatus.RUNNING) {
                        TextButton(
                            onClick = onCancel,
                            modifier = Modifier.testTag("cancel_release_button"),
                        ) {
                            Text("Cancel", color = MaterialTheme.colorScheme.error)
                        }
                    }
                    if (release != null && release.status.isTerminal) {
                        TextButton(
                            onClick = onRerun,
                            modifier = Modifier.testTag("rerun_release_button"),
                        ) {
                            Text("Re-run")
                        }
                        if (release.status != ReleaseStatus.ARCHIVED) {
                            TextButton(
                                onClick = onArchive,
                                modifier = Modifier.testTag("archive_release_button"),
                            ) {
                                Text("Archive")
                            }
                        }
                    }
                },
            )
        },
        modifier = Modifier.testTag("release_detail_screen"),
    ) { padding ->
        if (release == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            // DAG Canvas
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                ExecutionDagCanvas(
                    graph = release.dagSnapshot,
                    blockExecutions = blockExecutions,
                    onBlockClick = { blockId ->
                        selectedBlockId = blockId
                        onBlockClick(blockId)
                    },
                )
            }

            // Block detail panel
            selectedBlockId?.let { blockId ->
                val execution = blockExecutions.find { it.blockId == blockId }
                val block = release.dagSnapshot.blocks.find { it.id == blockId }
                if (execution != null && block != null) {
                    BlockDetailPanel(
                        block = block,
                        execution = execution,
                        onApprove = { onApproveBlock(blockId) },
                        onDismiss = { selectedBlockId = null },
                    )
                }
            }
        }
    }
}

@Composable
private fun BlockDetailPanel(
    block: Block,
    execution: BlockExecution,
    onApprove: () -> Unit,
    onDismiss: () -> Unit,
) {
    Surface(
        tonalElevation = 2.dp,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("block_detail_panel"),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = block.name,
                    style = MaterialTheme.typography.titleMedium,
                )
                TextButton(onClick = onDismiss) {
                    Text("Close")
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Status: ${execution.status.name}",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.testTag("block_status_text"),
            )

            execution.error?.let { error ->
                Spacer(modifier = Modifier.height(4.dp))
                ErrorDetailSection(
                    error = error,
                    finishedAt = execution.finishedAt,
                )
            }

            val genericOutputs = execution.outputs.filterKeys { it != BlockExecution.ARTIFACTS_OUTPUT_KEY }
            if (genericOutputs.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Outputs:",
                    style = MaterialTheme.typography.labelMedium,
                )
                genericOutputs.forEach { (key, value) ->
                    Text(
                        text = "  $key: $value",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            execution.outputs[BlockExecution.ARTIFACTS_OUTPUT_KEY]?.let { artifactsJson ->
                ArtifactTreeView(artifactsJson = artifactsJson)
            }

            if (execution.status == BlockStatus.WAITING_FOR_INPUT) {
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = onApprove,
                    modifier = Modifier.testTag("approve_block_button"),
                ) {
                    Text("Approve")
                }
            }
        }
    }
}
