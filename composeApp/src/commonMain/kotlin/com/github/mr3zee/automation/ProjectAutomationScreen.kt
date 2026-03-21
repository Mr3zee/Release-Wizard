package com.github.mr3zee.automation

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.github.mr3zee.api.CreateMavenTriggerRequest
import com.github.mr3zee.api.CreateScheduleRequest
import com.github.mr3zee.api.CreateTriggerRequest
import com.github.mr3zee.api.TriggerResponse
import com.github.mr3zee.components.RwButton
import com.github.mr3zee.components.RwButtonVariant
import com.github.mr3zee.components.RwDropdownMenuItem
import com.github.mr3zee.components.RwCard
import com.github.mr3zee.components.RwCheckbox
import com.github.mr3zee.components.RwIconButton
import com.github.mr3zee.components.RwInlineConfirmation
import com.github.mr3zee.components.RwSwitch
import com.github.mr3zee.components.RwInlineForm
import com.github.mr3zee.components.RwTextField
import com.github.mr3zee.components.RwTooltip
import com.github.mr3zee.keyboard.ProvideShortcutActions
import com.github.mr3zee.keyboard.ShortcutActions
import com.github.mr3zee.i18n.packPluralStringResource
import com.github.mr3zee.i18n.packStringResource
import com.github.mr3zee.model.MavenTrigger
import com.github.mr3zee.model.Schedule
import com.github.mr3zee.theme.AppTypography
import com.github.mr3zee.theme.Spacing
import com.github.mr3zee.util.copyToClipboard
import com.github.mr3zee.util.resolve
import kotlinx.coroutines.launch
import releasewizard.composeapp.generated.resources.*
import kotlin.time.Clock

private fun isValidCron(expression: String): Boolean {
    val parts = expression.trim().split("\\s+".toRegex())
    if (parts.size != 5) return false

    fun validField(field: String, min: Int, max: Int): Boolean {
        return field.split(",").all { item ->
            if (item.isEmpty()) return@all false
            val slashParts = item.split("/")
            if (slashParts.size > 2) return@all false
            val step = slashParts.getOrNull(1)
            if (step != null && ((step.toIntOrNull() ?: return@all false) < 1)) return@all false
            val base = slashParts[0]
            when {
                base == "*" -> true
                "-" in base -> {
                    val rangeParts = base.split("-")
                    if (rangeParts.size != 2) return@all false
                    val from = rangeParts[0].toIntOrNull() ?: return@all false
                    val to = rangeParts[1].toIntOrNull() ?: return@all false
                    from in min..max && to in min..max && from <= to
                }
                else -> {
                    val num = base.toIntOrNull() ?: return@all false
                    num in min..max
                }
            }
        }
    }

    return validField(parts[0], 0, 59) &&  // minute
        validField(parts[1], 0, 23) &&     // hour
        validField(parts[2], 1, 31) &&     // day of month
        validField(parts[3], 1, 12) &&     // month
        validField(parts[4], 0, 6)         // day of week
}

