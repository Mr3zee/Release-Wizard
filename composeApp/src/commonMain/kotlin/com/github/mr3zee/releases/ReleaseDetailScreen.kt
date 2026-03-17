package com.github.mr3zee.releases

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.github.mr3zee.components.RwButton
import com.github.mr3zee.components.RwButtonVariant
import com.github.mr3zee.model.*
import com.github.mr3zee.model.isTerminal
import com.github.mr3zee.theme.AppTypography
import com.github.mr3zee.theme.Spacing
import com.github.mr3zee.util.UiMessage
import com.github.mr3zee.util.displayName
import com.github.mr3zee.util.resolve
import com.github.mr3zee.util.typeLabel
import com.github.mr3zee.i18n.packStringResource
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
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
            title = { Text(packStringResource(Res.string.releases_cancel_title)) },
            text = { Text(packStringResource(Res.string.releases_cancel_body)) },
            confirmButton = {
                RwButton(
                    onClick = {
                        showCancelConfirmation = false
                        onCancel()
                    },
                    modifier = Modifier.testTag("confirm_cancel_button"),
                    variant = RwButtonVariant.Ghost,
                    contentColor = MaterialTheme.colorScheme.error,
                ) {
                    Text(packStringResource(Res.string.releases_cancel_confirm))
                }
            },
            dismissButton = {
                RwButton(
                    onClick = { showCancelConfirmation = false },
                    modifier = Modifier.testTag("dismiss_cancel_button"),
                    variant = RwButtonVariant.Ghost,
                ) {
                    Text(packStringResource(Res.string.releases_keep_running))
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
            title = { Text(packStringResource(Res.string.releases_approve_title, approveBlock?.name ?: packStringResource(Res.string.releases_approve_fallback_name))) },
            text = { Text(approveMessage ?: packStringResource(Res.string.releases_approve_default_message)) },
            confirmButton = {
                RwButton(
                    onClick = {
                        showApproveConfirmation = null
                        onApproveBlock(blockId)
                    },
                    modifier = Modifier.testTag("confirm_approve_button"),
                    variant = RwButtonVariant.Ghost,
                ) {
                    Text(packStringResource(Res.string.common_approve))
                }
            },
            dismissButton = {
                RwButton(
                    onClick = { showApproveConfirmation = null },
                    modifier = Modifier.testTag("dismiss_approve_button"),
                    variant = RwButtonVariant.Ghost,
                ) {
                    Text(packStringResource(Res.string.common_cancel))
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
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        Text(
                            packStringResource(Res.string.releases_detail_title),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (release != null) {
                            StatusBadge(release.status)
                        }
                    }
                },
                navigationIcon = {
                    RwButton(onClick = onBack, variant = RwButtonVariant.Ghost) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = packStringResource(Res.string.common_navigate_back))
                        Text(packStringResource(Res.string.common_back))
                    }
                },
                actions = {
                    if (!isConnected) {
                        val disconnectedText = if (reconnectAttempt > 0) {
                            packStringResource(Res.string.releases_reconnecting, reconnectAttempt)
                        } else {
                            packStringResource(Res.string.releases_disconnected)
                        }
                        Text(
                            text = disconnectedText,
                            color = MaterialTheme.colorScheme.error,
                            style = AppTypography.caption,
                            modifier = Modifier
                                .padding(end = Spacing.sm)
                                .testTag("disconnected_indicator"),
                        )
                    }
                    if (release?.status == ReleaseStatus.RUNNING) {
                        RwButton(
                            onClick = { showCancelConfirmation = true },
                            modifier = Modifier.testTag("cancel_release_button"),
                            variant = RwButtonVariant.Ghost,
                            contentColor = MaterialTheme.colorScheme.error,
                        ) {
                            Text(packStringResource(Res.string.common_cancel))
                        }
                    }
                    if (release != null && release.status.isTerminal) {
                        RwButton(
                            onClick = onRerun,
                            modifier = Modifier.testTag("rerun_release_button"),
                            variant = RwButtonVariant.Ghost,
                        ) {
                            Text(packStringResource(Res.string.releases_rerun))
                        }
                        if (release.status != ReleaseStatus.ARCHIVED) {
                            RwButton(
                                onClick = onArchive,
                                modifier = Modifier.testTag("archive_release_button"),
                                variant = RwButtonVariant.Ghost,
                            ) {
                                Text(packStringResource(Res.string.releases_archive))
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
            modifier = Modifier
                .heightIn(max = 350.dp)
                .verticalScroll(rememberScrollState())
                .padding(Spacing.lg),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = block.name,
                        style = AppTypography.heading,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = block.typeLabel(),
                        style = AppTypography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag("block_type_label"),
                    )
                }
                RwButton(onClick = onDismiss, variant = RwButtonVariant.Ghost) {
                    Text(packStringResource(Res.string.common_close))
                }
            }

            Spacer(modifier = Modifier.height(Spacing.sm))

            Text(
                text = packStringResource(Res.string.releases_block_status, execution.status.displayName()),
                style = AppTypography.body,
                modifier = Modifier.testTag("block_status_text"),
            )

            // Duration / elapsed time
            val startedAt = execution.startedAt
            val finishedAt = execution.finishedAt
            if (startedAt != null) {
                Spacer(modifier = Modifier.height(Spacing.xs))
                if (finishedAt != null) {
                    // Completed block — show static duration
                    val duration = finishedAt - startedAt
                    Text(
                        text = packStringResource(Res.string.releases_duration, formatDuration(duration)),
                        style = AppTypography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag("block_duration_text"),
                    )
                } else if (execution.status == BlockStatus.RUNNING) {
                    // Running block — live ticker
                    var elapsed by remember { mutableStateOf("") }
                    LaunchedEffect(startedAt) {
                        while (true) {
                            val now = Clock.System.now()
                            elapsed = formatDuration(now - startedAt)
                            kotlinx.coroutines.delay(1000)
                        }
                    }
                    Text(
                        text = packStringResource(Res.string.releases_elapsed, elapsed),
                        style = AppTypography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag("block_duration_text"),
                    )
                }
            }

            // Webhook status update section
            WebhookStatusSection(block = block, execution = execution, hasSubBuilds = execution.subBuilds.isNotEmpty())

            // Sub-builds section
            if (execution.subBuilds.isNotEmpty()) {
                Spacer(modifier = Modifier.height(Spacing.sm))
                SubBuildsSection(
                    subBuilds = execution.subBuilds,
                    blockType = (block as? Block.ActionBlock)?.type,
                )
            } else if (execution.status == BlockStatus.RUNNING && (block as? Block.ActionBlock)?.type in listOf(BlockType.TEAMCITY_BUILD, BlockType.GITHUB_ACTION)) {
                SubBuildsDiscoveringPlaceholder(
                    blockType = (block as? Block.ActionBlock)?.type,
                )
            }

            execution.error?.let { errorMsg ->
                Spacer(modifier = Modifier.height(Spacing.sm))
                ErrorDetailSection(
                    error = errorMsg,
                    finishedAt = execution.finishedAt,
                )
            }

            val genericOutputs = execution.outputs.filterKeys { it != BlockExecution.ARTIFACTS_OUTPUT_KEY }
            if (genericOutputs.isNotEmpty()) {
                Spacer(modifier = Modifier.height(Spacing.sm))
                Text(
                    text = packStringResource(Res.string.releases_block_outputs),
                    style = AppTypography.label,
                )
                genericOutputs.forEach { (key, value) ->
                    Text(
                        text = packStringResource(Res.string.releases_block_output_entry, key, value),
                        style = AppTypography.bodySmall,
                        modifier = Modifier.padding(start = Spacing.lg),
                    )
                }
            }

            execution.outputs[BlockExecution.ARTIFACTS_OUTPUT_KEY]?.let { artifactsJson ->
                ArtifactTreeView(artifactsJson = artifactsJson)
            }

            if (execution.status == BlockStatus.WAITING_FOR_INPUT) {
                Spacer(modifier = Modifier.height(Spacing.sm))

                // Gate context
                execution.gateMessage?.let { msg ->
                    Text(
                        text = msg,
                        style = AppTypography.subheading,
                        modifier = Modifier.testTag("gate_message_text"),
                    )
                    Spacer(modifier = Modifier.height(Spacing.xs))
                }

                val phaseContext = when (execution.gatePhase) {
                    GatePhase.PRE -> packStringResource(Res.string.releases_gate_phase_pre)
                    GatePhase.POST -> packStringResource(Res.string.releases_gate_phase_post)
                    null -> packStringResource(Res.string.releases_gate_phase_unknown)
                }
                Text(
                    text = phaseContext,
                    style = AppTypography.bodySmall,
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
                    val requiredCount = g.approvalRule.requiredCount
                    Spacer(modifier = Modifier.height(Spacing.xs))
                    Text(
                        text = packStringResource(Res.string.releases_approval_progress, execution.approvals.size, requiredCount),
                        style = AppTypography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag("gate_approval_progress"),
                    )

                    // Progress bar
                    if (requiredCount > 0) {
                        LinearProgressIndicator(
                            progress = { (execution.approvals.size.toFloat() / requiredCount).coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = Spacing.xs)
                                .testTag("gate_approval_progress_bar"),
                        )
                    }

                    // Individual approvals
                    if (execution.approvals.isNotEmpty()) {
                        Column(modifier = Modifier.testTag("approval_list")) {
                            execution.approvals.forEach { approval ->
                                val instant = Instant.fromEpochMilliseconds(approval.approvedAt)
                                val dateTime = instant.toLocalDateTime(TimeZone.currentSystemDefault())
                                val formatted = "${dateTime.date} ${dateTime.hour.toString().padStart(2, '0')}:${dateTime.minute.toString().padStart(2, '0')}"
                                Text(
                                    text = "\u2713 ${approval.username} — $formatted",
                                    style = AppTypography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(Spacing.xs))
                RwButton(
                    onClick = onApprove,
                    modifier = Modifier.testTag("approve_block_button"),
                    variant = RwButtonVariant.Primary,
                ) {
                    Text(packStringResource(Res.string.common_approve))
                }
            }
        }
    }
}

@Composable
private fun WebhookStatusSection(block: Block, execution: BlockExecution, hasSubBuilds: Boolean = false) {
    val webhookStatus = execution.webhookStatus
    val isWebhookEnabled = (block as? Block.ActionBlock)?.injectWebhookUrl == true

    if (webhookStatus != null) {
        Spacer(modifier = Modifier.height(Spacing.sm))
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = MaterialTheme.shapes.small,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("webhook_status_card"),
        ) {
            Column(modifier = Modifier.padding(Spacing.sm)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = webhookStatus.status,
                        style = AppTypography.body.copy(fontWeight = FontWeight.Medium),
                        modifier = Modifier.weight(1f).testTag("webhook_status_text"),
                    )
                    val time = webhookStatus.receivedAt.toLocalDateTime(TimeZone.currentSystemDefault())
                    Text(
                        text = "${time.hour.toString().padStart(2, '0')}:${time.minute.toString().padStart(2, '0')}:${time.second.toString().padStart(2, '0')}",
                        style = AppTypography.caption,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                val desc = webhookStatus.description
                if (!desc.isNullOrBlank()) {
                    Text(
                        text = desc,
                        style = AppTypography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    } else if (isWebhookEnabled && !hasSubBuilds) {
        Spacer(modifier = Modifier.height(Spacing.sm))
        val text = when (execution.status) {
            BlockStatus.RUNNING -> packStringResource(Res.string.releases_webhook_no_updates_running)
            BlockStatus.SUCCEEDED, BlockStatus.FAILED -> packStringResource(Res.string.releases_webhook_no_updates_finished)
            else -> null
        }

        if (text != null) {
            Text(
                text = text,
                style = AppTypography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("webhook_status_placeholder"),
            )
        }
    }
}

private fun formatDuration(duration: Duration): String {
    val totalSeconds = duration.inWholeSeconds.coerceAtLeast(0)
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return buildString {
        if (h > 0) append("${h}h ")
        if (h > 0 || m > 0) append("${m}m ")
        append("${s}s")
    }.trim()
}
