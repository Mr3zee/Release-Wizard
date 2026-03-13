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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.github.mr3zee.model.Release
import com.github.mr3zee.model.ReleaseId
import com.github.mr3zee.model.ReleaseStatus

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

    var showStartDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.loadReleases()
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
                        )
                    }
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
) {
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
        }
    }
}

@Composable
internal fun StatusBadge(status: ReleaseStatus) {
    val (color, label) = when (status) {
        ReleaseStatus.PENDING -> Color(0xFF9CA3AF) to "Pending"
        ReleaseStatus.RUNNING -> Color(0xFF3B82F6) to "Running"
        ReleaseStatus.SUCCEEDED -> Color(0xFF22C55E) to "Succeeded"
        ReleaseStatus.FAILED -> Color(0xFFEF4444) to "Failed"
        ReleaseStatus.CANCELLED -> Color(0xFFF97316) to "Cancelled"
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