@Composable
private fun cronDescription(expression: String): String? = when (expression.trim()) {
    "0 9 * * *"   -> packStringResource(Res.string.schedule_preset_daily)
    "0 9 * * 1-5" -> packStringResource(Res.string.schedule_preset_weekdays)
    "0 12 * * 1"  -> packStringResource(Res.string.schedule_preset_monday)
    else -> null
}

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

    // Inline form visibility (local UI state)
    var showCreateSchedule by remember { mutableStateOf(false) }
    var showCreateMaven by remember { mutableStateOf(false) }

    // Delete confirmation state — lifted to screen level
    var scheduleToDelete by remember { mutableStateOf<String?>(null) }
    var webhookToDelete by remember { mutableStateOf<String?>(null) }
    var mavenToDelete by remember { mutableStateOf<String?>(null) }

    // Webhook secret — shown persistently after creation until dismissed
    var pendingWebhookSecret by remember { mutableStateOf<String?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }
    val dismissLabel = packStringResource(Res.string.common_dismiss)
    val mavenCreatedLabel = packStringResource(Res.string.maven_created_title)

    DisposableEffect(Unit) {
        onDispose {
            pendingWebhookSecret = null
        }
    }

    // Subscribe to all flows before triggering any loads to avoid missing emissions
    LaunchedEffect(viewModel) {
        launch { viewModel.webhookCreated.collect { trigger -> pendingWebhookSecret = trigger.secret } }
        launch {
            viewModel.mavenTriggerCreated.collect { trigger ->
                snackbarHostState.showSnackbar(
                    message = "$mavenCreatedLabel: ${trigger.groupId}:${trigger.artifactId}",
                    actionLabel = dismissLabel,
                    duration = SnackbarDuration.Long,
                )
            }
        }
        viewModel.load()
    }

    // Auto-copy webhook secret to clipboard when it appears
    LaunchedEffect(pendingWebhookSecret) {
        val secret = pendingWebhookSecret ?: return@LaunchedEffect
        copyToClipboard(secret)
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

    val shortcutActions = remember { ShortcutActions(onRefresh = { viewModel.load() }) }
    ProvideShortcutActions(shortcutActions) {

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(packStringResource(Res.string.automation_title))
                        Text(
                            packStringResource(Res.string.automation_description),
                            style = AppTypography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
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
                val loadingDesc = packStringResource(Res.string.loading_automation)
                CircularProgressIndicator(modifier = Modifier.semantics { contentDescription = loadingDesc })
            }
        } else {
            val scrollState = rememberScrollState()
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.TopCenter,
            ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 720.dp)
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
                    .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.xl),
            ) {
                // ── Schedules Section ──
                AutomationSection(
                    title = packStringResource(Res.string.automation_schedules_section),
                    addButtonLabel = packStringResource(Res.string.automation_add_schedule),
                    addButtonTestTag = "add_schedule_button",
                    onAdd = { showCreateSchedule = true },
                    leadingIcon = { Icon(Icons.Default.Schedule, contentDescription = packStringResource(Res.string.automation_schedules_section), modifier = Modifier.size(20.dp)) },
                ) {
                    CreateScheduleInlineForm(
                        visible = showCreateSchedule,
                        isSaving = isSaving,
                        onConfirm = { request ->
                            viewModel.createSchedule(request)
                            showCreateSchedule = false
                        },
                        onDismiss = { showCreateSchedule = false },
                    )

                    if (schedules.isEmpty()) {
                        Column {
                            Text(
                                packStringResource(Res.string.automation_empty_schedules),
                                style = AppTypography.body,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                packStringResource(Res.string.automation_empty_schedules_hint),
                                style = AppTypography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = Spacing.xs),
                            )
                        }
                    } else {
                        schedules.forEach { schedule ->
                            ScheduleItem(
                                schedule = schedule,
                                onToggle = { viewModel.toggleSchedule(schedule.id, it) },
                                onRequestDelete = { scheduleToDelete = schedule.id },
                            )
                            RwInlineConfirmation(
                                visible = scheduleToDelete == schedule.id,
                                message = "\"${schedule.cronExpression}\" — ${packStringResource(Res.string.automation_delete_confirm_message)}",
                                confirmLabel = packStringResource(Res.string.common_delete),
                                onConfirm = {
                                    val id = scheduleToDelete
                                    scheduleToDelete = null
                                    if (id != null) viewModel.deleteSchedule(id)
                                },
                                onDismiss = { scheduleToDelete = null },
                                isDestructive = true,
                                testTag = "confirm_delete_schedule_${schedule.id}",
                            )
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(horizontal = Spacing.xs))

                // ── Webhook Triggers Section ──
                AutomationSection(
                    title = packStringResource(Res.string.automation_webhook_section),
                    addButtonLabel = packStringResource(Res.string.automation_add_webhook),
                    addButtonTestTag = "add_webhook_button",
                    onAdd = { if (!isSaving) viewModel.createWebhookTrigger(CreateTriggerRequest()) },
                    leadingIcon = { Icon(Icons.Default.Link, contentDescription = packStringResource(Res.string.automation_webhook_section), modifier = Modifier.size(20.dp)) },
                    addButtonEnabled = !isSaving,
                ) {
                    // Persistent webhook secret card — shown until user clicks dismiss
                    val secret = pendingWebhookSecret
                    if (secret != null) {
                        WebhookSecretInlineCard(
                            secret = secret,
                            onDismiss = { pendingWebhookSecret = null },
                        )
                    }

                    if (webhookTriggers.isEmpty()) {
                        Column {
                            Text(
                                packStringResource(Res.string.automation_empty_webhooks),
                                style = AppTypography.body,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                packStringResource(Res.string.automation_empty_webhooks_hint),
                                style = AppTypography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = Spacing.xs),
                            )
                        }
                    } else {
                        webhookTriggers.forEach { trigger ->
                            WebhookTriggerItem(
                                trigger = trigger,
                                onToggle = { viewModel.toggleWebhookTrigger(trigger.id, it) },
                                onRequestDelete = { webhookToDelete = trigger.id },
                            )
                            RwInlineConfirmation(
                                visible = webhookToDelete == trigger.id,
                                message = packStringResource(Res.string.automation_delete_confirm_message),
                                confirmLabel = packStringResource(Res.string.common_delete),
                                onConfirm = {
                                    val id = webhookToDelete
                                    webhookToDelete = null
                                    if (id != null) viewModel.deleteWebhookTrigger(id)
                                },
                                onDismiss = { webhookToDelete = null },
                                isDestructive = true,
                                testTag = "confirm_delete_webhook_${trigger.id}",
                            )
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(horizontal = Spacing.xs))

                // ── Maven Publication Triggers Section ──
                AutomationSection(
                    title = packStringResource(Res.string.automation_maven_section),
                    addButtonLabel = packStringResource(Res.string.automation_add_maven),
                    addButtonTestTag = "add_maven_button",
                    onAdd = { showCreateMaven = true },
                    leadingIcon = { Icon(Icons.Default.Inventory2, contentDescription = packStringResource(Res.string.automation_maven_section), modifier = Modifier.size(20.dp)) },
                ) {
                    CreateMavenTriggerInlineForm(
                        visible = showCreateMaven,
                        isSaving = isSaving,
                        onConfirm = { request ->
                            viewModel.createMavenTrigger(request)
                            showCreateMaven = false
                        },
                        onDismiss = { showCreateMaven = false },
                    )

                    if (mavenTriggers.isEmpty()) {
                        Column {
                            Text(
                                packStringResource(Res.string.automation_empty_maven),
                                style = AppTypography.body,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                packStringResource(Res.string.automation_empty_maven_hint),
                                style = AppTypography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = Spacing.xs),
                            )
                        }
                    } else {
                        mavenTriggers.forEach { trigger ->
                            MavenTriggerItem(
                                trigger = trigger,
                                onToggle = { viewModel.toggleMavenTrigger(trigger.id, it) },
                                onRequestDelete = { mavenToDelete = trigger.id },
                            )
                            RwInlineConfirmation(
                                visible = mavenToDelete == trigger.id,
                                message = "${trigger.groupId}:${trigger.artifactId} — ${packStringResource(Res.string.automation_delete_confirm_message)}",
                                confirmLabel = packStringResource(Res.string.common_delete),
                                onConfirm = {
                                    val id = mavenToDelete
                                    mavenToDelete = null
                                    if (id != null) viewModel.deleteMavenTrigger(id)
                                },
                                onDismiss = { mavenToDelete = null },
                                isDestructive = true,
                                testTag = "confirm_delete_maven_${trigger.id}",
                            )
                        }
                    }
                }
            }
            VerticalScrollbar(
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                adapter = rememberScrollbarAdapter(scrollState),
            )
            } // Box
        }
    }

    } // ProvideShortcutActions
}

