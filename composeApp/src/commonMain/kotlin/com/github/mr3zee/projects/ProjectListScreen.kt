package com.github.mr3zee.projects

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.github.mr3zee.model.ProjectId
import com.github.mr3zee.model.ProjectTemplate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectListScreen(
    viewModel: ProjectListViewModel,
    onEditProject: (ProjectId) -> Unit,
    onConnections: (() -> Unit)? = null,
    onReleases: (() -> Unit)? = null,
    onLogout: (() -> Unit)? = null,
) {
    val projects by viewModel.projects.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadProjects()
    }

    var showCreateDialog by remember { mutableStateOf(false) }
    var projectToDelete by remember { mutableStateOf<ProjectTemplate?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Projects") },
                actions = {
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
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showCreateDialog = true },
                modifier = Modifier.testTag("create_project_fab"),
            ) {
                Text("+")
            }
        },
        modifier = Modifier.testTag("project_list_screen"),
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
                    Text(
                        text = "No projects yet. Create one to get started.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("project_list"),
                ) {
                    items(projects, key = { it.id.value }) { project ->
                        ProjectListItem(
                            project = project,
                            onClick = { onEditProject(project.id) },
                            onDelete = { projectToDelete = project },
                        )
                    }
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
}

@Composable
private fun ProjectListItem(
    project: ProjectTemplate,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable(onClick = onClick)
            .testTag("project_item_${project.id.value}"),
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
                    text = project.name,
                    style = MaterialTheme.typography.titleMedium,
                )
                if (project.description.isNotBlank()) {
                    Text(
                        text = project.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
