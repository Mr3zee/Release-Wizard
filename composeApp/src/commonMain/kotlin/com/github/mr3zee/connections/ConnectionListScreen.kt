package com.github.mr3zee.connections

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.github.mr3zee.components.ListItemCard
import com.github.mr3zee.components.RefreshErrorBanner
import com.github.mr3zee.components.RefreshIconButton
import com.github.mr3zee.components.RwBadge
import com.github.mr3zee.components.RwButton
import com.github.mr3zee.components.RwButtonVariant
import com.github.mr3zee.components.RwChip
import com.github.mr3zee.components.RwFab
import com.github.mr3zee.components.RwInlineConfirmation
import com.github.mr3zee.components.RwTextField
import com.github.mr3zee.components.RwTooltip
import com.github.mr3zee.components.loadMoreItem
import com.github.mr3zee.model.Connection
import com.github.mr3zee.model.ConnectionId
import com.github.mr3zee.model.ConnectionType
import com.github.mr3zee.keyboard.ProvideShortcutActions
import com.github.mr3zee.keyboard.ShortcutActions
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import com.github.mr3zee.theme.AppTypography
import com.github.mr3zee.theme.LocalAppColors
import com.github.mr3zee.theme.Spacing
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
    val testingConnectionIds by viewModel.testingConnectionIds.collectAsState()

    var connectionToDelete by remember { mutableStateOf<Connection?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }
    val searchFocusRequester = remember { FocusRequester() }

    val isDialogOpen = connectionToDelete != null
    val shortcutActions = remember(isDialogOpen) {
        ShortcutActions(
            onSearch = { searchFocusRequester.requestFocus() },
            onCreate = { onCreateConnection() },
            onRefresh = { viewModel.loadConnections() },
            hasDialogOpen = isDialogOpen,
        )
    }
    ProvideShortcutActions(shortcutActions) {

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
                        RwButton(
                            onClick = onBack,
                            variant = RwButtonVariant.Ghost,
                            modifier = Modifier.testTag("back_button"),
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = packStringResource(Res.string.common_navigate_back))
                            Text(packStringResource(Res.string.common_back))
                        }
                    },
                    actions = {
                        RefreshIconButton(
                            onClick = { viewModel.refresh() },
                            isRefreshing = isRefreshing,
                            isManualRefresh = isManualRefresh,
                        )
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
            RwTooltip(tooltip = packStringResource(Res.string.connections_create)) {
                RwFab(
                    onClick = onCreateConnection,
                    modifier = Modifier.testTag("create_connection_fab"),
                ) {
                    Icon(Icons.Default.Add, contentDescription = packStringResource(Res.string.connections_create))
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = Modifier.testTag("connection_list_screen"),
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val resolvedRefreshError = refreshError?.resolve()
            if (resolvedRefreshError != null) {
                RefreshErrorBanner(
                    message = resolvedRefreshError,
                    onDismiss = { viewModel.dismissRefreshError() },
                )
            }

            RwTextField(
                value = searchQuery,
                onValueChange = { viewModel.setSearchQuery(it) },
                placeholder = packStringResource(Res.string.connections_search_placeholder),
                singleLine = true,
                leadingIcon = {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 1200.dp)
                    .padding(horizontal = Spacing.lg, vertical = Spacing.sm)
                    .focusRequester(searchFocusRequester)
                    .testTag("search_field"),
            )
            Row(
                modifier = Modifier
                    .widthIn(max = 1200.dp)
                    .padding(horizontal = Spacing.lg),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                RwChip(
                    selected = typeFilter == null,
                    onClick = { viewModel.setTypeFilter(null) },
                    label = { Text(packStringResource(Res.string.common_all)) },
                    modifier = Modifier.testTag("filter_ALL"),
                )
                for (type in ConnectionType.entries) {
                    RwChip(
                        selected = typeFilter == type,
                        onClick = {
                            viewModel.setTypeFilter(if (typeFilter == type) null else type)
                        },
                        label = { Text(type.displayName()) },
                        modifier = Modifier.testTag("filter_${type.name}"),
                    )
                }
            }
            Spacer(modifier = Modifier.height(Spacing.xs))

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
                                style = AppTypography.body,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(modifier = Modifier.height(Spacing.sm))
                            RwButton(onClick = {
                                viewModel.setSearchQuery("")
                                viewModel.setTypeFilter(null)
                            }, variant = RwButtonVariant.Ghost) {
                                Text(packStringResource(Res.string.common_clear_search))
                            }
                        }
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Outlined.Link,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            )
                            Spacer(modifier = Modifier.height(Spacing.md))
                            Text(
                                text = packStringResource(Res.string.connections_empty_state),
                                style = AppTypography.body,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(modifier = Modifier.height(Spacing.md))
                            RwButton(
                                onClick = onCreateConnection,
                                variant = RwButtonVariant.Primary,
                                modifier = Modifier.testTag("empty_state_create_connection_button"),
                            ) {
                                Text(packStringResource(Res.string.connections_create))
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("connection_list"),
                    contentPadding = PaddingValues(bottom = 80.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    items(connections, key = { it.id.value }) { connection ->
                        Column(modifier = Modifier.widthIn(max = 1200.dp)) {
                            ConnectionListItem(
                                connection = connection,
                                webhookUrl = webhookUrls[connection.id.value],
                                onClick = { onEditConnection(connection.id) },
                                onDelete = { connectionToDelete = connection },
                                onTest = { viewModel.testConnection(connection.id) },
                                isTesting = connection.id in testingConnectionIds,
                            )
                            RwInlineConfirmation(
                                visible = connectionToDelete?.id == connection.id,
                                message = packStringResource(Res.string.connections_delete_confirmation, connection.name),
                                confirmLabel = packStringResource(Res.string.common_delete),
                                onConfirm = {
                                    viewModel.deleteConnection(connection.id)
                                    connectionToDelete = null
                                },
                                onDismiss = { connectionToDelete = null },
                                testTag = "delete_connection_confirm_${connection.id.value}",
                                modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.xs),
                            )
                        }
                    }
                    loadMoreItem(pagination, isLoadingMore, onLoadMore = { viewModel.loadMore() })
                }
            }
        }
    }

    // Delete confirmation is now shown inline within the LazyColumn items
    } // ProvideShortcutActions
}

