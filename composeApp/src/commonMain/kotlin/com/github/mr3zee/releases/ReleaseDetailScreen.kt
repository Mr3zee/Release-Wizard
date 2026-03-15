package com.github.mr3zee.releases

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.github.mr3zee.model.*
import com.github.mr3zee.model.isTerminal
import com.github.mr3zee.util.UiMessage
import com.github.mr3zee.util.displayName
import com.github.mr3zee.util.resolve
import org.jetbrains.compose.resources.stringResource
import releasewizard.composeapp.generated.resources.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReleaseDetailScreen(
    release: Release?,
    blockExecutions: List<BlockExecution>,
    isConnected: Boolean,
    reconnectAttempt: Int = 0,
    error: UiMessage? = null,
    onBack: () -> Unit,
    onCancel: () -> Unit,
    onRerun: () -> Unit,
    onArchive: () -> Unit,
    onApproveBlock: (BlockId) -> Unit,
    onBlockClick: (BlockId) -> Unit,
    onDismissError: () -> Unit = {},
) {
    var selectedBlockId by remember(release?.id) { mutableStateOf<BlockId?>(null) }
    var showCancelConfirmation by remember { mutableStateOf(false) }
    var showApproveConfirmation by remember { mutableStateOf<BlockId?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }
    val resolvedError = error?.resolve()

    // Show errors via snackbar
    LaunchedEffect(error) {
        val msg = resolvedError ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(
            message = msg,
            duration = SnackbarDuration.Long,
        )
        onDismissError()
    }

    // Cancel release confirmation dialog
    if (showCancelConfirmation) {
        AlertDialog(
            onDismissRequest = { showCancelConfirmation = false },
            title = { Text(stringResource(Res.string.releases_cancel_title)) },
            text = { Text(stringResource(Res.string.releases_cancel_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showCancelConfirmation = false
                        onCancel()
                    },
                    modifier = Modifier.testTag("confirm_cancel_button"),
                ) {
                    Text(stringResource(Res.string.releases_cancel_confirm), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showCancelConfirmation = false },
                    modifier = Modifier.testTag("dismiss_cancel_button"),
                ) {
                    Text(stringResource(Res.string.releases_keep_running))
                }
            },
            modifier = Modifier.testTag("cancel_confirmation_dialog"),
        )
    }

    // Approve block confirmation dialog
    showApproveConfirmation?.let { blockId ->
        val approveBlock = release?.dagSnapshot?.blocks?.find { it.id == blockId }
        val approveExec = blockExecutions.find { it.blockId == blockId }
        val approveMessage = approveExec?.gateMessage
        AlertDialog(
            onDismissRequest = { showApproveConfirmation = null },
            title = { Text(stringResource(Res.string.releases_approve_title, approveBlock?.name ?: stringResource(Res.string.releases_approve_fallback_name))) },
            text = { Text(approveMessage ?: stringResource(Res.string.releases_approve_default_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showApproveConfirmation = null
                        onApproveBlock(blockId)
                    },
                    modifier = Modifier.testTag("confirm_approve_button"),
                ) {
                    Text(stringResource(Res.string.common_approve))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showApproveConfirmation = null },
                    modifier = Modifier.testTag("dismiss_approve_button"),
                ) {
                    Text(stringResource(Res.string.common_cancel))
                }
            },
            modifier = Modifier.testTag("approve_confirmation_dialog"),
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            stringResource(Res.string.releases_detail_title),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (release != null) {
                            StatusBadge(release.status)
                        }
                    }
                },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.common_navigate_back))
                        Text(stringResource(Res.string.common_back))
                    }
                },
                actions = {
                    if (!isConnected) {
                        val disconnectedText = if (reconnectAttempt > 0) {
                            stringResource(Res.string.releases_reconnecting, reconnectAttempt)
                        } else {
                            stringResource(Res.string.releases_disconnected)
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
                            onClick = { showCancelConfirmation = true },
                            modifier = Modifier.testTag("cancel_release_button"),
                        ) {
                            Text(stringResource(Res.string.common_cancel), color = MaterialTheme.colorScheme.error)
                        }
                    }
                    if (release != null && release.status.isTerminal) {
                        TextButton(
                            onClick = onRerun,
                            modifier = Modifier.testTag("rerun_release_button"),
                        ) {
                            Text(stringResource(Res.string.releases_rerun))
                        }
                        if (release.status != ReleaseStatus.ARCHIVED) {
                            TextButton(
                                onClick = onArchive,
                                modifier = Modifier.testTag("archive_release_button"),
                            ) {
                                Text(stringResource(Res.string.releases_archive))
                            }
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
                        onApprove = { showApproveConfirmation = blockId },
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
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onDismiss) {
                    Text(stringResource(Res.string.common_close))
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = stringResource(Res.string.releases_block_status, execution.status.displayName()),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.testTag("block_status_text"),
            )

            execution.error?.let { errorMsg ->
                Spacer(modifier = Modifier.height(4.dp))
                ErrorDetailSection(
                    error = errorMsg,
                    finishedAt = execution.finishedAt,
                )
            }

            val genericOutputs = execution.outputs.filterKeys { it != BlockExecution.ARTIFACTS_OUTPUT_KEY }
            if (genericOutputs.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(Res.string.releases_block_outputs),
                    style = MaterialTheme.typography.labelMedium,
                )
                genericOutputs.forEach { (key, value) ->
                    Text(
                        text = stringResource(Res.string.releases_block_output_entry, key, value),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(start = 16.dp),
                    )
                }
            }

            execution.outputs[BlockExecution.ARTIFACTS_OUTPUT_KEY]?.let { artifactsJson ->
                ArtifactTreeView(artifactsJson = artifactsJson)
            }

            if (execution.status == BlockStatus.WAITING_FOR_INPUT) {
                Spacer(modifier = Modifier.height(8.dp))

                // Gate context
                execution.gateMessage?.let { msg ->
                    Text(
                        text = msg,
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.testTag("gate_message_text"),
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }

                val phaseContext = when (execution.gatePhase) {
                    GatePhase.PRE -> stringResource(Res.string.releases_gate_phase_pre)
                    GatePhase.POST -> stringResource(Res.string.releases_gate_phase_post)
                    null -> stringResource(Res.string.releases_gate_phase_unknown)
                }
                Text(
                    text = phaseContext,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("gate_phase_text"),
                )

                // Approval progress
                val actionBlock = block as? Block.ActionBlock
                val gate = when (execution.gatePhase) {
                    GatePhase.PRE -> actionBlock?.preGate
                    GatePhase.POST -> actionBlock?.postGate
                    null -> null // gatePhase should always be set; don't guess
                }
                gate?.let { g ->
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(Res.string.releases_approval_progress, execution.approvals.size, g.approvalRule.requiredCount),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag("gate_approval_progress"),
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))
                Button(
                    onClick = onApprove,
                    modifier = Modifier.testTag("approve_block_button"),
                ) {
                    Text(stringResource(Res.string.common_approve))
                }
            }
        }
    }
}
