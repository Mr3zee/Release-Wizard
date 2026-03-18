package com.github.mr3zee.automation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.github.mr3zee.api.TriggerResponse
import com.github.mr3zee.components.RwButton
import com.github.mr3zee.components.RwButtonVariant
import com.github.mr3zee.i18n.packStringResource
import com.github.mr3zee.model.MavenTrigger
import com.github.mr3zee.model.Schedule
import com.github.mr3zee.theme.AppTypography
import com.github.mr3zee.util.resolve
import kotlinx.coroutines.launch
import releasewizard.composeapp.generated.resources.*
import kotlin.time.Clock

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectAutomationScreen(
    viewModel: ProjectAutomationViewModel,
    onBack: () -> Unit,
) {
    val schedules by viewModel.schedules.collectAsState()
    val webhookTriggers by viewModel.webhookTriggers.collectAsState()
    val mavenTriggers by viewModel.mavenTriggers.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isSaving by viewModel.isSaving.collectAsState()
    val error by viewModel.error.collectAsState()

    // Dialog visibility (local UI state)
    var showCreateSchedule by remember { mutableStateOf(false) }
    var showCreateWebhook by remember { mutableStateOf(false) }
    var showCreateMaven by remember { mutableStateOf(false) }

    // Webhook secret — shown after creation
    var pendingWebhookSecret by remember { mutableStateOf<String?>(null) }

    // Maven created — confirmation dialog (carries artifact info for display)
    var createdMavenTrigger by remember { mutableStateOf<com.github.mr3zee.model.MavenTrigger?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }
    val dismissLabel = packStringResource(Res.string.common_dismiss)

    // Subscribe to all flows before triggering any loads to avoid missing emissions
    LaunchedEffect(viewModel) {
        launch { viewModel.webhookCreated.collect { trigger -> pendingWebhookSecret = trigger.secret } }
        launch { viewModel.mavenTriggerCreated.collect { trigger -> createdMavenTrigger = trigger } }
        viewModel.load()
    }

    // Show errors via snackbar and auto-dismiss from ViewModel
    val resolvedError = error?.resolve()
    LaunchedEffect(resolvedError) {
        val msg = resolvedError ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(
            message = msg,
            actionLabel = dismissLabel,
            duration = SnackbarDuration.Long,
        )
        viewModel.dismissError()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(packStringResource(Res.string.automation_title)) },
                navigationIcon = {
                    RwButton(onClick = onBack, variant = RwButtonVariant.Ghost, modifier = Modifier.testTag("automation_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = packStringResource(Res.string.common_navigate_back))
                        Text(packStringResource(Res.string.common_back))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = Modifier.testTag("project_automation_screen"),
    ) { padding ->
        if (isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // ── Schedules Section ──
                AutomationSection(
                    title = packStringResource(Res.string.automation_schedules_section),
                    addButtonLabel = packStringResource(Res.string.automation_add_schedule),
                    addButtonTestTag = "add_schedule_button",
                    onAdd = { showCreateSchedule = true },
                ) {
                    if (schedules.isEmpty()) {
                        Text(
                            packStringResource(Res.string.automation_empty_schedules),
                            style = AppTypography.body,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 8.dp),
                        )
                    } else {
                        schedules.forEach { schedule ->
                            ScheduleItem(
                                schedule = schedule,
                                onToggle = { viewModel.toggleSchedule(schedule.id, it) },
                                onDelete = { viewModel.deleteSchedule(schedule.id) },
                            )
                        }
                    }
                }

                HorizontalDivider()

                // ── Webhook Triggers Section ──
                AutomationSection(
                    title = packStringResource(Res.string.automation_webhook_section),
                    addButtonLabel = packStringResource(Res.string.automation_add_webhook),
                    addButtonTestTag = "add_webhook_button",
                    onAdd = { showCreateWebhook = true },
                ) {
                    if (webhookTriggers.isEmpty()) {
                        Text(
                            packStringResource(Res.string.automation_empty_webhooks),
                            style = AppTypography.body,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 8.dp),
                        )
                    } else {
                        webhookTriggers.forEach { trigger ->
                            WebhookTriggerItem(
                                trigger = trigger,
                                onToggle = { viewModel.toggleWebhookTrigger(trigger.id, it) },
                                onDelete = { viewModel.deleteWebhookTrigger(trigger.id) },
                            )
                        }
                    }
                }

                HorizontalDivider()

                // ── Maven Publication Triggers Section ──
                AutomationSection(
                    title = packStringResource(Res.string.automation_maven_section),
                    addButtonLabel = packStringResource(Res.string.automation_add_maven),
                    addButtonTestTag = "add_maven_button",
                    onAdd = { showCreateMaven = true },
                ) {
                    if (mavenTriggers.isEmpty()) {
                        Text(
                            packStringResource(Res.string.automation_empty_maven),
                            style = AppTypography.body,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 8.dp),
                        )
                    } else {
                        mavenTriggers.forEach { trigger ->
                            MavenTriggerItem(
                                trigger = trigger,
                                onToggle = { viewModel.toggleMavenTrigger(trigger.id, it) },
                                onDelete = { viewModel.deleteMavenTrigger(trigger.id) },
                            )
                        }
                    }
                }
            }
        }
    }

    // Dialogs
    if (showCreateSchedule) {
        CreateScheduleDialog(
            isSaving = isSaving,
            onConfirm = { request ->
                viewModel.createSchedule(request)
                showCreateSchedule = false
            },
            onDismiss = { showCreateSchedule = false },
        )
    }

    if (showCreateWebhook) {
        CreateWebhookTriggerDialog(
            isSaving = isSaving,
            onConfirm = { request ->
                viewModel.createWebhookTrigger(request)
                showCreateWebhook = false
            },
            onDismiss = { showCreateWebhook = false },
        )
    }

    if (showCreateMaven) {
        CreateMavenTriggerDialog(
            isSaving = isSaving,
            onConfirm = { request ->
                viewModel.createMavenTrigger(request)
                showCreateMaven = false
            },
            onDismiss = { showCreateMaven = false },
        )
    }

    val secret = pendingWebhookSecret
    if (secret != null) {
        WebhookSecretDialog(
            secret = secret,
            onDismiss = { pendingWebhookSecret = null },
        )
    }

    val createdTrigger = createdMavenTrigger
    if (createdTrigger != null) {
        MavenTriggerCreatedDialog(
            trigger = createdTrigger,
            onDismiss = { createdMavenTrigger = null },
        )
    }
}

