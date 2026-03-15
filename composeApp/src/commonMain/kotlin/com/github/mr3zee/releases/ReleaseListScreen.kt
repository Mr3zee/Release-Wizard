package com.github.mr3zee.releases

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
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
import com.github.mr3zee.model.Release
import com.github.mr3zee.model.ReleaseId
import com.github.mr3zee.model.ReleaseStatus
import com.github.mr3zee.model.isTerminal
import com.github.mr3zee.theme.LocalAppColors
import com.github.mr3zee.util.displayName
import com.github.mr3zee.util.resolve
import kotlinx.coroutines.delay
import com.github.mr3zee.i18n.packPluralStringResource
import com.github.mr3zee.i18n.packStringResource
import releasewizard.composeapp.generated.resources.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReleaseListScreen(
    viewModel: ReleaseListViewModel,
    onViewRelease: (ReleaseId) -> Unit,
    onBack: () -> Unit,
) {
    val releases by viewModel.releases.collectAsState()
    val projects by viewModel.projects.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val statusFilter by viewModel.statusFilter.collectAsState()
    val projectFilter by viewModel.projectFilter.collectAsState()
    val isLoadingMore by viewModel.isLoadingMore.collectAsState()
    val pagination by viewModel.pagination.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val isManualRefresh by viewModel.isManualRefresh.collectAsState()
    val refreshError by viewModel.refreshError.collectAsState()
    val lastRefreshedAt by viewModel.lastRefreshedAt.collectAsState()

    var showStartDialog by remember { mutableStateOf(false) }

    // "Updated Xs ago" ticker — store elapsed duration, format in composable scope
    var elapsed by remember { mutableStateOf<kotlin.time.Duration?>(null) }
    LaunchedEffect(lastRefreshedAt) {
        val mark = lastRefreshedAt
        if (mark == null) {
            elapsed = null
            return@LaunchedEffect
        }
        while (true) {
            elapsed = mark.elapsedNow()
            delay(1000)
        }
    }
    val relativeTimeText = elapsed?.let { dur ->
        when {
            dur.inWholeSeconds < 5 -> packStringResource(Res.string.releases_updated_just_now)
            dur.inWholeSeconds < 60 -> {
                val seconds = dur.inWholeSeconds.toInt()
                packPluralStringResource(Res.plurals.releases_updated_seconds_ago, seconds, seconds)
            }
            dur.inWholeMinutes < 60 -> {
                val minutes = dur.inWholeMinutes.toInt()
                packPluralStringResource(Res.plurals.releases_updated_minutes_ago, minutes, minutes)
            }
            else -> {
                val hours = dur.inWholeHours.toInt()
                packPluralStringResource(Res.plurals.releases_updated_hours_ago, hours, hours)
            }
        }
    }

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

    DisposableEffect(Unit) {
        viewModel.setActive(true)
        onDispose { viewModel.setActive(false) }
    }

    LaunchedEffect(Unit) {
        viewModel.loadProjects()
    }

    Scaffold(
        topBar = {
            Box {
                TopAppBar(
                    title = {
                        Column {
                            Text(packStringResource(Res.string.releases_title))
                            if (relativeTimeText != null) {
                                Text(
                                    text = relativeTimeText ?: "",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.testTag("last_updated_text"),
                                )
                            }
                        }
                    },
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
                onClick = { showStartDialog = true },
                modifier = Modifier.testTag("start_release_fab"),
            ) {
                Icon(Icons.Default.Add, contentDescription = packStringResource(Res.string.releases_start_release))
            }
        },
        modifier = Modifier.testTag("release_list_screen"),
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
                placeholder = { Text(packStringResource(Res.string.releases_search_placeholder)) },
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
                    selected = statusFilter == null,
                    onClick = { viewModel.setStatusFilter(null) },
                    label = { Text(packStringResource(Res.string.common_all)) },
                )
                for (status in listOf(ReleaseStatus.RUNNING, ReleaseStatus.SUCCEEDED, ReleaseStatus.FAILED)) {
                    FilterChip(
                        selected = statusFilter == status,
                        onClick = {
                            viewModel.setStatusFilter(if (statusFilter == status) null else status)
                        },
                        label = { Text(status.displayName()) },
                        modifier = Modifier.testTag("filter_${status.name}"),
                    )
                }
            }
            if (projects.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .widthIn(max = 900.dp)
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = projectFilter == null,
                        onClick = { viewModel.setProjectFilter(null) },
                        label = { Text(packStringResource(Res.string.releases_filter_all_projects)) },
                        modifier = Modifier.testTag("filter_all_projects"),
                    )
                    for (project in projects) {
                        FilterChip(
                            selected = projectFilter == project.id,
                            onClick = {
                                viewModel.setProjectFilter(
                                    if (projectFilter == project.id) null else project.id
                                )
                            },
                            label = { Text(project.name) },
                            modifier = Modifier.testTag("filter_project_${project.id.value}"),
                        )
                    }
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
            } else if (error != null) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = error?.resolve() ?: packStringResource(Res.string.common_unknown_error),
                            color = MaterialTheme.colorScheme.error,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = { viewModel.loadReleases() },
                            modifier = Modifier.testTag("retry_button"),
                        ) {
                            Text(packStringResource(Res.string.common_retry))
                        }
                    }
                }
            } else if (releases.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    if (searchQuery.isNotBlank() || statusFilter != null || projectFilter != null) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = packStringResource(Res.string.common_no_search_results),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            TextButton(onClick = {
                                viewModel.setSearchQuery("")
                                viewModel.setStatusFilter(null)
                                viewModel.setProjectFilter(null)
                            }) {
                                Text(packStringResource(Res.string.common_clear_search))
                            }
                        }
                    } else {
                        Text(
                            text = packStringResource(Res.string.releases_empty_state),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.testTag("empty_state"),
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("release_list"),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    items(releases, key = { it.id.value }) { release ->
                        ReleaseListItem(
                            release = release,
                            onClick = { onViewRelease(release.id) },
                            onArchive = { viewModel.archiveRelease(release.id) },
                            onDelete = { viewModel.deleteRelease(release.id) },
                            modifier = Modifier.widthIn(max = 900.dp),
                        )
                    }
                    loadMoreItem(pagination, isLoadingMore, onLoadMore = { viewModel.loadMore() })
                }
            }
        }
    }

    if (showStartDialog) {
        StartReleaseDialog(
            projects = projects,
            onStart = { projectId ->
                viewModel.startRelease(projectId)
                showStartDialog = false
            },
            onDismiss = { showStartDialog = false },
        )
    }
}

