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
import com.github.mr3zee.components.RwInlineConfirmation
import com.github.mr3zee.components.RwTooltip
import com.github.mr3zee.keyboard.ProvideShortcutActions
import com.github.mr3zee.keyboard.ShortcutActions
import com.github.mr3zee.model.*
import com.github.mr3zee.model.isTerminal
import com.github.mr3zee.theme.AppTypography
import com.github.mr3zee.theme.Spacing
import com.github.mr3zee.util.UiMessage
import com.github.mr3zee.util.displayName
import com.github.mr3zee.util.formatDuration
import com.github.mr3zee.util.formatTime
import com.github.mr3zee.util.formatTimestamp
import com.github.mr3zee.util.resolve
import com.github.mr3zee.util.typeLabel
import com.github.mr3zee.i18n.packStringResource
import kotlinx.coroutines.delay
import kotlin.time.Clock
import kotlin.time.Instant
import releasewizard.composeapp.generated.resources.*
import kotlin.time.Duration.Companion.milliseconds

private sealed class ActiveConfirmation {
    data object None : ActiveConfirmation()
    data object CancelRelease : ActiveConfirmation()
    data object StopRelease : ActiveConfirmation()
    data class StopBlock(val blockId: BlockId) : ActiveConfirmation()
    data class ApproveBlock(val blockId: BlockId, val gateMessage: String) : ActiveConfirmation()
}

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
    onStopRelease: () -> Unit,
    onResumeRelease: () -> Unit,
    onStopBlock: (BlockId) -> Unit,
    onRerun: () -> Unit,
    onArchive: () -> Unit,
    onApproveBlock: (BlockId) -> Unit,
    onBlockClick: (BlockId) -> Unit,
    onDismissError: () -> Unit = {},
) {
    var selectedBlockId by remember(release?.id) { mutableStateOf<BlockId?>(null) }
    var activeConfirmation by remember { mutableStateOf<ActiveConfirmation>(ActiveConfirmation.None) }

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

    val isDialogOpen = activeConfirmation != ActiveConfirmation.None
    val shortcutActions = remember(isDialogOpen) {
        ShortcutActions(hasDialogOpen = isDialogOpen)
    }
    ProvideShortcutActions(shortcutActions) {

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        val titleText = if (release != null) {
                            packStringResource(Res.string.releases_release_title, release.id.value.take(8))
                        } else {
                            packStringResource(Res.string.releases_detail_title)
                        }
                        Text(
                            titleText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (release != null) {
                            StatusBadge(release.status)
                        }
                    }
                },
                navigationIcon = {
                    RwButton(onClick = onBack, variant = RwButtonVariant.Ghost, modifier = Modifier.testTag("back_button")) {
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
                        RwTooltip(tooltip = packStringResource(Res.string.releases_stop_tooltip)) {
                            RwButton(
                                onClick = { activeConfirmation = ActiveConfirmation.StopRelease },
                                modifier = Modifier.testTag("stop_release_button"),
                                variant = RwButtonVariant.Secondary,
                            ) {
                                Text(packStringResource(Res.string.releases_stop))
                            }
                        }
                        RwTooltip(tooltip = packStringResource(Res.string.releases_cancel_tooltip)) {
                            RwButton(
                                onClick = { activeConfirmation = ActiveConfirmation.CancelRelease },
                                modifier = Modifier.testTag("cancel_release_button"),
                                variant = RwButtonVariant.Ghost,
                                contentColor = MaterialTheme.colorScheme.error,
                            ) {
                                Text(packStringResource(Res.string.common_cancel))
                            }
                        }
                    }
                    if (release?.status == ReleaseStatus.STOPPED) {
                        RwButton(
                            onClick = onResumeRelease,
                            modifier = Modifier.testTag("resume_release_button"),
                            variant = RwButtonVariant.Primary,
                        ) {
                            Text(packStringResource(Res.string.releases_resume))
                        }
                        RwTooltip(tooltip = packStringResource(Res.string.releases_cancel_tooltip)) {
                            RwButton(
                                onClick = { activeConfirmation = ActiveConfirmation.CancelRelease },
                                modifier = Modifier.testTag("cancel_release_button"),
                                variant = RwButtonVariant.Ghost,
                                contentColor = MaterialTheme.colorScheme.error,
                            ) {
                                Text(packStringResource(Res.string.common_cancel))
                            }
                        }
                    }
                    if (release?.status == ReleaseStatus.PENDING) {
                        RwTooltip(tooltip = packStringResource(Res.string.releases_cancel_tooltip)) {
                            RwButton(
                                onClick = { activeConfirmation = ActiveConfirmation.CancelRelease },
                                modifier = Modifier.testTag("cancel_release_button"),
                                variant = RwButtonVariant.Ghost,
                                contentColor = MaterialTheme.colorScheme.error,
                            ) {
                                Text(packStringResource(Res.string.common_cancel))
                            }
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
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(Spacing.md))
                    Text(
                        packStringResource(Res.string.releases_loading),
                        style = AppTypography.body,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            // Cancel release inline confirmation
            RwInlineConfirmation(
                visible = activeConfirmation is ActiveConfirmation.CancelRelease,
                message = packStringResource(Res.string.releases_cancel_body),
                confirmLabel = packStringResource(Res.string.releases_cancel_confirm),
                onConfirm = {
                    activeConfirmation = ActiveConfirmation.None
                    onCancel()
                },
                onDismiss = { activeConfirmation = ActiveConfirmation.None },
                isDestructive = true,
                testTag = "confirm_cancel_release",
                modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.xs),
            )

            // Stop release inline confirmation
            RwInlineConfirmation(
                visible = activeConfirmation is ActiveConfirmation.StopRelease,
                message = packStringResource(Res.string.releases_stop_body),
                confirmLabel = packStringResource(Res.string.releases_stop_confirm),
                onConfirm = {
                    activeConfirmation = ActiveConfirmation.None
                    onStopRelease()
                },
                onDismiss = { activeConfirmation = ActiveConfirmation.None },
                isDestructive = true,
                testTag = "confirm_stop_release",
                modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.xs),
            )

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
                    selectedBlockId = selectedBlockId,
                    onDeselect = { selectedBlockId = null },
                )
            }

            // Block detail panel
            selectedBlockId?.let { blockId ->
                val execution = blockExecutions.find { it.blockId == blockId }
                val block = release.dagSnapshot.blocks.find { it.id == blockId }
                if (block != null) {
                    if (execution != null) {
                        val defaultApproveMessage = packStringResource(Res.string.releases_approve_default_message)
                        BlockDetailPanel(
                            block = block,
                            execution = execution,
                            releaseStatus = release.status,
                            activeConfirmation = activeConfirmation,
                            onApprove = {
                                val gateMessage = execution.gateMessage ?: defaultApproveMessage
                                activeConfirmation = ActiveConfirmation.ApproveBlock(blockId, gateMessage)
                            },
                            onStopBlock = { activeConfirmation = ActiveConfirmation.StopBlock(blockId) },
                            onConfirmApprove = {
                                val approveBlockId = (activeConfirmation as? ActiveConfirmation.ApproveBlock)?.blockId
                                activeConfirmation = ActiveConfirmation.None
                                if (approveBlockId != null) onApproveBlock(approveBlockId)
                            },
                            onConfirmStopBlock = {
                                val stopBlockId = (activeConfirmation as? ActiveConfirmation.StopBlock)?.blockId
                                activeConfirmation = ActiveConfirmation.None
                                if (stopBlockId != null) onStopBlock(stopBlockId)
                            },
                            onDismissConfirmation = { activeConfirmation = ActiveConfirmation.None },
                            onDismiss = { selectedBlockId = null },
                        )
                    } else {
                        // Block has no execution entry yet — show minimal waiting panel
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
                                    RwButton(onClick = { selectedBlockId = null }, variant = RwButtonVariant.Ghost) {
                                        Text(packStringResource(Res.string.common_close))
                                    }
                                }
                                Spacer(modifier = Modifier.height(Spacing.sm))
                                Text(
                                    text = packStringResource(Res.string.releases_block_status, packStringResource(Res.string.block_status_waiting)),
                                    style = AppTypography.body,
                                    modifier = Modifier.testTag("block_status_text"),
                                )
                                Spacer(modifier = Modifier.height(Spacing.xs))
                                Text(
                                    text = packStringResource(Res.string.releases_block_waiting_info),
                                    style = AppTypography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.testTag("block_waiting_info"),
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    } // ProvideShortcutActions
}

@Composable
private fun BlockDetailPanel(
    block: Block,
    execution: BlockExecution,
    releaseStatus: ReleaseStatus = ReleaseStatus.RUNNING,
    activeConfirmation: ActiveConfirmation,
    onApprove: () -> Unit,
    onStopBlock: () -> Unit = {},
    onConfirmApprove: () -> Unit,
    onConfirmStopBlock: () -> Unit,
    onDismissConfirmation: () -> Unit,
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

            // Stop block inline confirmation
            RwInlineConfirmation(
                visible = (activeConfirmation as? ActiveConfirmation.StopBlock)?.blockId == execution.blockId,
                message = packStringResource(Res.string.releases_stop_block_body),
                confirmLabel = packStringResource(Res.string.releases_stop_block_confirm),
                onConfirm = onConfirmStopBlock,
                onDismiss = onDismissConfirmation,
                isDestructive = true,
                testTag = "confirm_stop_block",
                modifier = Modifier.padding(vertical = Spacing.xs),
            )

            // Approve block inline confirmation
            val approveConfirmation = activeConfirmation as? ActiveConfirmation.ApproveBlock
            RwInlineConfirmation(
                visible = approveConfirmation != null && approveConfirmation.blockId == execution.blockId,
                message = approveConfirmation?.gateMessage ?: "",
                confirmLabel = packStringResource(Res.string.common_approve),
                onConfirm = onConfirmApprove,
                onDismiss = onDismissConfirmation,
                isDestructive = false,
                testTag = "confirm_approve_block",
                modifier = Modifier.padding(vertical = Spacing.xs),
            )

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
                    // Running block — live ticker (4J: initialize with computed value)
                    var elapsed by remember { mutableStateOf(formatDuration(Clock.System.now() - startedAt)) }
                    LaunchedEffect(startedAt) {
                        while (true) {
                            val now = Clock.System.now()
                            elapsed = formatDuration(now - startedAt)
                            delay(1000.milliseconds)
                        }
                    }
                    Text(
                        text = packStringResource(Res.string.releases_elapsed, elapsed),
                        style = AppTypography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag("block_duration_text"),
                    )
                } else if (execution.status == BlockStatus.STOPPED) {
                    // Stopped block — no stoppedAt field in model, show static label
                    Text(
                        text = packStringResource(Res.string.releases_stopped_label),
                        style = AppTypography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag("block_duration_text"),
                    )
                } else if (execution.status == BlockStatus.WAITING_FOR_INPUT) {
                    // Waiting for input — live ticker showing wait time
                    var elapsed by remember { mutableStateOf(formatDuration(Clock.System.now() - startedAt)) }
                    LaunchedEffect(startedAt) {
                        while (true) {
                            elapsed = formatDuration(Clock.System.now() - startedAt)
                            delay(1000.milliseconds)
                        }
                    }
                    Text(
                        text = packStringResource(Res.string.releases_waiting_elapsed, elapsed),
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

            // Stopped block context message
            if (execution.status == BlockStatus.STOPPED) {
                Spacer(modifier = Modifier.height(Spacing.sm))
                Text(
                    text = packStringResource(Res.string.releases_stopped_context),
                    style = AppTypography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("stopped_context_text"),
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
                        modifier = Modifier.padding(start = Spacing.md),
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
                                val formatted = formatTimestamp(instant)
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
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    RwButton(
                        onClick = onApprove,
                        modifier = Modifier.testTag("approve_block_button"),
                        variant = RwButtonVariant.Primary,
                    ) {
                        Text(packStringResource(Res.string.common_approve))
                    }
                    if (releaseStatus == ReleaseStatus.RUNNING) {
                        RwButton(
                            onClick = onStopBlock,
                            modifier = Modifier.testTag("stop_block_button"),
                            variant = RwButtonVariant.Ghost,
                            contentColor = MaterialTheme.colorScheme.error,
                        ) {
                            Text(packStringResource(Res.string.releases_stop))
                        }
                    }
                }
            }

            // Stop button for running blocks (without gate)
            if (execution.status == BlockStatus.RUNNING && releaseStatus == ReleaseStatus.RUNNING) {
                Spacer(modifier = Modifier.height(Spacing.sm))
                RwButton(
                    onClick = onStopBlock,
                    modifier = Modifier.testTag("stop_block_button"),
                    variant = RwButtonVariant.Ghost,
                    contentColor = MaterialTheme.colorScheme.error,
                ) {
                    Text(packStringResource(Res.string.releases_stop))
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
                    Text(
                        text = formatTime(webhookStatus.receivedAt),
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

