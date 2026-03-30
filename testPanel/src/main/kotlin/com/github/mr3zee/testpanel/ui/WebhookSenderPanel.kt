package com.github.mr3zee.testpanel.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.github.mr3zee.testpanel.model.TestPanelState
import com.github.mr3zee.testpanel.model.WebhookSendRecord
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Composable
fun WebhookSenderPanel(state: TestPanelState) {
    val mockState by state.state.collectAsState()
    val builds = mockState.builds
    val history = mockState.webhookSendHistory
    val scope = rememberCoroutineScope()

    val httpClient = remember {
        HttpClient(CIO) {
            install(ContentNegotiation) { json() }
        }
    }
    DisposableEffect(Unit) {
        onDispose { httpClient.close() }
    }

    // Extract builds that have webhook URL and token in trigger properties (latest first)
    val webhookBuilds = builds.filter { build ->
        build.triggerProperties.containsKey("env.RELEASE_WIZARD_WEBHOOK_URL") &&
                build.triggerProperties.containsKey("env.RELEASE_WIZARD_WEBHOOK_TOKEN")
    }

    // Auto-pick URL and token from the latest build that has them
    val latestWebhookBuild = webhookBuilds.lastOrNull()
    val autoUrl = latestWebhookBuild?.triggerProperties?.get("env.RELEASE_WIZARD_WEBHOOK_URL").orEmpty()
    val autoToken = latestWebhookBuild?.triggerProperties?.get("env.RELEASE_WIZARD_WEBHOOK_TOKEN").orEmpty()

    // Track which auto-picked build we've applied, so we update when a new build arrives
    var appliedBuildId by remember { mutableStateOf<Int?>(null) }
    var url by remember { mutableStateOf("") }
    var token by remember { mutableStateOf("") }

    // When a new build with webhook params appears, auto-populate
    LaunchedEffect(latestWebhookBuild?.id) {
        if (latestWebhookBuild != null && latestWebhookBuild.id != appliedBuildId) {
            url = autoUrl
            token = autoToken
            appliedBuildId = latestWebhookBuild.id
        }
    }

    // Send form state
    var statusText by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var lastError by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Webhook Sender", style = MaterialTheme.typography.headlineSmall)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Send form
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("Send Status Update", style = MaterialTheme.typography.titleSmall)

                    if (latestWebhookBuild != null) {
                        val btName = mockState.buildTypes.find { it.id == latestWebhookBuild.buildTypeId }?.name
                            ?: latestWebhookBuild.buildTypeId
                        Text(
                            "Auto-filled from $btName #${latestWebhookBuild.number}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }

                    OutlinedTextField(
                        value = url,
                        onValueChange = { url = it },
                        label = { Text("Webhook URL") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = token,
                        onValueChange = { token = it },
                        label = { Text("Token") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    // Build picker when multiple builds have webhook config
                    if (webhookBuilds.size > 1) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "Other builds:",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            for (build in webhookBuilds.reversed()) {
                                if (build.id == latestWebhookBuild?.id && url == autoUrl && token == autoToken) continue
                                val btName = mockState.buildTypes.find { it.id == build.buildTypeId }?.name
                                    ?: build.buildTypeId
                                TextButton(
                                    onClick = {
                                        url = build.triggerProperties["env.RELEASE_WIZARD_WEBHOOK_URL"].orEmpty()
                                        token = build.triggerProperties["env.RELEASE_WIZARD_WEBHOOK_TOKEN"].orEmpty()
                                    },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                ) {
                                    Text(
                                        "$btName #${build.number}",
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                }
                            }
                        }
                    }

                    OutlinedTextField(
                        value = statusText,
                        onValueChange = { statusText = it },
                        label = { Text("Status") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("Description (optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    if (lastError != null) {
                        Text(
                            lastError.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                sending = true
                                lastError = null
                                scope.launch {
                                    try {
                                        val response = httpClient.post(url) {
                                            header(HttpHeaders.Authorization, "Bearer $token")
                                            contentType(ContentType.Application.Json)
                                            setBody(buildJsonObject(statusText, description))
                                        }
                                        val responseCode = response.status.value
                                        state.addWebhookSendRecord(
                                            WebhookSendRecord(
                                                url = url,
                                                token = token,
                                                status = statusText,
                                                description = description.takeIf { it.isNotBlank() },
                                                responseCode = responseCode,
                                                sentAt = formatTimestamp(),
                                            )
                                        )
                                    } catch (e: Exception) {
                                        lastError = "Error: ${e.message}"
                                        state.addWebhookSendRecord(
                                            WebhookSendRecord(
                                                url = url,
                                                token = token,
                                                status = statusText,
                                                description = description.takeIf { it.isNotBlank() },
                                                responseCode = null,
                                                sentAt = formatTimestamp(),
                                            )
                                        )
                                    } finally {
                                        sending = false
                                    }
                                }
                            },
                            enabled = url.isNotBlank() && token.isNotBlank() && statusText.isNotBlank() && !sending,
                        ) {
                            Text(if (sending) "Sending..." else "Send")
                        }
                    }
                }
            }

            // Send history
            if (history.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Send History", style = MaterialTheme.typography.titleSmall)
                    OutlinedButton(onClick = { state.clearWebhookHistory() }) {
                        Text("Clear History")
                    }
                }

                for (record in history.reversed()) {
                    WebhookRecordCard(record)
                }
            }
        }
    }
}

@Composable
private fun WebhookRecordCard(record: WebhookSendRecord) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    record.sentAt,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (record.responseCode != null) {
                    val color = if (record.responseCode in 200..299) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    }
                    Surface(
                        color = color.copy(alpha = 0.15f),
                        shape = MaterialTheme.shapes.extraSmall,
                    ) {
                        Text(
                            "HTTP ${record.responseCode}",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = color,
                        )
                    }
                } else {
                    Surface(
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.15f),
                        shape = MaterialTheme.shapes.extraSmall,
                    ) {
                        Text(
                            "FAILED",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "Status: ${record.status}",
                style = MaterialTheme.typography.bodySmall,
            )
            if (record.description != null) {
                Text(
                    "Description: ${record.description}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                record.url,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun buildJsonObject(status: String, description: String): Map<String, String> {
    val map = mutableMapOf("status" to status)
    if (description.isNotBlank()) {
        map["description"] = description
    }
    return map
}

private fun formatTimestamp(): String {
    return LocalDateTime.now().format(
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    )
}