@Composable
private fun AutomationSection(
    title: String,
    addButtonLabel: String,
    addButtonTestTag: String,
    onAdd: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(title, style = AppTypography.subheading)
            RwButton(
                onClick = onAdd,
                variant = RwButtonVariant.Secondary,
                modifier = Modifier.testTag(addButtonTestTag),
            ) {
                Icon(Icons.Default.Add, contentDescription = addButtonLabel, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(addButtonLabel)
            }
        }
        content()
    }
}

@Composable
private fun ScheduleItem(
    schedule: Schedule,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit,
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(schedule.cronExpression, style = AppTypography.body)
            }
            Switch(
                checked = schedule.enabled,
                onCheckedChange = onToggle,
                modifier = Modifier.testTag("schedule_toggle_${schedule.id}"),
            )
            IconButton(
                onClick = { showDeleteConfirm = true },
                modifier = Modifier.testTag("schedule_delete_${schedule.id}"),
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = packStringResource(Res.string.common_delete),
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }

    if (showDeleteConfirm) {
        DeleteConfirmDialog(
            title = packStringResource(Res.string.automation_delete_confirm_title),
            message = "\"${schedule.cronExpression}\" — ${packStringResource(Res.string.automation_delete_confirm_message)}",
            onConfirm = { showDeleteConfirm = false; onDelete() },
            onDismiss = { showDeleteConfirm = false },
        )
    }
}

@Composable
private fun WebhookTriggerItem(
    trigger: TriggerResponse,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit,
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(packStringResource(Res.string.automation_webhook_item_label), style = AppTypography.body)
                Text(
                    trigger.webhookUrl,
                    style = AppTypography.caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            Switch(
                checked = trigger.enabled,
                onCheckedChange = onToggle,
                modifier = Modifier.testTag("webhook_toggle_${trigger.id}"),
            )
            IconButton(
                onClick = { showDeleteConfirm = true },
                modifier = Modifier.testTag("webhook_delete_${trigger.id}"),
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = packStringResource(Res.string.common_delete),
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }

    if (showDeleteConfirm) {
        DeleteConfirmDialog(
            title = packStringResource(Res.string.automation_delete_confirm_title),
            message = packStringResource(Res.string.automation_delete_confirm_message),
            onConfirm = { showDeleteConfirm = false; onDelete() },
            onDismiss = { showDeleteConfirm = false },
        )
    }
}

@Composable
private fun MavenTriggerItem(
    trigger: MavenTrigger,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit,
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "${trigger.groupId}:${trigger.artifactId}",
                    style = AppTypography.body,
                )
                Text(
                    trigger.repoUrl,
                    style = AppTypography.caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
                val checkedAt = trigger.lastCheckedAt
                val lastCheckedText = if (checkedAt != null) {
                    packStringResource(Res.string.maven_last_checked_label) + ": " +
                        formatRelativeTime(checkedAt)
                } else {
                    packStringResource(Res.string.maven_last_checked_never)
                }
                Text(
                    lastCheckedText,
                    style = AppTypography.caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = trigger.enabled,
                onCheckedChange = onToggle,
                modifier = Modifier.testTag("maven_toggle_${trigger.id}"),
            )
            IconButton(
                onClick = { showDeleteConfirm = true },
                modifier = Modifier.testTag("maven_delete_${trigger.id}"),
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = packStringResource(Res.string.common_delete),
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }

    if (showDeleteConfirm) {
        DeleteConfirmDialog(
            title = packStringResource(Res.string.automation_delete_confirm_title),
            message = "${trigger.groupId}:${trigger.artifactId} — ${packStringResource(Res.string.automation_delete_confirm_message)}",
            onConfirm = { showDeleteConfirm = false; onDelete() },
            onDismiss = { showDeleteConfirm = false },
        )
    }
}

@Composable
private fun DeleteConfirmDialog(
    title: String,
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            RwButton(onClick = onConfirm, variant = RwButtonVariant.Danger, modifier = Modifier.testTag("delete_confirm_button")) {
                Text(packStringResource(Res.string.common_delete))
            }
        },
        dismissButton = {
            RwButton(onClick = onDismiss, variant = RwButtonVariant.Ghost, modifier = Modifier.testTag("delete_cancel_button")) {
                Text(packStringResource(Res.string.common_cancel))
            }
        },
    )
}

private fun formatRelativeTime(instant: kotlin.time.Instant): String {
    val now = Clock.System.now()
    val diff = now - instant
    return when {
        diff.inWholeMinutes < 1 -> "just now"
        diff.inWholeMinutes < 60 -> "${diff.inWholeMinutes} min ago"
        diff.inWholeHours < 24 -> "${diff.inWholeHours} hr ago"
        else -> "${diff.inWholeDays} days ago"
    }
}
