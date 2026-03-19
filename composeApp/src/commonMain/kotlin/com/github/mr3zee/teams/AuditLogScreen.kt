package com.github.mr3zee.teams

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.github.mr3zee.components.RefreshErrorBanner
import com.github.mr3zee.components.RefreshIconButton
import com.github.mr3zee.components.RwButton
import com.github.mr3zee.components.RwButtonVariant
import com.github.mr3zee.components.RwCard
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
    val hasMore by viewModel.hasMore.collectAsState()
    val isLoadingMore by viewModel.isLoadingMore.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val isManualRefresh by viewModel.isManualRefresh.collectAsState()
    val refreshError by viewModel.refreshError.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    val dismissLabel = packStringResource(Res.string.common_dismiss)
    val resolvedError = error?.resolve()

    // Show errors via snackbar with dismiss
    LaunchedEffect(error) {
        val msg = resolvedError ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(
            message = msg,
            actionLabel = dismissLabel,
            duration = SnackbarDuration.Long,
        )
        viewModel.dismissError()
    }

    Scaffold(
        topBar = {
            Box {
                TopAppBar(
                    title = { Text(packStringResource(Res.string.teams_audit_log_title)) },
                    navigationIcon = {
                        RwButton(onClick = onBack, variant = RwButtonVariant.Ghost, modifier = Modifier.testTag("back_button")) {
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
                    CircularProgressIndicator()
                }
            } else if (events.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize().weight(1f), contentAlignment = Alignment.Center) {
                    Text(
                        packStringResource(Res.string.teams_no_audit_events),
                        style = AppTypography.body,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().weight(1f).testTag("audit_event_list"),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    items(events, key = { it.id }) { event ->
                        AuditEventItem(
                            event = event,
                            modifier = Modifier.widthIn(max = 1200.dp),
                        )
                    }
                    if (hasMore) {
                        item {
                            RwButton(
                                onClick = { viewModel.loadMore() },
                                variant = RwButtonVariant.Ghost,
                                enabled = !isLoadingMore,
                                modifier = Modifier.fillMaxWidth().padding(Spacing.sm).testTag("load_more_audit"),
                            ) {
                                if (isLoadingMore) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                } else {
                                    Text(packStringResource(Res.string.teams_load_more))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
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
                    style = AppTypography.heading,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    event.targetType.displayName(),
                    style = AppTypography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
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
