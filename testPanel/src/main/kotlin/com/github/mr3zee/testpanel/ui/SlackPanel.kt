package com.github.mr3zee.testpanel.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.mr3zee.testpanel.model.TestPanelState

@Composable
fun SlackPanel(state: TestPanelState) {
    val mockState by state.state.collectAsState()
    val messages = mockState.slackMessages
    val port = mockState.serverConfig.port

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Slack Messages", style = MaterialTheme.typography.headlineSmall)
            if (messages.isNotEmpty()) {
                OutlinedButton(onClick = { state.clearSlackMessages() }) {
                    Text("Clear All")
                }
            }
        }

        // Summary stats
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            SlackStatChip("Messages", messages.size, MaterialTheme.colorScheme.primary)
        }

        if (messages.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "No messages received yet. Configure a Slack connection in Release Wizard " +
                            "pointing to http://localhost:$port/services/T00/B00/xxx",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                for (message in messages.sortedByDescending { it.id }) {
                    SlackMessageCard(message)
                }
            }
        }
    }
}

@Composable
private fun SlackStatChip(label: String, count: Int, color: androidx.compose.ui.graphics.Color) {
    Surface(
        color = color.copy(alpha = 0.15f),
        shape = MaterialTheme.shapes.small,
    ) {
        Text(
            "$label: $count",
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium,
            color = color,
        )
    }
}

@Composable
private fun SlackMessageCard(message: com.github.mr3zee.testpanel.model.SlackMessage) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                message.receivedAt,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                message.text,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
