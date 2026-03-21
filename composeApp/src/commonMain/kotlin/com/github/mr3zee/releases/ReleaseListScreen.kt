package com.github.mr3zee.releases

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.RocketLaunch
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
import com.github.mr3zee.components.RwButton
import com.github.mr3zee.components.RwButtonVariant
import com.github.mr3zee.components.RwChip
import com.github.mr3zee.components.RwFab
import com.github.mr3zee.components.RwIconButton
import com.github.mr3zee.components.RwInlineConfirmation
import com.github.mr3zee.components.RwInlineForm
import com.github.mr3zee.components.RwTextField
import com.github.mr3zee.components.RwTooltip
import com.github.mr3zee.components.loadMoreItem
import com.github.mr3zee.model.Release
import com.github.mr3zee.model.ReleaseId
import com.github.mr3zee.model.ReleaseStatus
import com.github.mr3zee.model.isTerminal
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.github.mr3zee.model.ProjectTemplate
import com.github.mr3zee.theme.AppShapes
import com.github.mr3zee.theme.AppTypography
import com.github.mr3zee.theme.LocalAppColors
import com.github.mr3zee.theme.Spacing
import com.github.mr3zee.util.displayName
import com.github.mr3zee.util.formatTimestamp
import com.github.mr3zee.util.resolve
import kotlinx.coroutines.delay
import com.github.mr3zee.i18n.packPluralStringResource
import com.github.mr3zee.i18n.packStringResource
import com.github.mr3zee.keyboard.ProvideShortcutActions
import com.github.mr3zee.keyboard.ShortcutActions
import releasewizard.composeapp.generated.resources.*
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReleaseListScreen(
    viewModel: ReleaseListViewModel,
    onViewRelease: (ReleaseId) -> Unit,
    onBack: (() -> Unit)? = null,
    isTeamLead: Boolean = false,
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
    var releaseToArchive by remember { mutableStateOf<ReleaseId?>(null) }
    var releaseToDelete by remember { mutableStateOf<ReleaseId?>(null) }
    val searchFocusRequester = remember { FocusRequester() }

    val isDialogOpen = showStartDialog || releaseToArchive != null || releaseToDelete != null
    val shortcutActions = remember(isDialogOpen) {
        ShortcutActions(
            onSearch = { searchFocusRequester.requestFocus() },
            onCreate = { showStartDialog = true },
            onRefresh = { viewModel.refresh() },
            hasDialogOpen = isDialogOpen,
        )
    }
    ProvideShortcutActions(shortcutActions) {

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
            delay(1000.milliseconds)
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
                                    text = relativeTimeText,
                                    style = AppTypography.caption,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.testTag("last_updated_text"),
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        if (onBack != null) {
                            RwButton(
                                onClick = onBack,
                                modifier = Modifier.testTag("back_button"),
                                variant = RwButtonVariant.Ghost,
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = packStringResource(Res.string.common_navigate_back))
                                Text(packStringResource(Res.string.common_back))
                            }
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
            RwTooltip(tooltip = packStringResource(Res.string.releases_start_release)) {
                RwFab(
                    onClick = { showStartDialog = true },
                    modifier = Modifier.testTag("start_release_fab"),
                ) {
                    Icon(Icons.Default.Add, contentDescription = packStringResource(Res.string.releases_start_release))
                }
            }
        },
        modifier = Modifier.testTag("release_list_screen"),
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            horizontalAlignment = Alignment.Start,
        ) {
            val resolvedRefreshError = refreshError?.resolve()
            if (resolvedRefreshError != null) {
                RefreshErrorBanner(
                    message = resolvedRefreshError,
                    onDismiss = { viewModel.dismissRefreshError() },
                )
            }

            // Inline start release form
            StartReleaseInlineForm(
                visible = showStartDialog,
                projects = projects,
                onStart = { projectId ->
                    viewModel.startRelease(projectId) { releaseId -> onViewRelease(releaseId) }
                    showStartDialog = false
                },
                onDismiss = { showStartDialog = false },
            )

            // todo claude: duplicate 19 lines
            RwTextField(
                value = searchQuery,
                onValueChange = { viewModel.setSearchQuery(it) },
                placeholder = packStringResource(Res.string.releases_search_placeholder),
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
            Text(
                packStringResource(Res.string.releases_filter_status_label),
                style = AppTypography.caption,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .widthIn(max = 1200.dp)
                    .padding(horizontal = Spacing.lg)
                    .padding(top = Spacing.sm),
            )
            Row(
                modifier = Modifier
                    .widthIn(max = 1200.dp)
                    .padding(horizontal = Spacing.lg, vertical = Spacing.xs)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                RwChip(
                    selected = statusFilter == null,
                    onClick = { viewModel.setStatusFilter(null) },
                    label = { Text(packStringResource(Res.string.common_all)) },
                    modifier = Modifier.testTag("filter_ALL"),
                )
                for (status in listOf(
                    ReleaseStatus.PENDING,
                    ReleaseStatus.RUNNING,
                    ReleaseStatus.SUCCEEDED,
                    ReleaseStatus.FAILED,
                    ReleaseStatus.STOPPED,
                    ReleaseStatus.CANCELLED,
                    ReleaseStatus.ARCHIVED,
                )) {
                    RwChip(
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
                Text(
                    packStringResource(Res.string.releases_filter_project_label),
                    style = AppTypography.caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .widthIn(max = 1200.dp)
                        .padding(horizontal = Spacing.lg)
                        .padding(top = Spacing.sm),
                )
                Row(
                    modifier = Modifier
                        .widthIn(max = 1200.dp)
                        .padding(horizontal = Spacing.lg, vertical = Spacing.xs)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    RwChip(
                        selected = projectFilter == null,
                        onClick = { viewModel.setProjectFilter(null) },
                        label = { Text(packStringResource(Res.string.releases_filter_all_projects)) },
                        modifier = Modifier.testTag("filter_all_projects"),
                    )
                    for (project in projects) {
                        RwChip(
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
            Spacer(modifier = Modifier.height(Spacing.xs))

            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    val loadingDesc = packStringResource(Res.string.loading_releases)
                    CircularProgressIndicator(modifier = Modifier.semantics { contentDescription = loadingDesc })
                }
            } else if (error != null) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = packStringResource(Res.string.releases_warning_icon),
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                        )
                        Spacer(modifier = Modifier.height(Spacing.md))
                        Text(
                            text = error?.resolve() ?: packStringResource(Res.string.common_unknown_error),
                            color = MaterialTheme.colorScheme.error,
                        )
                        Spacer(modifier = Modifier.height(Spacing.sm))
                        RwButton(
                            onClick = { viewModel.loadReleases() },
                            modifier = Modifier.testTag("retry_button"),
                            variant = RwButtonVariant.Primary,
                        ) {
                            Text(packStringResource(Res.string.common_retry))
                        }
                    }
                }
            } else if (releases.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    if (searchQuery.isNotBlank() || statusFilter != null || projectFilter != null) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(top = 80.dp),
                        ) {
                            // todo claude: duplicate 13 lines
                            Icon(
                                Icons.Default.Search,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            )
                            Spacer(modifier = Modifier.height(Spacing.md))
                            Text(
                                text = packStringResource(Res.string.common_no_search_results),
                                style = AppTypography.body,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(modifier = Modifier.height(Spacing.sm))
                            RwButton(
                                onClick = {
                                    viewModel.setSearchQuery("")
                                    viewModel.setStatusFilter(null)
                                    viewModel.setProjectFilter(null)
                                },
                                variant = RwButtonVariant.Ghost,
                            ) {
                                Text(packStringResource(Res.string.common_clear_search))
                            }
                        }
                    } else {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(top = 80.dp),
                        ) {
                            Icon(
                                Icons.Outlined.RocketLaunch,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            )
                            Spacer(modifier = Modifier.height(Spacing.md))
                            Text(
                                text = packStringResource(Res.string.releases_empty_state),
                                style = AppTypography.body,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.testTag("empty_state"),
                            )
                            Spacer(modifier = Modifier.height(Spacing.md))
                            RwButton(
                                onClick = { showStartDialog = true },
                                variant = RwButtonVariant.Primary,
                                modifier = Modifier.testTag("empty_state_start_release_button"),
                            ) {
                                Text(packStringResource(Res.string.releases_create_release))
                            }
                        }
                    }
                }
            } else {
                val projectNameMap = remember(projects) {
                    projects.associate { it.id to it.name }
                }
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("release_list"),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    contentPadding = PaddingValues(bottom = 80.dp),
                ) {
                    items(releases, key = { it.id.value }) { release ->
                        ReleaseListItem(
                            release = release,
                            projectName = projectNameMap[release.projectTemplateId],
                            onClick = { onViewRelease(release.id) },
                            onArchive = { releaseToArchive = release.id },
                            onDelete = { releaseToDelete = release.id },
                            isTeamLead = isTeamLead,
                            modifier = Modifier.widthIn(max = 1200.dp),
                        )
                        RwInlineConfirmation(
                            visible = releaseToArchive == release.id,
                            message = packStringResource(Res.string.releases_archive_confirmation),
                            confirmLabel = packStringResource(Res.string.releases_archive),
                            onConfirm = {
                                val id = releaseToArchive
                                releaseToArchive = null
                                if (id != null) viewModel.archiveRelease(id)
                            },
                            onDismiss = { releaseToArchive = null },
                            isDestructive = true,
                            testTag = "confirm_archive_${release.id.value}",
                            modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.xs),
                        )
                        RwInlineConfirmation(
                            visible = releaseToDelete == release.id,
                            message = packStringResource(Res.string.releases_delete_confirmation),
                            confirmLabel = packStringResource(Res.string.common_delete),
                            onConfirm = {
                                val id = releaseToDelete
                                releaseToDelete = null
                                if (id != null) viewModel.deleteRelease(id)
                            },
                            onDismiss = { releaseToDelete = null },
                            isDestructive = true,
                            testTag = "confirm_delete_${release.id.value}",
                            modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.xs),
                        )
                    }
                    loadMoreItem(pagination, isLoadingMore, onLoadMore = { viewModel.loadMore() })
                }
            }
        }
    }
    } // ProvideShortcutActions
}

