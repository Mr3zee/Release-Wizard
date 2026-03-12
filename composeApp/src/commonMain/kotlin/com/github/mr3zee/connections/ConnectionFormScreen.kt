package com.github.mr3zee.connections

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.github.mr3zee.model.ConnectionConfig
import com.github.mr3zee.model.ConnectionType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectionFormScreen(
    viewModel: ConnectionsViewModel,
    onBack: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf(ConnectionType.GITHUB) }
    var expanded by remember { mutableStateOf(false) }

    // Type-specific fields
    var slackWebhookUrl by remember { mutableStateOf("") }
    var teamCityServerUrl by remember { mutableStateOf("") }
    var teamCityToken by remember { mutableStateOf("") }
    var githubToken by remember { mutableStateOf("") }
    var githubOwner by remember { mutableStateOf("") }
    var githubRepo by remember { mutableStateOf("") }
    var mavenUsername by remember { mutableStateOf("") }
    var mavenPassword by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("New Connection") },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text("Back")
                    }
                },
                actions = {
                    Button(
                        onClick = {
                            val config = buildConfig(
                                selectedType, slackWebhookUrl, teamCityServerUrl, teamCityToken,
                                githubToken, githubOwner, githubRepo, mavenUsername, mavenPassword,
                            )
                            viewModel.createConnection(name, selectedType, config)
                            onBack()
                        },
                        enabled = name.isNotBlank() && isConfigValid(
                            selectedType, slackWebhookUrl, teamCityServerUrl, teamCityToken,
                            githubToken, githubOwner, githubRepo, mavenUsername, mavenPassword,
                        ),
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
                onExpandedChange = { expanded = it },
            ) {
                OutlinedTextField(
                    value = selectedType.name,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Type") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                        .testTag("connection_type_selector"),
                )
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
    githubToken: String,
    githubOwner: String,
    githubRepo: String,
    mavenUsername: String,
    mavenPassword: String,
): ConnectionConfig = when (type) {
    ConnectionType.SLACK -> ConnectionConfig.SlackConfig(webhookUrl = slackWebhookUrl)
    ConnectionType.TEAMCITY -> ConnectionConfig.TeamCityConfig(serverUrl = teamCityServerUrl, token = teamCityToken)
    ConnectionType.GITHUB -> ConnectionConfig.GitHubConfig(token = githubToken, owner = githubOwner, repo = githubRepo)
    ConnectionType.MAVEN_CENTRAL -> ConnectionConfig.MavenCentralConfig(username = mavenUsername, password = mavenPassword)
}

private fun isConfigValid(
    type: ConnectionType,
    slackWebhookUrl: String,
    teamCityServerUrl: String,
    teamCityToken: String,
    githubToken: String,
    githubOwner: String,
    githubRepo: String,
    mavenUsername: String,
    mavenPassword: String,
): Boolean = when (type) {
    ConnectionType.SLACK -> slackWebhookUrl.isNotBlank()
    ConnectionType.TEAMCITY -> teamCityServerUrl.isNotBlank() && teamCityToken.isNotBlank()
    ConnectionType.GITHUB -> githubToken.isNotBlank() && githubOwner.isNotBlank() && githubRepo.isNotBlank()
    ConnectionType.MAVEN_CENTRAL -> mavenUsername.isNotBlank() && mavenPassword.isNotBlank()
}
