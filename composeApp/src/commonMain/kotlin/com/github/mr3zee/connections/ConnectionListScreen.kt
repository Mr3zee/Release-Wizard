package com.github.mr3zee.connections

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.github.mr3zee.components.ListItemCard
import com.github.mr3zee.components.loadMoreItem
import com.github.mr3zee.model.Connection
import com.github.mr3zee.model.ConnectionId
import com.github.mr3zee.model.ConnectionType

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
    val searchQuery by viewModel.searchQuery.collectAsState()
    val typeFilter by viewModel.typeFilter.collectAsState()
    val isLoadingMore by viewModel.isLoadingMore.collectAsState()
    val pagination by viewModel.pagination.collectAsState()
    val testSuccess by viewModel.testSuccessMessage.collectAsState()

    var connectionToDelete by remember { mutableStateOf<Connection?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }

    // Show errors via snackbar
    LaunchedEffect(error) {
        val msg = error ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(
            message = msg,
            actionLabel = "Retry",
            duration = SnackbarDuration.Long,
        ).let { result ->
            if (result == SnackbarResult.ActionPerformed) {
                viewModel.loadConnections()
            }
        }
        viewModel.dismissError()
    }

    // Show test success via snackbar
    LaunchedEffect(testSuccess) {
        val msg = testSuccess ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(
            message = msg,
            duration = SnackbarDuration.Short,
        )
        viewModel.clearTestSuccessMessage()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Connections") },
                navigationIcon = {
                    TextButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("back_button"),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Navigate back")
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
                Icon(Icons.Default.Add, contentDescription = "Create connection")
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = Modifier.testTag("connection_list_screen"),
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.setSearchQuery(it) },
                placeholder = { Text("Search connections...") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .testTag("search_field"),
            )
            Row(
                modifier = Modifier
                    .widthIn(max = 900.dp)
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = typeFilter == null,
                    onClick = { viewModel.setTypeFilter(null) },
                    label = { Text("All") },
                )
                for (type in ConnectionType.entries) {
                    FilterChip(
                        selected = typeFilter == type,
                        onClick = {
                            viewModel.setTypeFilter(if (typeFilter == type) null else type)
                        },
                        label = { Text(type.name.replace("_", " ")) },
                        modifier = Modifier.testTag("filter_${type.name}"),
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))

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
                    if (searchQuery.isNotBlank() || typeFilter != null) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "No results match your search.",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            TextButton(onClick = {
                                viewModel.setSearchQuery("")
                                viewModel.setTypeFilter(null)
                            }) {
                                Text("Clear search")
                            }
                        }
                    } else {
                        Text(
                            text = "No connections yet. Add one to get started.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("connection_list"),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    items(connections, key = { it.id.value }) { connection ->
                        ConnectionListItem(
                            connection = connection,
                            webhookUrl = webhookUrls[connection.id.value],
                            onClick = { onEditConnection(connection.id) },
                            onDelete = { connectionToDelete = connection },
                            onTest = { viewModel.testConnection(connection.id) },
                            modifier = Modifier.widthIn(max = 900.dp),
                        )
                    }
                    loadMoreItem(pagination, isLoadingMore, onLoadMore = { viewModel.loadMore() })
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
    modifier: Modifier = Modifier,
) {
    ListItemCard(
        onClick = onClick,
        testTag = "connection_item_${connection.id.value}",
        modifier = modifier,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = connection.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
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
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
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
