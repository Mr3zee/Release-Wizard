package com.github.mr3zee.connections

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.github.mr3zee.model.Connection
import com.github.mr3zee.model.ConnectionId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectionListScreen(
    viewModel: ConnectionsViewModel,
    onCreateConnection: () -> Unit,
    onEditConnection: (ConnectionId) -> Unit,
    onBack: () -> Unit,
) {
    val connections by viewModel.connections.collectAsState()
    val webhookUrls by viewModel.webhookUrls.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadConnections()
    }

    var connectionToDelete by remember { mutableStateOf<Connection?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Connections") },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text("Back")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onCreateConnection,
                modifier = Modifier.testTag("create_connection_fab"),
            ) {
                Text("+")
            }
        },
        modifier = Modifier.testTag("connection_list_screen"),
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (error != null) {
                Snackbar(
                    modifier = Modifier.padding(16.dp),
                    action = {
                        TextButton(onClick = { viewModel.loadConnections() }) {
                            Text("Retry")
                        }
                    },
                ) {
                    Text(error ?: "")
                }
            }

            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            } else if (connections.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "No connections yet. Add one to get started.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("connection_list"),
                ) {
                    items(connections, key = { it.id.value }) { connection ->
                        ConnectionListItem(
                            connection = connection,
                            webhookUrl = webhookUrls[connection.id.value],
                            onClick = { onEditConnection(connection.id) },
                            onDelete = { connectionToDelete = connection },
                            onTest = { viewModel.testConnection(connection.id) },
                        )
                    }
                }
            }
        }
    }

    connectionToDelete?.let { connection ->
        AlertDialog(
            onDismissRequest = { connectionToDelete = null },
            title = { Text("Delete Connection") },
            text = { Text("Are you sure you want to delete \"${connection.name}\"?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteConnection(connection.id)
                    connectionToDelete = null
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { connectionToDelete = null }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun ConnectionListItem(
    connection: Connection,
    webhookUrl: String?,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onTest: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable(onClick = onClick)
            .testTag("connection_item_${connection.id.value}"),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = connection.name,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = connection.type.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (webhookUrl != null) {
                    Text(
                        text = "Webhook: $webhookUrl",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag("webhook_url_${connection.id.value}"),
                    )
                }
            }
            Row {
                TextButton(onClick = onTest) {
                    Text("Test")
                }
                TextButton(onClick = onDelete) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}
