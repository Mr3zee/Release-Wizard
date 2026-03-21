package com.github.mr3zee.teams

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.github.mr3zee.components.BackRefreshTopBar
import com.github.mr3zee.components.EmptyState
import com.github.mr3zee.components.RefreshErrorBanner
import com.github.mr3zee.components.RwBadge
import com.github.mr3zee.components.RwCard
import com.github.mr3zee.components.loadMoreItem
import com.github.mr3zee.keyboard.ProvideShortcutActions
import com.github.mr3zee.keyboard.ShortcutActions
import com.github.mr3zee.model.AuditEvent
import com.github.mr3zee.theme.AppTypography
import com.github.mr3zee.theme.Spacing
import com.github.mr3zee.util.resolve
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import com.github.mr3zee.i18n.packStringResource
import com.github.mr3zee.util.displayName
import releasewizard.composeapp.generated.resources.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuditLogScreen(
    viewModel: AuditLogViewModel,
    onBack: () -> Unit,
) {
    val events by viewModel.events.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val pagination by viewModel.pagination.collectAsState()
    val isLoadingMore by viewModel.isLoadingMore.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val isManualRefresh by viewModel.isManualRefresh.collectAsState()
    val refreshError by viewModel.refreshError.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    val retryLabel = packStringResource(Res.string.common_retry)
    val resolvedError = error?.resolve()

    // Show errors via snackbar with dismiss
    LaunchedEffect(error) {
        val msg = resolvedError ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(
            message = msg,
            actionLabel = retryLabel,
            duration = SnackbarDuration.Long,
        ).let { result ->
            if (result == SnackbarResult.ActionPerformed) {
                viewModel.refresh()
            }
        }
        viewModel.dismissError()
    }

    val shortcutActions = remember { ShortcutActions(onRefresh = { viewModel.refresh() }) }
    ProvideShortcutActions(shortcutActions) {

    Scaffold(
        topBar = {
            BackRefreshTopBar(
                title = packStringResource(Res.string.teams_audit_log_title),
                onBack = onBack,
                onRefresh = { viewModel.refresh() },
                isRefreshing = isRefreshing,
                isManualRefresh = isManualRefresh,
                isLoading = isLoading,
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = Modifier.testTag("audit_log_screen"),
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            val resolvedRefreshError = refreshError?.resolve()
            if (resolvedRefreshError != null) {
                RefreshErrorBanner(
                    message = resolvedRefreshError,
                    onDismiss = { viewModel.dismissRefreshError() },
                )
            }

            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize().weight(1f), contentAlignment = Alignment.Center) {
                    val loadingDesc = packStringResource(Res.string.loading_audit)
                    CircularProgressIndicator(modifier = Modifier.semantics { contentDescription = loadingDesc })
                }
            } else if (events.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize().weight(1f), contentAlignment = Alignment.Center) {
                    EmptyState(
                        icon = Icons.Outlined.History,
                        message = packStringResource(Res.string.teams_no_audit_events),
                        secondaryMessage = packStringResource(Res.string.teams_audit_empty_hint),
                    )
                }
            } else {
                val listState = rememberLazyListState()
                Box(modifier = Modifier.fillMaxSize().weight(1f)) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().testTag("audit_event_list"),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    contentPadding = PaddingValues(bottom = Spacing.xl),
                ) {
                    items(events, key = { it.id }) { event ->
                        AuditEventItem(
                            event = event,
                            modifier = Modifier.widthIn(max = 1200.dp),
                        )
                    }
                    loadMoreItem(pagination, isLoadingMore, onLoadMore = { viewModel.loadMore() })
                }
                VerticalScrollbar(
                    modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                    adapter = rememberScrollbarAdapter(listState),
                )
                }
            }
        }
    }

    } // ProvideShortcutActions
}

@Composable
private fun AuditEventItem(
    event: AuditEvent,
    modifier: Modifier = Modifier,
) {
    RwCard(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg, vertical = Spacing.xs)
            .testTag("audit_event_${event.id}"),
    ) {
        Column(modifier = Modifier.padding(Spacing.lg)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    event.action.displayName(),
                    style = AppTypography.subheading,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                RwBadge(
                    text = event.targetType.displayName(),
                    color = MaterialTheme.colorScheme.primary,
                    testTag = "audit_category_${event.id}",
                )
            }
            Spacer(modifier = Modifier.height(Spacing.xs))
            val instant = Instant.fromEpochMilliseconds(event.timestamp)
            val dateTime = instant.toLocalDateTime(TimeZone.currentSystemDefault())
            val formatted = "${dateTime.date} ${dateTime.hour.toString().padStart(2, '0')}:${dateTime.minute.toString().padStart(2, '0')}"
            Text(
                packStringResource(Res.string.teams_audit_entry_by, event.actorUsername, formatted),
                style = AppTypography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (event.details.isNotBlank()) {
                Spacer(modifier = Modifier.height(Spacing.xxs))
                Text(
                    event.details,
                    style = AppTypography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
