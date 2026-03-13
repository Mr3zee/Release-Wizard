package com.github.mr3zee.connections

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.github.mr3zee.model.ConnectionConfig
import com.github.mr3zee.model.ConnectionId
import com.github.mr3zee.model.ConnectionType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectionFormScreen(
    viewModel: ConnectionsViewModel,
    connectionId: ConnectionId? = null,
    onBack: () -> Unit,
) {
    val isEditMode = connectionId != null
    val editingConnection by viewModel.editingConnection.collectAsState()

    var name by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf(ConnectionType.GITHUB) }
    var expanded by remember { mutableStateOf(false) }

    // Type-specific fields
    var slackWebhookUrl by remember { mutableStateOf("") }
    var teamCityServerUrl by remember { mutableStateOf("") }
    var teamCityToken by remember { mutableStateOf("") }
    var teamCityWebhookSecret by remember { mutableStateOf("") }
    var githubToken by remember { mutableStateOf("") }
    var githubOwner by remember { mutableStateOf("") }
    var githubRepo by remember { mutableStateOf("") }
    var githubWebhookSecret by remember { mutableStateOf("") }
    var mavenUsername by remember { mutableStateOf("") }
    var mavenPassword by remember { mutableStateOf("") }
    var mavenBaseUrl by remember { mutableStateOf("https://central.sonatype.com") }

    if (isEditMode) {
        LaunchedEffect(connectionId) {
            viewModel.loadConnection(connectionId!!)
        }

        LaunchedEffect(editingConnection) {
            val connection = editingConnection ?: return@LaunchedEffect
            name = connection.name
            selectedType = connection.type
            when (val config = connection.config) {
                is ConnectionConfig.SlackConfig -> {
                    slackWebhookUrl = config.webhookUrl
                }
                is ConnectionConfig.TeamCityConfig -> {
                    teamCityServerUrl = config.serverUrl
                    teamCityToken = config.token
                    teamCityWebhookSecret = config.webhookSecret
                }
                is ConnectionConfig.GitHubConfig -> {
                    githubToken = config.token
                    githubOwner = config.owner
                    githubRepo = config.repo
                    githubWebhookSecret = config.webhookSecret
                }
                is ConnectionConfig.MavenCentralConfig -> {
                    mavenUsername = config.username
                    mavenPassword = config.password
                    mavenBaseUrl = config.baseUrl
                }
            }
        }
    }

    val currentConfig by remember {
        derivedStateOf {
            buildConfig(
                selectedType, slackWebhookUrl, teamCityServerUrl, teamCityToken,
                teamCityWebhookSecret, githubToken, githubOwner, githubRepo,
                githubWebhookSecret, mavenUsername, mavenPassword, mavenBaseUrl,
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isEditMode) "Edit Connection" else "New Connection") },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text("Back")
                    }
                },
                actions = {
                    Button(
                        onClick = {
                            if (isEditMode) {
                                viewModel.updateConnection(connectionId!!, name, currentConfig)
                            } else {
                                viewModel.createConnection(name, selectedType, currentConfig)
                            }
                            onBack()
                        },
                        enabled = name.isNotBlank() && currentConfig.isValid(),
                        modifier = Modifier.testTag("save_connection_button"),
                    ) {
                        Text("Save")
                    }
                },
            )
        },
        modifier = Modifier.testTag("connection_form_screen"),
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Connection Name") },
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
                    value = selectedType.name,
                    onValueChange = {},
                    readOnly = true,
                    enabled = !isEditMode,
                    label = { Text("Type") },
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
                                text = { Text(type.name) },
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
                    OutlinedTextField(
                        value = slackWebhookUrl,
                        onValueChange = { slackWebhookUrl = it },
                        label = { Text("Webhook URL") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("slack_webhook_url"),
                    )
                }
                ConnectionType.TEAMCITY -> {
                    OutlinedTextField(
                        value = teamCityServerUrl,
                        onValueChange = { teamCityServerUrl = it },
                        label = { Text("Server URL") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("teamcity_server_url"),
                    )
                    OutlinedTextField(
                        value = teamCityToken,
                        onValueChange = { teamCityToken = it },
                        label = { Text("Token") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth().testTag("teamcity_token"),
                    )
                    OutlinedTextField(
                        value = teamCityWebhookSecret,
                        onValueChange = { teamCityWebhookSecret = it },
                        label = { Text("Webhook Secret (optional)") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth().testTag("teamcity_webhook_secret"),
                    )
                }
                ConnectionType.GITHUB -> {
                    OutlinedTextField(
                        value = githubToken,
                        onValueChange = { githubToken = it },
                        label = { Text("Personal Access Token") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth().testTag("github_token"),
                    )
                    OutlinedTextField(
                        value = githubOwner,
                        onValueChange = { githubOwner = it },
                        label = { Text("Owner") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("github_owner"),
                    )
                    OutlinedTextField(
                        value = githubRepo,
                        onValueChange = { githubRepo = it },
                        label = { Text("Repository") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("github_repo"),
                    )
                    OutlinedTextField(
                        value = githubWebhookSecret,
                        onValueChange = { githubWebhookSecret = it },
                        label = { Text("Webhook Secret (optional)") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth().testTag("github_webhook_secret"),
                    )
                }
                ConnectionType.MAVEN_CENTRAL -> {
                    OutlinedTextField(
                        value = mavenUsername,
                        onValueChange = { mavenUsername = it },
                        label = { Text("Username") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("maven_username"),
                    )
                    OutlinedTextField(
                        value = mavenPassword,
                        onValueChange = { mavenPassword = it },
                        label = { Text("Password") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth().testTag("maven_password"),
                    )
                    OutlinedTextField(
                        value = mavenBaseUrl,
                        onValueChange = { mavenBaseUrl = it },
                        label = { Text("Base URL") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("maven_base_url"),
                    )
                }
            }
        }
    }
}

private fun buildConfig(
    type: ConnectionType,
    slackWebhookUrl: String,
    teamCityServerUrl: String,
    teamCityToken: String,
    teamCityWebhookSecret: String,
    githubToken: String,
    githubOwner: String,
    githubRepo: String,
    githubWebhookSecret: String,
    mavenUsername: String,
    mavenPassword: String,
    mavenBaseUrl: String,
): ConnectionConfig = when (type) {
    ConnectionType.SLACK -> ConnectionConfig.SlackConfig(webhookUrl = slackWebhookUrl)
    ConnectionType.TEAMCITY -> ConnectionConfig.TeamCityConfig(
        serverUrl = teamCityServerUrl,
        token = teamCityToken,
        webhookSecret = teamCityWebhookSecret,
    )
    ConnectionType.GITHUB -> ConnectionConfig.GitHubConfig(
        token = githubToken,
        owner = githubOwner,
        repo = githubRepo,
        webhookSecret = githubWebhookSecret,
    )
    ConnectionType.MAVEN_CENTRAL -> ConnectionConfig.MavenCentralConfig(
        username = mavenUsername,
        password = mavenPassword,
        baseUrl = mavenBaseUrl,
    )
}

private fun ConnectionConfig.isValid(): Boolean = when (this) {
    is ConnectionConfig.SlackConfig -> webhookUrl.isNotBlank()
    is ConnectionConfig.TeamCityConfig -> serverUrl.isNotBlank() && token.isNotBlank()
    is ConnectionConfig.GitHubConfig -> token.isNotBlank() && owner.isNotBlank() && repo.isNotBlank()
    is ConnectionConfig.MavenCentralConfig -> username.isNotBlank() && password.isNotBlank() && baseUrl.isNotBlank()
}