@Composable
private fun AutomationSection(
    title: String,
    addButtonLabel: String,
    addButtonTestTag: String,
    onAdd: () -> Unit,
    leadingIcon: @Composable (() -> Unit)? = null,
    addButtonEnabled: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (leadingIcon != null) {
                    leadingIcon()
                    Spacer(Modifier.width(Spacing.sm))
                }
                Text(title, style = AppTypography.heading)
            }
            RwButton(
                onClick = onAdd,
                variant = RwButtonVariant.Secondary,
                enabled = addButtonEnabled,
                modifier = Modifier.testTag(addButtonTestTag),
            ) {
                Icon(Icons.Default.Add, contentDescription = addButtonLabel, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(Spacing.xs))
                Text(addButtonLabel)
            }
        }
        content()
    }
}

// ── Webhook Secret Inline Card ──

@Composable
private fun WebhookSecretInlineCard(
    secret: String,
    onDismiss: () -> Unit,
) {
    RwCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("webhook_secret_card"),
        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Text(
                packStringResource(Res.string.webhook_secret_dialog_title),
                style = AppTypography.subheading,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                RwTextField(
                    value = secret,
                    onValueChange = {},
                    readOnly = true,
                    singleLine = true,
                    modifier = Modifier.weight(1f).testTag("webhook_secret_field"),
                )
                RwTooltip(tooltip = packStringResource(Res.string.common_copy_to_clipboard)) {
                    RwIconButton(
                        onClick = { copyToClipboard(secret) },
                        modifier = Modifier.testTag("webhook_secret_copy"),
                    ) {
                        Icon(
                            Icons.Default.ContentCopy,
                            contentDescription = packStringResource(Res.string.common_copy_to_clipboard),
                        )
                    }
                }
            }

            Text(
                text = packStringResource(Res.string.webhook_secret_warning),
                color = MaterialTheme.colorScheme.error,
                style = AppTypography.bodySmall,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                RwButton(
                    onClick = onDismiss,
                    variant = RwButtonVariant.Primary,
                    modifier = Modifier.testTag("webhook_secret_dismiss"),
                ) {
                    Text(packStringResource(Res.string.webhook_secret_saved))
                }
            }
        }
    }
}