@Composable
private fun ConnectionListItem(
    connection: Connection,
    webhookUrl: String?,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onTest: () -> Unit,
    isTesting: Boolean = false,
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
                style = AppTypography.subheading,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = connection.type.displayName(),
                style = AppTypography.body,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (webhookUrl != null) {
                Text(
                    text = packStringResource(Res.string.connections_webhook_display, webhookUrl),
                    style = AppTypography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("webhook_url_${connection.id.value}"),
                )
            }
        }
        val appColors = LocalAppColors.current
        val badgeColor = when (connection.type) {
            ConnectionType.GITHUB -> appColors.githubAction
            ConnectionType.SLACK -> appColors.slackMessage
            ConnectionType.TEAMCITY -> appColors.teamcityBuild
        }
        RwBadge(
            text = connection.type.displayName(),
            color = badgeColor,
            testTag = "connection_type_badge_${connection.id.value}",
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            RwButton(
                onClick = onTest,
                variant = RwButtonVariant.Ghost,
                enabled = !isTesting,
                modifier = Modifier.testTag("test_connection_${connection.id.value}"),
            ) {
                if (isTesting) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Text(packStringResource(Res.string.connections_test))
                }
            }
            RwButton(
                onClick = onDelete,
                variant = RwButtonVariant.Danger,
                modifier = Modifier.testTag("delete_connection_btn_${connection.id.value}"),
            ) {
                Text(packStringResource(Res.string.common_delete))
            }
        }
    }
}
