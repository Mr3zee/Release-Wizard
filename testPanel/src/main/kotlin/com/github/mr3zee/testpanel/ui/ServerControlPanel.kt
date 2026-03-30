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
import com.github.mr3zee.testpanel.server.TestPanelServer

@Composable
fun ServerControlPanel(
    state: TestPanelState,
    server: TestPanelServer,
) {
    val mockState by state.state.collectAsState()
    val config = mockState.serverConfig
    val isRunning by server.isRunning.collectAsState()
    var startError by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Server Control", style = MaterialTheme.typography.headlineSmall)

        // Server config
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                var portText by remember(config.port) { mutableStateOf(config.port.toString()) }
                var tokenText by remember(config.acceptedToken) { mutableStateOf(config.acceptedToken) }

                OutlinedTextField(
                    value = portText,
                    onValueChange = {
                        portText = it
                        val port = it.toIntOrNull()
                        if (port != null && port in 1..65535) {
                            state.updateServerConfig(config.copy(port = port))
                        }
                    },
                    label = { Text("Port") },
                    enabled = !isRunning,
                    singleLine = true,
                    modifier = Modifier.width(200.dp),
                )

                OutlinedTextField(
                    value = tokenText,
                    onValueChange = {
                        tokenText = it
                        state.updateServerConfig(config.copy(acceptedToken = it))
                    },
                    label = { Text("Accepted Token (empty = accept any)") },
                    enabled = !isRunning,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Button(
                        onClick = {
                            if (isRunning) {
                                server.stop()
                            } else {
                                try {
                                    server.start(config.port)
                                    startError = null
                                } catch (e: Exception) {
                                    startError = e.message ?: "Failed to start server"
                                }
                            }
                        },
                        colors = if (isRunning) ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        ) else ButtonDefaults.buttonColors(),
                    ) {
                        Text(if (isRunning) "Stop Server" else "Start Server")
                    }

                    if (isRunning) {
                        Text(
                            "Listening on http://localhost:${config.port}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    startError?.let { error ->
                        Text(
                            error,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }

        // Request log
        Text("Request Log", style = MaterialTheme.typography.titleMedium)

        val log by state.requestLog.collectAsState()

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                "${log.size} requests",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (log.isNotEmpty()) {
                TextButton(onClick = { state.clearLog() }) {
                    Text("Clear")
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth().weight(1f),
        ) {
            if (log.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "No requests yet",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(8.dp),
                ) {
                    for (entry in log.asReversed()) {
                        val statusColor = when (entry.statusCode) {
                            in 200..299 -> MaterialTheme.colorScheme.primary
                            in 400..499 -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                        Text(
                            "${entry.timestamp}  ${entry.method.padEnd(6)} ${entry.statusCode}  ${entry.path}",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = statusColor,
                            modifier = Modifier.padding(vertical = 1.dp),
                        )
                    }
                }
            }
        }
    }
}
