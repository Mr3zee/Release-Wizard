package com.github.mr3zee.releases

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.github.mr3zee.components.loadMoreItem
import com.github.mr3zee.model.Release
import com.github.mr3zee.model.ReleaseId
import com.github.mr3zee.model.ReleaseStatus
import com.github.mr3zee.model.isTerminal
import com.github.mr3zee.theme.LocalAppColors

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
    val isLoadingMore by viewModel.isLoadingMore.collectAsState()
    val pagination by viewModel.pagination.collectAsState()

    var showStartDialog by remember { mutableStateOf(false) }

    // Initial release load is handled by the ViewModel's init block (debounced search/filter flow).
    // Projects must still be loaded explicitly since they are not part of that flow.
    LaunchedEffect(Unit) {
        viewModel.loadProjects()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Releases") },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text("Back")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showStartDialog = true },
                modifier = Modifier.testTag("start_release_fab"),
            ) {
                Text("+")
            }
        },
        modifier = Modifier.testTag("release_list_screen"),
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.setSearchQuery(it) },
                placeholder = { Text("Search releases...") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .testTag("search_field"),
            )
            Row(
                modifier = Modifier.padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = statusFilter == null,
                    onClick = { viewModel.setStatusFilter(null) },
                    label = { Text("All") },
                )
                for (status in listOf(ReleaseStatus.RUNNING, ReleaseStatus.SUCCEEDED, ReleaseStatus.FAILED)) {
                    FilterChip(
                        selected = statusFilter == status,
                        onClick = {
                            viewModel.setStatusFilter(if (statusFilter == status) null else status)
                        },
                        label = { Text(status.name.lowercase().replaceFirstChar { it.uppercase() }) },
                        modifier = Modifier.testTag("filter_${status.name}"),
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
            } else if (error != null) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = error ?: "Unknown error",
                            color = MaterialTheme.colorScheme.error,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = { viewModel.loadReleases() },
                            modifier = Modifier.testTag("retry_button"),
                        ) {
                            Text("Retry")
                        }
                    }
                }
            } else if (releases.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "No releases yet. Start one to get going.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag("empty_state"),
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("release_list"),
                ) {
                    items(releases, key = { it.id.value }) { release ->
                        ReleaseListItem(
                            release = release,
                            onClick = { onViewRelease(release.id) },
                            onArchive = { viewModel.archiveRelease(release.id) },
                            onDelete = { viewModel.deleteRelease(release.id) },
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
) {
    var showMenu by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable(onClick = onClick)
            .testTag("release_item_${release.id.value}"),
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
                    text = "Release ${release.id.value.take(8)}",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = "Project: ${release.projectTemplateId.value.take(8)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (release.startedAt != null) {
                    Text(
                        text = "Started: ${release.startedAt}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            StatusBadge(release.status)
            if (release.status.isTerminal) {
                Box {
                    TextButton(
                        onClick = { showMenu = true },
                        modifier = Modifier.testTag("release_menu_${release.id.value}"),
                    ) {
                        Text("...")
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                    ) {
                        if (release.status != ReleaseStatus.ARCHIVED) {
                            DropdownMenuItem(
                                text = { Text("Archive") },
                                onClick = {
                                    showMenu = false
                                    onArchive()
                                },
                                modifier = Modifier.testTag("archive_menu_item"),
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
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
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Release") },
            text = { Text("This will permanently delete the release and all its data. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = { showDeleteConfirm = false; onDelete() }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
internal fun StatusBadge(status: ReleaseStatus) {
    val appColors = LocalAppColors.current
    val (color, label) = when (status) {
        ReleaseStatus.PENDING -> appColors.statusPending to "Pending"
        ReleaseStatus.RUNNING -> appColors.statusRunning to "Running"
        ReleaseStatus.SUCCEEDED -> appColors.statusSuccess to "Succeeded"
        ReleaseStatus.FAILED -> appColors.statusFailed to "Failed"
        ReleaseStatus.CANCELLED -> appColors.statusCancelled to "Cancelled"
        ReleaseStatus.ARCHIVED -> appColors.statusArchived to "Archived"
    }
    Surface(
        color = color.copy(alpha = 0.15f),
        shape = RoundedCornerShape(4.dp),
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