// ── Create Schedule Inline Form ──

@Composable
private fun CreateScheduleInlineForm(
    visible: Boolean,
    isSaving: Boolean,
    onConfirm: (CreateScheduleRequest) -> Unit,
    onDismiss: () -> Unit,
) {
    var cronExpression by remember { mutableStateOf("") }
    var presetsExpanded by remember { mutableStateOf(false) }
    var selectedPresetLabel by remember { mutableStateOf("") }

    // Reset form state when it becomes visible
    LaunchedEffect(visible) {
        if (visible) {
            cronExpression = ""
            presetsExpanded = false
            selectedPresetLabel = ""
        }
    }

    val presets = listOf(
        packStringResource(Res.string.schedule_preset_daily) to "0 9 * * *",
        packStringResource(Res.string.schedule_preset_weekdays) to "0 9 * * 1-5",
        packStringResource(Res.string.schedule_preset_monday) to "0 12 * * 1",
    )

    val isCronValid = remember(cronExpression) { isValidCron(cronExpression) }
    val showValidation = cronExpression.isNotBlank()

    val nextRunHint = cronDescription(cronExpression)

    RwInlineForm(
        visible = visible,
        title = packStringResource(Res.string.schedule_create_title),
        onDismiss = onDismiss,
        testTag = "create_schedule_form",
        actions = {
            RwButton(
                onClick = {
                    onConfirm(CreateScheduleRequest(cronExpression = cronExpression.trim()))
                },
                variant = RwButtonVariant.Primary,
                enabled = isCronValid && !isSaving,
                modifier = Modifier.testTag("schedule_create_button"),
            ) {
                Text(packStringResource(Res.string.common_create))
            }
        },
    ) {
        Box {
            RwTextField(
                value = selectedPresetLabel.ifBlank { packStringResource(Res.string.schedule_preset_label) },
                onValueChange = {},
                readOnly = true,
                label = packStringResource(Res.string.schedule_preset_label),
                trailingIcon = {
                    Icon(
                        Icons.Default.ArrowDropDown,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("schedule_preset_selector"),
            )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .pointerHoverIcon(PointerIcon.Hand)
                    .testTag("schedule_preset_selector_click")
                    .clickable { presetsExpanded = !presetsExpanded },
            )
            DropdownMenu(
                expanded = presetsExpanded,
                onDismissRequest = { presetsExpanded = false },
            ) {
                presets.forEach { (label, cron) ->
                    RwDropdownMenuItem(
                        text = { Text(label) },
                        onClick = {
                            cronExpression = cron
                            selectedPresetLabel = label
                            presetsExpanded = false
                        },
                    )
                }
            }
        }

        // Cron expression field
        RwTextField(
            value = cronExpression,
            onValueChange = { cronExpression = it; selectedPresetLabel = "" },
            label = packStringResource(Res.string.schedule_cron_label),
            placeholder = packStringResource(Res.string.schedule_cron_hint),
            supportingText = {
                when {
                    nextRunHint != null -> Text(
                        "${packStringResource(Res.string.schedule_next_run_label)}: $nextRunHint",
                        color = MaterialTheme.colorScheme.primary,
                    )
                    showValidation && isCronValid -> Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(modifier = Modifier.width(Spacing.xxs))
                        Text(
                            packStringResource(Res.string.schedule_cron_valid),
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    showValidation && !isCronValid -> Text(
                        packStringResource(Res.string.schedule_cron_invalid),
                        color = MaterialTheme.colorScheme.error,
                    )
                    else -> {}
                }
            },
            isError = showValidation && !isCronValid,
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("schedule_cron_input"),
        )
    }
}

// ── Create Maven Trigger Inline Form ──

@Composable
private fun CreateMavenTriggerInlineForm(
    visible: Boolean,
    isSaving: Boolean,
    onConfirm: (CreateMavenTriggerRequest) -> Unit,
    onDismiss: () -> Unit,
) {
    var repoUrl by remember { mutableStateOf("") }
    var groupId by remember { mutableStateOf("") }
    var artifactId by remember { mutableStateOf("") }
    var parameterKey by remember { mutableStateOf("version") }
    var includeSnapshots by remember { mutableStateOf(false) }

    // Reset form state when it becomes visible
    LaunchedEffect(visible) {
        if (visible) {
            repoUrl = ""
            groupId = ""
            artifactId = ""
            parameterKey = "version"
            includeSnapshots = false
        }
    }

    val isValid = repoUrl.isNotBlank() &&
        (repoUrl.startsWith("http://") || repoUrl.startsWith("https://")) &&
        groupId.isNotBlank() &&
        artifactId.isNotBlank() &&
        parameterKey.isNotBlank()

    RwInlineForm(
        visible = visible,
        title = packStringResource(Res.string.maven_create_title),
        onDismiss = onDismiss,
        testTag = "create_maven_form",
        actions = {
            RwButton(
                onClick = {
                    onConfirm(
                        CreateMavenTriggerRequest(
                            repoUrl = repoUrl.trim(),
                            groupId = groupId.trim(),
                            artifactId = artifactId.trim(),
                            parameterKey = parameterKey.trim(),
                            includeSnapshots = includeSnapshots,
                        )
                    )
                },
                variant = RwButtonVariant.Primary,
                enabled = isValid && !isSaving,
                modifier = Modifier.testTag("maven_create_button"),
            ) {
                Text(if (isSaving) packStringResource(Res.string.common_saving) else packStringResource(Res.string.common_create))
            }
        },
    ) {
        val repoUrlInvalid = repoUrl.isNotBlank() &&
            !repoUrl.startsWith("http://") && !repoUrl.startsWith("https://")
        RwTextField(
            value = repoUrl,
            onValueChange = { repoUrl = it },
            label = packStringResource(Res.string.maven_repo_url_label),
            placeholder = packStringResource(Res.string.maven_repo_url_hint),
            isError = repoUrlInvalid,
            supportingText = if (repoUrlInvalid) {
                { Text(packStringResource(Res.string.maven_url_validation_error)) }
            } else null,
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("maven_repo_url_field"),
        )
        RwTextField(
            value = groupId,
            onValueChange = { groupId = it },
            label = packStringResource(Res.string.maven_group_id_label),
            placeholder = packStringResource(Res.string.maven_group_id_hint),
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("maven_group_id_field"),
        )
        RwTextField(
            value = artifactId,
            onValueChange = { artifactId = it },
            label = packStringResource(Res.string.maven_artifact_id_label),
            placeholder = packStringResource(Res.string.maven_artifact_id_hint),
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("maven_artifact_id_field"),
        )
        RwTextField(
            value = parameterKey,
            onValueChange = { parameterKey = it },
            label = packStringResource(Res.string.maven_parameter_key_label),
            placeholder = packStringResource(Res.string.maven_parameter_key_hint),
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("maven_parameter_key_field"),
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            modifier = Modifier
                .toggleable(
                    value = includeSnapshots,
                    role = Role.Checkbox,
                    onValueChange = { includeSnapshots = it },
                )
                .testTag("maven_include_snapshots"),
        ) {
            RwCheckbox(checked = includeSnapshots, onCheckedChange = null)
            Text(packStringResource(Res.string.maven_include_snapshots_label))
        }
    }
}

// ── Item composables ──

@Composable
private fun ScheduleItem(
    schedule: Schedule,
    onToggle: (Boolean) -> Unit,
    onRequestDelete: () -> Unit,
) {
    RwCard(modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.xxs)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(schedule.cronExpression, style = AppTypography.code)
                val description = cronDescription(schedule.cronExpression)
                if (description != null) {
                    Text(
                        description,
                        style = AppTypography.caption,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag("schedule_description_${schedule.id}"),
                    )
                }
            }
            RwSwitch(
                checked = schedule.enabled,
                onCheckedChange = onToggle,
                modifier = Modifier.testTag("schedule_toggle_${schedule.id}"),
            )
            RwTooltip(tooltip = packStringResource(Res.string.common_delete)) {
                RwIconButton(
                    onClick = onRequestDelete,
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
    }
}

@Composable
private fun WebhookTriggerItem(
    trigger: TriggerResponse,
    onToggle: (Boolean) -> Unit,
    onRequestDelete: () -> Unit,
) {
    RwCard(modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.xxs)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(packStringResource(Res.string.automation_webhook_item_label), style = AppTypography.body)
                Text(
                    trigger.webhookUrl,
                    style = AppTypography.code,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            RwSwitch(
                checked = trigger.enabled,
                onCheckedChange = onToggle,
                modifier = Modifier.testTag("webhook_toggle_${trigger.id}"),
            )
            RwTooltip(tooltip = packStringResource(Res.string.common_delete)) {
                RwIconButton(
                    onClick = onRequestDelete,
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
    }
}

@Composable
private fun MavenTriggerItem(
    trigger: MavenTrigger,
    onToggle: (Boolean) -> Unit,
    onRequestDelete: () -> Unit,
) {
    RwCard(modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.xxs)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(Spacing.md),
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
            RwSwitch(
                checked = trigger.enabled,
                onCheckedChange = onToggle,
                modifier = Modifier.testTag("maven_toggle_${trigger.id}"),
            )
            RwTooltip(tooltip = packStringResource(Res.string.common_delete)) {
                RwIconButton(
                    onClick = onRequestDelete,
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
    }
}

@Composable
private fun formatRelativeTime(instant: kotlin.time.Instant): String {
    val now = Clock.System.now()
    val diff = now - instant
    return when {
        diff.inWholeMinutes < 1 -> packStringResource(Res.string.automation_checked_just_now)
        diff.inWholeMinutes < 60 -> {
            val minutes = diff.inWholeMinutes.toInt()
            packPluralStringResource(Res.plurals.automation_checked_minutes_ago, minutes, minutes)
        }
        diff.inWholeHours < 24 -> {
            val hours = diff.inWholeHours.toInt()
            packPluralStringResource(Res.plurals.automation_checked_hours_ago, hours, hours)
        }
        else -> {
            val days = diff.inWholeDays.toInt()
            packPluralStringResource(Res.plurals.automation_checked_days_ago, days, days)
        }
    }
}