@Composable
private fun ReleaseListItem(
    release: Release,
    onClick: () -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showMenu by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showArchiveConfirm by remember { mutableStateOf(false) }

    ListItemCard(
        onClick = onClick,
        testTag = "release_item_${release.id.value}",
        modifier = modifier,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = packStringResource(Res.string.releases_release_title, release.id.value.take(8)),
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = packStringResource(Res.string.releases_project_label, release.projectTemplateId.value.take(8)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (release.startedAt != null) {
                Text(
                    text = packStringResource(Res.string.releases_started_label, release.startedAt.toString()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        StatusBadge(release.status)
        if (release.status.isTerminal) {
            Box {
                IconButton(
                    onClick = { showMenu = true },
                    modifier = Modifier.testTag("release_menu_${release.id.value}"),
                ) {
                    Icon(Icons.Default.MoreVert, contentDescription = packStringResource(Res.string.common_more_options))
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                ) {
                    if (release.status != ReleaseStatus.ARCHIVED) {
                        DropdownMenuItem(
                            text = { Text(packStringResource(Res.string.releases_archive)) },
                            onClick = {
                                showMenu = false
                                showArchiveConfirm = true
                            },
                            modifier = Modifier.testTag("archive_menu_item"),
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(packStringResource(Res.string.common_delete), color = MaterialTheme.colorScheme.error) },
                        onClick = {
                            showMenu = false
                            showDeleteConfirm = true
                        },
                        modifier = Modifier.testTag("delete_menu_item"),
                    )
                }
            }
        }
    }

    if (showArchiveConfirm) {
        AlertDialog(
            onDismissRequest = { showArchiveConfirm = false },
            title = { Text(packStringResource(Res.string.releases_archive_title)) },
            text = { Text(packStringResource(Res.string.releases_archive_confirmation)) },
            confirmButton = {
                TextButton(onClick = { showArchiveConfirm = false; onArchive() }) {
                    Text(packStringResource(Res.string.releases_archive))
                }
            },
            dismissButton = {
                TextButton(onClick = { showArchiveConfirm = false }) {
                    Text(packStringResource(Res.string.common_cancel))
                }
            },
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(packStringResource(Res.string.releases_delete_title)) },
            text = { Text(packStringResource(Res.string.releases_delete_confirmation)) },
            confirmButton = {
                TextButton(onClick = { showDeleteConfirm = false; onDelete() }) {
                    Text(packStringResource(Res.string.common_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(packStringResource(Res.string.common_cancel))
                }
            },
        )
    }
}

@Composable
internal fun StatusBadge(status: ReleaseStatus) {
    val appColors = LocalAppColors.current
    val color = when (status) {
        ReleaseStatus.PENDING -> appColors.statusPending
        ReleaseStatus.RUNNING -> appColors.statusRunning
        ReleaseStatus.SUCCEEDED -> appColors.statusSuccess
        ReleaseStatus.FAILED -> appColors.statusFailed
        ReleaseStatus.CANCELLED -> appColors.statusCancelled
        ReleaseStatus.ARCHIVED -> appColors.statusArchived
    }
    val label = status.displayName()
    Surface(
        color = color.copy(alpha = 0.15f),
        shape = RoundedCornerShape(50),
        modifier = Modifier.testTag("status_badge_${status.name}"),
    ) {
        Text(
            text = label,
            color = color,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}
