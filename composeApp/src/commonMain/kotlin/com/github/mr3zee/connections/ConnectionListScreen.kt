package com.github.mr3zee.connections

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.github.mr3zee.components.ListItemCard
import com.github.mr3zee.components.RefreshErrorBanner
import com.github.mr3zee.components.loadMoreItem
import com.github.mr3zee.model.Connection
import com.github.mr3zee.model.ConnectionId
import com.github.mr3zee.model.ConnectionType
import com.github.mr3zee.util.displayName
import com.github.mr3zee.util.resolve
import com.github.mr3zee.i18n.packStringResource
import releasewizard.composeapp.generated.resources.*

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
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val isManualRefresh by viewModel.isManualRefresh.collectAsState()
    val refreshError by viewModel.refreshError.collectAsState()

    var connectionToDelete by remember { mutableStateOf<Connection?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }

    // Spin animation for refresh icon
    val infiniteTransition = rememberInfiniteTransition()
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = LinearEasing),
        ),
    )
    val spinning = isManualRefresh && isRefreshing

    val retryLabel = packStringResource(Res.string.common_retry)
    val resolvedError = error?.resolve()
    val resolvedTestSuccess = testSuccess?.resolve()

    // Show errors via snackbar
    LaunchedEffect(error) {
        val msg = resolvedError ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(
            message = msg,
            actionLabel = retryLabel,
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
        val msg = resolvedTestSuccess ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(
            message = msg,
            duration = SnackbarDuration.Short,
        )
        viewModel.clearTestSuccessMessage()
    }

    Scaffold(
        topBar = {
            Box {
                TopAppBar(
                    title = { Text(packStringResource(Res.string.connections_title)) },
                    navigationIcon = {
                        TextButton(
                            onClick = onBack,
                            modifier = Modifier.testTag("back_button"),
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = packStringResource(Res.string.common_navigate_back))
                            Text(packStringResource(Res.string.common_back))
                        }
                    },
                    actions = {
                        TooltipBox(
                            positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                            tooltip = { PlainTooltip { Text(packStringResource(Res.string.common_refresh)) } },
                            state = rememberTooltipState(),
                        ) {
                            IconButton(
                                onClick = { viewModel.refresh() },
                                modifier = Modifier.testTag("refresh_button"),
                            ) {
                                Icon(
                                    Icons.Outlined.Refresh,
                                    contentDescription = packStringResource(Res.string.common_refresh),
                                    modifier = Modifier
                                        .rotate(if (spinning) rotation else 0f)
                                        .testTag(if (spinning) "refresh_icon_spinning" else "refresh_icon_idle"),
                                )
                            }
                        }
                    },
                )
                if (isRefreshing && !isLoading) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .align(Alignment.BottomCenter)
                            .alpha(if (isManualRefresh) 1f else 0.5f)
                            .testTag("refresh_indicator"),
                    )
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onCreateConnection,
                modifier = Modifier.testTag("create_connection_fab"),
            ) {
                Icon(Icons.Default.Add, contentDescription = packStringResource(Res.string.connections_create))
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
            // Refresh error banner
            val resolvedRefreshError = refreshError?.resolve()
            if (resolvedRefreshError != null) {
                RefreshErrorBanner(
                    message = resolvedRefreshError,
                    onDismiss = { viewModel.dismissRefreshError() },
                )
            }

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.setSearchQuery(it) },
                placeholder = { Text(packStringResource(Res.string.connections_search_placeholder)) },
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
                    label = { Text(packStringResource(Res.string.common_all)) },
                )
                for (type in ConnectionType.entries) {
                    FilterChip(
                        selected = typeFilter == type,
                        onClick = {
                            viewModel.setTypeFilter(if (typeFilter == type) null else type)
                        },
                        label = { Text(type.displayName()) },
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
                                text = packStringResource(Res.string.common_no_search_results),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            TextButton(onClick = {
                                viewModel.setSearchQuery("")
                                viewModel.setTypeFilter(null)
                            }) {
                                Text(packStringResource(Res.string.common_clear_search))
                            }
                        }
                    } else {
                        Text(
                            text = packStringResource(Res.string.connections_empty_state),
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
            title = { Text(packStringResource(Res.string.connections_delete_title)) },
            text = { Text(packStringResource(Res.string.connections_delete_confirmation, connection.name)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteConnection(connection.id)
                    connectionToDelete = null
                }) {
                    Text(packStringResource(Res.string.common_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { connectionToDelete = null }) {
                    Text(packStringResource(Res.string.common_cancel))
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
                text = connection.type.displayName(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (webhookUrl != null) {
                Text(
                    text = packStringResource(Res.string.connections_webhook_display, webhookUrl),
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
                Text(packStringResource(Res.string.connections_test))
            }
            TextButton(onClick = onDelete) {
                Text(packStringResource(Res.string.common_delete), color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
