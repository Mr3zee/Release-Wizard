package com.github.mr3zee.connections

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.github.mr3zee.components.RwButton
import com.github.mr3zee.components.RwButtonVariant
import com.github.mr3zee.components.RwIconButton
import com.github.mr3zee.components.RwTextField
import com.github.mr3zee.model.ConnectionConfig
import com.github.mr3zee.model.ConnectionId
import com.github.mr3zee.model.ConnectionType
import com.github.mr3zee.keyboard.LocalShortcutActions
import com.github.mr3zee.keyboard.ShortcutActions
import com.github.mr3zee.theme.AppTypography
import com.github.mr3zee.theme.Spacing
import com.github.mr3zee.util.displayName
import com.github.mr3zee.util.resolve
import com.github.mr3zee.i18n.packStringResource
import releasewizard.composeapp.generated.resources.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectionFormScreen(
    viewModel: ConnectionsViewModel,
    connectionId: ConnectionId? = null,
    onBack: () -> Unit,
) {
    val isEditMode = connectionId != null
    val editingConnection by viewModel.editingConnection.collectAsState()
    val isSaving by viewModel.isSaving.collectAsState()
    val error by viewModel.error.collectAsState()

    // Navigate back only after a successful save
    LaunchedEffect(Unit) {
        viewModel.savedSuccessfully.collect {
            onBack()
        }
    }

    var name by remember(connectionId) { mutableStateOf("") }
    var selectedType by remember(connectionId) { mutableStateOf(ConnectionType.GITHUB) }
    var expanded by remember(connectionId) { mutableStateOf(false) }

    // Type-specific fields
    var slackWebhookUrl by remember(connectionId) { mutableStateOf("") }
    var teamCityServerUrl by remember(connectionId) { mutableStateOf("") }
    var teamCityToken by remember(connectionId) { mutableStateOf("") }
    var teamCityPollingInterval by remember(connectionId) { mutableStateOf("30") }
    var githubToken by remember(connectionId) { mutableStateOf("") }
    var githubOwner by remember(connectionId) { mutableStateOf("") }
    var githubRepo by remember(connectionId) { mutableStateOf("") }
    var githubPollingInterval by remember(connectionId) { mutableStateOf("30") }
    // Track initial values to detect dirty state
    var initialName by remember(connectionId) { mutableStateOf("") }
    var initialSlackWebhookUrl by remember(connectionId) { mutableStateOf("") }
    var initialTeamCityServerUrl by remember(connectionId) { mutableStateOf("") }
    var initialTeamCityToken by remember(connectionId) { mutableStateOf("") }
    var initialTeamCityPollingInterval by remember(connectionId) { mutableStateOf("30") }
    var initialGithubToken by remember(connectionId) { mutableStateOf("") }
    var initialGithubOwner by remember(connectionId) { mutableStateOf("") }
    var initialGithubRepo by remember(connectionId) { mutableStateOf("") }
    var initialGithubPollingInterval by remember(connectionId) { mutableStateOf("30") }

    val isDirty by remember {
        derivedStateOf {
            name != initialName ||
                slackWebhookUrl != initialSlackWebhookUrl ||
                teamCityServerUrl != initialTeamCityServerUrl ||
                teamCityToken != initialTeamCityToken ||
                teamCityPollingInterval != initialTeamCityPollingInterval ||
                githubToken != initialGithubToken ||
                githubOwner != initialGithubOwner ||
                githubRepo != initialGithubRepo ||
                githubPollingInterval != initialGithubPollingInterval
        }
    }

    var showDiscardDialog by remember { mutableStateOf(false) }

    if (isEditMode) {
        LaunchedEffect(connectionId) {
            viewModel.loadConnection(connectionId)
        }

        LaunchedEffect(editingConnection) {
            val connection = editingConnection ?: return@LaunchedEffect
            name = connection.name
            initialName = connection.name
            selectedType = connection.type
            when (val config = connection.config) {
                is ConnectionConfig.SlackConfig -> {
                    slackWebhookUrl = config.webhookUrl
                    initialSlackWebhookUrl = config.webhookUrl
                }
                is ConnectionConfig.TeamCityConfig -> {
                    teamCityServerUrl = config.serverUrl
                    teamCityToken = config.token
                    teamCityPollingInterval = config.pollingIntervalSeconds.toString()
                    initialTeamCityServerUrl = config.serverUrl
                    initialTeamCityToken = config.token
                    initialTeamCityPollingInterval = config.pollingIntervalSeconds.toString()
                }
                is ConnectionConfig.GitHubConfig -> {
                    githubToken = config.token
                    githubOwner = config.owner
                    githubRepo = config.repo
                    githubPollingInterval = config.pollingIntervalSeconds.toString()
                    initialGithubToken = config.token
                    initialGithubOwner = config.owner
                    initialGithubRepo = config.repo
                    initialGithubPollingInterval = config.pollingIntervalSeconds.toString()
                }
            }
        }
    }

    val currentConfig by remember {
        derivedStateOf {
            buildConfig(
                selectedType, slackWebhookUrl, teamCityServerUrl, teamCityToken,
                teamCityPollingInterval, githubToken, githubOwner, githubRepo,
                githubPollingInterval,
            )
        }
    }

    val handleBack: () -> Unit = {
        if (isDirty) {
            showDiscardDialog = true
        } else {
            onBack()
        }
    }

    val handleSave: () -> Unit = {
        if (name.isNotBlank() && currentConfig.isValid() && !isSaving) {
            if (connectionId != null) {
                viewModel.updateConnection(connectionId, name, currentConfig)
            } else {
                viewModel.createConnection(name, selectedType, currentConfig)
            }
        }
    }

    CompositionLocalProvider(
        LocalShortcutActions provides ShortcutActions(
            onSave = handleSave,
            hasDialogOpen = showDiscardDialog,
        )
    ) {

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isEditMode) packStringResource(Res.string.connections_edit_title) else packStringResource(Res.string.connections_new_title)) },
                navigationIcon = {
                    RwButton(onClick = handleBack, variant = RwButtonVariant.Ghost) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = packStringResource(Res.string.common_navigate_back))
                        Text(packStringResource(Res.string.common_back))
                    }
                },
                actions = {
                    RwButton(
                        onClick = handleSave,
                        variant = RwButtonVariant.Primary,
                        enabled = name.isNotBlank() && currentConfig.isValid() && !isSaving,
                        modifier = Modifier.testTag("save_connection_button"),
                    ) {
                        Text(if (isSaving) packStringResource(Res.string.common_saving) else packStringResource(Res.string.common_save))
                    }
                },
            )
        },
        modifier = Modifier.testTag("connection_form_screen"),
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 800.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
            RwTextField(
                value = name,
                onValueChange = { name = it },
                label = packStringResource(Res.string.connections_name_label),
                placeholder = packStringResource(Res.string.connections_name_label),
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("connection_name_field"),
            )

            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { if (!isEditMode) expanded = it },
            ) {
                OutlinedTextField(
                    value = selectedType.displayName(),
                    onValueChange = {},
                    readOnly = true,
                    enabled = !isEditMode,
                    label = { Text(packStringResource(Res.string.connections_type_label)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                        .testTag("connection_type_selector"),
                )
                if (!isEditMode) {
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                    ) {
                        ConnectionType.entries.forEach { type ->
                            DropdownMenuItem(
                                text = { Text(type.displayName()) },
                                onClick = {
                                    selectedType = type
                                    expanded = false
                                },
                            )
                        }
                    }
                }
            }

            HorizontalDivider()

            when (selectedType) {
                ConnectionType.SLACK -> {
                    var showSlackWebhook by remember { mutableStateOf(false) }

                    Text(
                        text = packStringResource(Res.string.connections_section_slack),
                        style = AppTypography.subheading,
                        modifier = Modifier.testTag("section_header_slack"),
                    )
                    RwTextField(
                        value = slackWebhookUrl,
                        onValueChange = { slackWebhookUrl = it },
                        label = packStringResource(Res.string.connections_slack_webhook_url),
                        placeholder = packStringResource(Res.string.connections_slack_webhook_url),
                        singleLine = true,
                        visualTransformation = if (showSlackWebhook) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            RwIconButton(onClick = { showSlackWebhook = !showSlackWebhook }, modifier = Modifier.size(32.dp).testTag("slack_webhook_url_toggle_visibility")) {
                                Icon(
                                    if (showSlackWebhook) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (showSlackWebhook) packStringResource(Res.string.connections_hide_password) else packStringResource(Res.string.connections_show_password),
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth().testTag("slack_webhook_url"),
                    )
                }
                ConnectionType.TEAMCITY -> {
                    var showTeamCityToken by remember { mutableStateOf(false) }

                    Text(
                        text = packStringResource(Res.string.connections_section_teamcity),
                        style = AppTypography.subheading,
                        modifier = Modifier.testTag("section_header_teamcity"),
                    )
                    RwTextField(
                        value = teamCityServerUrl,
                        onValueChange = { teamCityServerUrl = it },
                        label = packStringResource(Res.string.connections_tc_server_url),
                        placeholder = packStringResource(Res.string.connections_tc_server_url),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("teamcity_server_url"),
                    )
                    RwTextField(
                        value = teamCityToken,
                        onValueChange = { teamCityToken = it },
                        label = packStringResource(Res.string.connections_tc_token),
                        placeholder = packStringResource(Res.string.connections_tc_token),
                        singleLine = true,
                        visualTransformation = if (showTeamCityToken) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            RwIconButton(onClick = { showTeamCityToken = !showTeamCityToken }, modifier = Modifier.size(32.dp).testTag("teamcity_token_toggle_visibility")) {
                                Icon(
                                    if (showTeamCityToken) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (showTeamCityToken) packStringResource(Res.string.connections_hide_password) else packStringResource(Res.string.connections_show_password),
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth().testTag("teamcity_token"),
                    )
                    RwTextField(
                        value = teamCityPollingInterval,
                        onValueChange = { text ->
                            val filtered = text.filter { it.isDigit() }
                            teamCityPollingInterval = filtered
                        },
                        label = packStringResource(Res.string.connections_tc_polling_interval),
                        placeholder = "30",
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("teamcity_polling_interval"),
                    )
                }
                ConnectionType.GITHUB -> {
                    var showGithubToken by remember { mutableStateOf(false) }

                    Text(
                        text = packStringResource(Res.string.connections_section_github),
                        style = AppTypography.subheading,
                        modifier = Modifier.testTag("section_header_github"),
                    )
                    RwTextField(
                        value = githubToken,
                        onValueChange = { githubToken = it },
                        label = packStringResource(Res.string.connections_github_pat),
                        placeholder = packStringResource(Res.string.connections_github_pat),
                        singleLine = true,
                        visualTransformation = if (showGithubToken) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            RwIconButton(onClick = { showGithubToken = !showGithubToken }, modifier = Modifier.size(32.dp).testTag("github_token_toggle_visibility")) {
                                Icon(
                                    if (showGithubToken) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (showGithubToken) packStringResource(Res.string.connections_hide_password) else packStringResource(Res.string.connections_show_password),
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth().testTag("github_token"),
                    )
                    RwTextField(
                        value = githubOwner,
                        onValueChange = { githubOwner = it },
                        label = packStringResource(Res.string.connections_github_owner),
                        placeholder = packStringResource(Res.string.connections_github_owner),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("github_owner"),
                    )
                    RwTextField(
                        value = githubRepo,
                        onValueChange = { githubRepo = it },
                        label = packStringResource(Res.string.connections_github_repo),
                        placeholder = packStringResource(Res.string.connections_github_repo),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("github_repo"),
                    )
                    RwTextField(
                        value = githubPollingInterval,
                        onValueChange = { text ->
                            val filtered = text.filter { it.isDigit() }
                            githubPollingInterval = filtered
                        },
                        label = packStringResource(Res.string.connections_github_polling_interval),
                        placeholder = "30",
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("github_polling_interval"),
                    )
                }
            }

            error?.let { errorMessage ->
                Text(
                    text = errorMessage.resolve(),
                    color = MaterialTheme.colorScheme.error,
                    style = AppTypography.body,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("connection_form_error"),
                )
            }
            }
        }
    }

    // Unsaved changes confirmation dialog
    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text(packStringResource(Res.string.common_unsaved_title)) },
            text = { Text(packStringResource(Res.string.common_unsaved_message)) },
            confirmButton = {
                RwButton(onClick = {
                    showDiscardDialog = false
                    onBack()
                }, variant = RwButtonVariant.Ghost) {
                    Text(packStringResource(Res.string.common_discard))
                }
            },
            dismissButton = {
                RwButton(onClick = { showDiscardDialog = false }, variant = RwButtonVariant.Ghost) {
                    Text(packStringResource(Res.string.common_cancel))
                }
            },
        )
    }
    } // CompositionLocalProvider
}

