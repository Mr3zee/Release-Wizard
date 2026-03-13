package com.github.mr3zee.navigation

import androidx.compose.runtime.*
import com.github.mr3zee.api.ProjectApiClient
import com.github.mr3zee.api.ReleaseApiClient
import com.github.mr3zee.connections.ConnectionFormScreen
import com.github.mr3zee.connections.ConnectionListScreen
import com.github.mr3zee.connections.ConnectionsViewModel
import com.github.mr3zee.editor.DagEditorScreen
import com.github.mr3zee.editor.DagEditorViewModel
import com.github.mr3zee.projects.ProjectListScreen
import com.github.mr3zee.projects.ProjectListViewModel
import com.github.mr3zee.releases.*

@Composable
fun AppNavigation(
    currentScreen: Screen,
    onNavigate: (Screen) -> Unit,
    projectListViewModel: ProjectListViewModel,
    projectApiClient: ProjectApiClient,
    releaseApiClient: ReleaseApiClient,
    releaseListViewModel: ReleaseListViewModel,
    connectionsViewModel: ConnectionsViewModel,
    onLogout: () -> Unit,
) {
    when (currentScreen) {
        is Screen.ProjectList -> ProjectListScreen(
            viewModel = projectListViewModel,
            onEditProject = { onNavigate(Screen.ProjectEditor(projectId = it)) },
            onConnections = { onNavigate(Screen.ConnectionList) },
            onReleases = { onNavigate(Screen.ReleaseList) },
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
            ReleaseListScreen(
                viewModel = releaseListViewModel,
                onViewRelease = { onNavigate(Screen.ReleaseView(it)) },
                onBack = { onNavigate(Screen.ProjectList) },
            )
        }
        is Screen.ReleaseView -> {
            val viewModel = remember(currentScreen.releaseId) {
                ReleaseDetailViewModel(currentScreen.releaseId, releaseApiClient)
            }

            DisposableEffect(currentScreen.releaseId) {
                viewModel.connect()
                onDispose { viewModel.disconnect() }
            }

            val release by viewModel.release.collectAsState()
            val blockExecutions by viewModel.blockExecutions.collectAsState()
            val isConnected by viewModel.isConnected.collectAsState()
            val reconnectAttempt by viewModel.reconnectAttempt.collectAsState()

            ReleaseDetailScreen(
                release = release,
                blockExecutions = blockExecutions,
                isConnected = isConnected,
                reconnectAttempt = reconnectAttempt,
                onBack = { onNavigate(Screen.ReleaseList) },
                onCancel = { viewModel.cancelRelease() },
                onApproveBlock = { viewModel.approveBlock(it) },
                onBlockClick = {},
            )
        }
    }
}
