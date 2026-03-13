package com.github.mr3zee.releases

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.github.mr3zee.model.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReleaseDetailScreen(
    release: Release?,
    blockExecutions: List<BlockExecution>,
    isConnected: Boolean,
    onBack: () -> Unit,
    onCancel: () -> Unit,
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
                        Text(
                            text = "Disconnected",
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

            if (execution.error != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Error: ${execution.error}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.testTag("block_error_text"),
                )
            }

            if (execution.outputs.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Outputs:",
                    style = MaterialTheme.typography.labelMedium,
                )
                execution.outputs.forEach { (key, value) ->
                    Text(
                        text = "  $key: $value",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
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