@Composable
private fun StartReleaseInlineForm(
    visible: Boolean,
    projects: List<ProjectTemplate>,
    onStart: (com.github.mr3zee.model.ProjectId) -> Unit,
    onDismiss: () -> Unit,
) {
    var selectedProject by remember { mutableStateOf<ProjectTemplate?>(null) }
    var expanded by remember { mutableStateOf(false) }

    // Reset selection when form becomes visible
    LaunchedEffect(visible) {
        if (visible) {
            selectedProject = null
            expanded = false
        }
    }

    RwInlineForm(
        visible = visible,
        title = packStringResource(Res.string.start_release_title),
        onDismiss = onDismiss,
        onSubmit = { selectedProject?.let { onStart(it.id) } },
        testTag = "start_release_form",
        modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.xs),
        actions = {
            RwButton(
                onClick = { selectedProject?.let { onStart(it.id) } },
                enabled = selectedProject != null,
                modifier = Modifier.testTag("start_release_confirm"),
                variant = RwButtonVariant.Primary,
            ) {
                Text(packStringResource(Res.string.start_release_start))
            }
        },
    ) {
        if (projects.isEmpty()) {
            Text(
                packStringResource(Res.string.start_release_no_projects),
                style = AppTypography.body,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("no_projects_message"),
            )
        } else {
            Box(modifier = Modifier.testTag("project_dropdown")) {
                RwTextField(
                    value = selectedProject?.name ?: "",
                    onValueChange = {},
                    readOnly = true,
                    label = packStringResource(Res.string.start_release_project_label),
                    placeholder = packStringResource(Res.string.start_release_select_project),
                    trailingIcon = {
                        Icon(
                            Icons.Default.ArrowDropDown,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clickable { expanded = !expanded },
                )
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                ) {
                    projects.forEach { project ->
                        DropdownMenuItem(
                            text = { Text(project.name) },
                            onClick = {
                                selectedProject = project
                                expanded = false
                            },
                            modifier = Modifier.testTag("project_option_${project.id.value}"),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReleaseListItem(
    release: Release,
    projectName: String?,
    onClick: () -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit,
    isTeamLead: Boolean = false,
    modifier: Modifier = Modifier,
) {
    var showMenu by remember { mutableStateOf(false) }
    val displayName = projectName ?: packStringResource(Res.string.releases_unknown_project)
    val startedAt = release.startedAt

    ListItemCard(
        onClick = onClick,
        testTag = "release_item_${release.id.value}",
        modifier = modifier,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            RwTooltip(tooltip = packStringResource(Res.string.releases_release_id_tooltip, release.id.value)) {
                Text(
                    text = displayName,
                    style = AppTypography.subheading,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (startedAt != null) {
                Text(
                    text = packStringResource(Res.string.releases_started_label, formatTimestamp(startedAt)),
                    style = AppTypography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        StatusBadge(release.status)
        if (release.status.isTerminal && isTeamLead) {
            Box {
                RwIconButton(
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
                                onArchive()
                            },
                            modifier = Modifier.testTag("archive_menu_item"),
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(packStringResource(Res.string.common_delete), color = MaterialTheme.colorScheme.error) },
                        onClick = {
                            showMenu = false
                            onDelete()
                        },
                        modifier = Modifier.testTag("delete_menu_item"),
                    )
                }
            }
        }
    }
}

@Composable
internal fun StatusBadge(status: ReleaseStatus) {
    val appColors = LocalAppColors.current
    val color = when (status) {
        ReleaseStatus.PENDING -> appColors.statusPending
        ReleaseStatus.RUNNING -> appColors.statusRunning
        ReleaseStatus.STOPPED -> appColors.statusStopped
        ReleaseStatus.SUCCEEDED -> appColors.statusSuccess
        ReleaseStatus.FAILED -> appColors.statusFailed
        ReleaseStatus.CANCELLED -> appColors.statusCancelled
        ReleaseStatus.ARCHIVED -> appColors.statusArchived
    }
    val label = status.displayName()
    Surface(
        color = color.copy(alpha = 0.15f),
        shape = AppShapes.pill,
        modifier = Modifier.testTag("status_badge_${status.name}"),
    ) {
        Text(
            text = label,
            color = color,
            style = AppTypography.caption,
            modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xs),
        )
    }
}
