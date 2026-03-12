package com.github.mr3zee.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.github.mr3zee.api.ProjectApiClient
import com.github.mr3zee.connections.ConnectionFormScreen
import com.github.mr3zee.connections.ConnectionListScreen
import com.github.mr3zee.connections.ConnectionsViewModel
import com.github.mr3zee.editor.DagEditorScreen
import com.github.mr3zee.editor.DagEditorViewModel
import com.github.mr3zee.projects.ProjectListScreen
import com.github.mr3zee.projects.ProjectListViewModel

@Composable
fun AppNavigation(
    currentScreen: Screen,
    onNavigate: (Screen) -> Unit,
    projectListViewModel: ProjectListViewModel,
    projectApiClient: ProjectApiClient,
    connectionsViewModel: ConnectionsViewModel,
    onLogout: () -> Unit,
) {
    when (currentScreen) {
        is Screen.ProjectList -> ProjectListScreen(
            viewModel = projectListViewModel,
            onCreateProject = { onNavigate(Screen.ProjectEditor(projectId = null)) },
            onEditProject = { onNavigate(Screen.ProjectEditor(projectId = it)) },
            onConnections = { onNavigate(Screen.ConnectionList) },
            onLogout = onLogout,
        )
        is Screen.ProjectEditor -> {
            val projectId = currentScreen.projectId
            if (projectId != null) {
                val viewModel = remember(projectId) {
                    DagEditorViewModel(projectId, projectApiClient)
                }
                DagEditorScreen(
                    viewModel = viewModel,
                    onBack = {
                        projectListViewModel.loadProjects()
                        onNavigate(Screen.ProjectList)
                    },
                )
            } else {
                onNavigate(Screen.ProjectList)
            }
        }
        is Screen.ConnectionList -> ConnectionListScreen(
            viewModel = connectionsViewModel,
            onCreateConnection = { onNavigate(Screen.ConnectionForm) },
            onEditConnection = { /* Phase 3: edit connection by ID */ },
            onBack = { onNavigate(Screen.ProjectList) },
        )
        is Screen.ConnectionForm -> ConnectionFormScreen(
            viewModel = connectionsViewModel,
            onBack = {
                connectionsViewModel.loadConnections()
                onNavigate(Screen.ConnectionList)
            },
        )
        is Screen.ReleaseList -> {
            // Phase 5: Release list
        }
        is Screen.ReleaseView -> {
            // Phase 5: Release view
        }
    }
}
