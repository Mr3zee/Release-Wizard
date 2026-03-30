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
import com.github.mr3zee.testpanel.model.TriggerRecord
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Composable
fun ReleaseTriggerPanel(state: TestPanelState) {
    val mockState by state.state.collectAsState()
    val history = mockState.triggerHistory
    val scope = rememberCoroutineScope()

    val httpClient = remember {
        HttpClient(CIO)
    }
    DisposableEffect(Unit) {
        onDispose { httpClient.close() }
    }

    var triggerUrl by remember { mutableStateOf("") }
    var triggerSecret by remember { mutableStateOf("") }
    var firing by remember { mutableStateOf(false) }
    var triggerResult by remember { mutableStateOf<String?>(null) }
    var triggerResultIsError by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Release Trigger", style = MaterialTheme.typography.headlineSmall)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Trigger form
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        "Fire a webhook trigger to start a release. Use the full URL and secret from the " +
                            "trigger configuration in Release Wizard.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    OutlinedTextField(
                        value = triggerUrl,
                        onValueChange = { triggerUrl = it },
                        label = { Text("Trigger Webhook URL") },
                        placeholder = { Text("http://localhost:8080/api/v1/triggers/webhook/{triggerId}") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = triggerSecret,
                        onValueChange = { triggerSecret = it },
                        label = { Text("Secret") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    if (triggerResult != null) {
                        Text(
                            triggerResult.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (triggerResultIsError) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.primary,
                        )
                    }

                    Button(
                        onClick = {
                            firing = true
                            triggerResult = null
                            scope.launch {
                                try {
                                    val response = httpClient.post(triggerUrl) {
                                        header(HttpHeaders.Authorization, "Bearer $triggerSecret")
                                    }
                                    val body = response.bodyAsText()
                                    triggerResultIsError = !response.status.isSuccess()
                                    triggerResult = "HTTP ${response.status.value}: $body"
                                    state.addTriggerRecord(
                                        TriggerRecord(
                                            url = triggerUrl,
                                            responseCode = response.status.value,
                                            responseBody = body,
                                            sentAt = formatTriggerTimestamp(),
                                        )
                                    )
                                } catch (e: Exception) {
                                    triggerResultIsError = true
                                    triggerResult = "Error: ${e.message}"
                                    state.addTriggerRecord(
                                        TriggerRecord(
                                            url = triggerUrl,
                                            responseCode = null,
                                            responseBody = e.message,
                                            sentAt = formatTriggerTimestamp(),
                                        )
                                    )
                                } finally {
                                    firing = false
                                }
                            }
                        },
                        enabled = triggerUrl.isNotBlank() && triggerSecret.isNotBlank() && !firing,
                    ) {
                        Text(if (firing) "Firing..." else "Fire Trigger")
                    }
                }
            }

            // History
            if (history.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Trigger History", style = MaterialTheme.typography.titleSmall)
                    OutlinedButton(onClick = { state.clearTriggerHistory() }) {
                        Text("Clear")
                    }
                }

                for (record in history.reversed()) {
                    TriggerRecordCard(record)
                }
            }
        }
    }
}

@Composable
private fun TriggerRecordCard(record: TriggerRecord) {
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
            if (record.responseBody != null) {
                Text(
                    record.responseBody,
                    style = MaterialTheme.typography.bodySmall,
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

private fun formatTriggerTimestamp(): String {
    return LocalDateTime.now().format(
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    )
}
