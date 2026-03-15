package com.github.mr3zee.projects

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.github.mr3zee.api.UserTeamInfo
import com.github.mr3zee.components.ListItemCard
import com.github.mr3zee.components.loadMoreItem
import com.github.mr3zee.model.ProjectId
import com.github.mr3zee.model.ProjectTemplate
import com.github.mr3zee.model.TeamId
import com.github.mr3zee.theme.ThemePreference
import kotlinx.coroutines.flow.StateFlow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectListScreen(
    viewModel: ProjectListViewModel,
    onEditProject: (ProjectId) -> Unit,
    onConnections: (() -> Unit)? = null,
    onReleases: (() -> Unit)? = null,
    onTeams: (() -> Unit)? = null,
    onLogout: (() -> Unit)? = null,
    themePreference: ThemePreference = ThemePreference.SYSTEM,
    onThemeChange: (ThemePreference) -> Unit = {},
    activeTeamId: StateFlow<TeamId?>? = null,
    userTeams: List<UserTeamInfo> = emptyList(),
    onTeamChanged: (TeamId) -> Unit = {},
) {
    val projects by viewModel.projects.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val isLoadingMore by viewModel.isLoadingMore.collectAsState()
    val pagination by viewModel.pagination.collectAsState()

    var showCreateDialog by remember { mutableStateOf(false) }
    var projectToDelete by remember { mutableStateOf<ProjectTemplate?>(null) }

    val currentTeamId = activeTeamId?.collectAsState()?.value
    var showTeamPicker by remember { mutableStateOf(false) }
    var showOverflowMenu by remember { mutableStateOf(false) }
    val activeTeamName = userTeams.find { it.teamId == currentTeamId }?.teamName ?: "No Team"

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (userTeams.size > 1) {
                        TextButton(
                            onClick = { showTeamPicker = true },
                            modifier = Modifier.testTag("team_switcher"),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "Projects - $activeTeamName",
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Icon(
                                    Icons.Default.ArrowDropDown,
                                    contentDescription = "Switch team",
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                    } else {
                        Text(
                            "Projects - $activeTeamName",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                actions = {
                    if (onTeams != null) {
                        TextButton(
                            onClick = onTeams,
                            modifier = Modifier.testTag("teams_button"),
                        ) {
                            Text("Teams")
                        }
                    }
                    if (onReleases != null) {
                        TextButton(
                            onClick = onReleases,
                            modifier = Modifier.testTag("releases_button"),
                        ) {
                            Text("Releases")
                        }
                    }
                    if (onConnections != null) {
                        TextButton(
                            onClick = onConnections,
                            modifier = Modifier.testTag("connections_button"),
                        ) {
                            Text("Connections")
                        }
                    }
                    if (onLogout != null) {
                        TextButton(
                            onClick = onLogout,
                            modifier = Modifier.testTag("logout_button"),
                        ) {
                            Text("Logout")
                        }
                    }
                    // Overflow menu for theme toggle
                    Box {
                        IconButton(
                            onClick = { showOverflowMenu = true },
                            modifier = Modifier.testTag("overflow_menu_button"),
                        ) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More options")
                        }
                        DropdownMenu(
                            expanded = showOverflowMenu,
                            onDismissRequest = { showOverflowMenu = false },
                        ) {
                            DropdownMenuItem(
                                text = {
                                    val label = when (themePreference) {
                                        ThemePreference.SYSTEM -> "Theme: Auto"
                                        ThemePreference.LIGHT -> "Theme: Light"
                                        ThemePreference.DARK -> "Theme: Dark"
                                    }
                                    Text(label)
                                },
                                onClick = {
                                    val next = when (themePreference) {
                                        ThemePreference.SYSTEM -> ThemePreference.LIGHT
                                        ThemePreference.LIGHT -> ThemePreference.DARK
                                        ThemePreference.DARK -> ThemePreference.SYSTEM
                                    }
                                    onThemeChange(next)
                                    showOverflowMenu = false
                                },
                                modifier = Modifier.testTag("theme_toggle_menu_item"),
                            )
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showCreateDialog = true },
                modifier = Modifier.testTag("create_project_fab"),
            ) {
                Icon(Icons.Default.Add, contentDescription = "Create project")
            }
        },
        modifier = Modifier.testTag("project_list_screen"),
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.setSearchQuery(it) },
                placeholder = { Text("Search projects...") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .testTag("search_field"),
            )

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
                        Button(onClick = { viewModel.loadProjects() }) {
                            Text("Retry")
                        }
                    }
                }
            } else if (projects.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    if (searchQuery.isNotBlank()) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "No results match your search.",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            TextButton(onClick = { viewModel.setSearchQuery("") }) {
                                Text("Clear search")
                            }
                        }
                    } else {
                        Text(
                            text = "No projects yet. Create one to get started.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("project_list"),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    items(projects, key = { it.id.value }) { project ->
                        ProjectListItem(
                            project = project,
                            onClick = { onEditProject(project.id) },
                            onDelete = { projectToDelete = project },
                            modifier = Modifier.widthIn(max = 900.dp),
                        )
                    }
                    loadMoreItem(pagination, isLoadingMore, onLoadMore = { viewModel.loadMore() })
                }
            }
        }
    }

    if (showCreateDialog) {
        CreateProjectDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { name ->
                viewModel.createProject(name) { projectId ->
                    onEditProject(projectId)
                }
                showCreateDialog = false
            },
        )
    }

    projectToDelete?.let { project ->
        AlertDialog(
            onDismissRequest = { projectToDelete = null },
            title = { Text("Delete Project") },
            text = { Text("Are you sure you want to delete \"${project.name}\"?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteProject(project.id)
                    projectToDelete = null
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { projectToDelete = null }) {
                    Text("Cancel")
                }
            },
        )
    }

    if (showTeamPicker && userTeams.size > 1) {
        AlertDialog(
            onDismissRequest = { showTeamPicker = false },
            title = { Text("Switch Team") },
            text = {
                Column {
                    userTeams.forEach { teamInfo ->
                        TextButton(
                            onClick = {
                                onTeamChanged(teamInfo.teamId)
                                showTeamPicker = false
                            },
                            modifier = Modifier.fillMaxWidth().testTag("team_picker_${teamInfo.teamId.value}"),
                        ) {
                            Text(
                                teamInfo.teamName,
                                color = if (teamInfo.teamId == currentTeamId) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showTeamPicker = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun ProjectListItem(
    project: ProjectTemplate,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ListItemCard(
        onClick = onClick,
        testTag = "project_item_${project.id.value}",
        modifier = modifier,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = project.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (project.description.isNotBlank()) {
                Text(
                    text = project.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = "${project.dagGraph.blocks.size} blocks",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = onDelete) {
            Text("Delete", color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun CreateProjectDialog(
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit,
) {
    var name by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Project") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Project name") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("project_name_input"),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onCreate(name) },
                enabled = name.isNotBlank(),
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}