private fun buildConfig(
    type: ConnectionType,
    slackWebhookUrl: String,
    teamCityServerUrl: String,
    teamCityToken: String,
    teamCityPollingInterval: String,
    githubToken: String,
    githubOwner: String,
    githubRepo: String,
    githubPollingInterval: String,
): ConnectionConfig = when (type) {
    ConnectionType.SLACK -> ConnectionConfig.SlackConfig(webhookUrl = slackWebhookUrl)
    ConnectionType.TEAMCITY -> ConnectionConfig.TeamCityConfig(
        serverUrl = teamCityServerUrl,
        token = teamCityToken,
        pollingIntervalSeconds = teamCityPollingInterval.toIntOrNull()?.coerceIn(5, 300) ?: 30,
    )
    ConnectionType.GITHUB -> ConnectionConfig.GitHubConfig(
        token = githubToken,
        owner = githubOwner,
        repo = githubRepo,
        pollingIntervalSeconds = githubPollingInterval.toIntOrNull()?.coerceIn(5, 300) ?: 30,
    )
}

private fun ConnectionConfig.isValid(): Boolean = when (this) {
    is ConnectionConfig.SlackConfig -> webhookUrl.isNotBlank()
    is ConnectionConfig.TeamCityConfig -> serverUrl.isNotBlank() && token.isNotBlank()
    is ConnectionConfig.GitHubConfig -> token.isNotBlank() && owner.isNotBlank() && repo.isNotBlank()
}
