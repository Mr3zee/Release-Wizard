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
import com.github.mr3zee.theme.ThemePreference

@Composable
fun AppNavigation(
    currentScreen: Screen,
    onNavigate: (Screen) -> Unit,
    onGoBack: () -> Boolean,
    // todo claude: unused
    navController: NavigationController,
    projectListViewModel: ProjectListViewModel,
    projectApiClient: ProjectApiClient,
    releaseApiClient: ReleaseApiClient,
    releaseListViewModel: ReleaseListViewModel,
    connectionsViewModel: ConnectionsViewModel,
    onLogout: () -> Unit,
    themePreference: ThemePreference = ThemePreference.SYSTEM,
    onThemeChange: (ThemePreference) -> Unit = {},
) {
    when (currentScreen) {
        is Screen.ProjectList -> ProjectListScreen(
            viewModel = projectListViewModel,
            onEditProject = { onNavigate(Screen.ProjectEditor(projectId = it)) },
            onConnections = { onNavigate(Screen.ConnectionList) },
            onReleases = { onNavigate(Screen.ReleaseList) },
            onLogout = onLogout,
            themePreference = themePreference,
            onThemeChange = onThemeChange,
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
                        onGoBack()
                    },
                )
            } else {
                LaunchedEffect(Unit) { onGoBack() }
            }
        }
        is Screen.ConnectionList -> ConnectionListScreen(
            viewModel = connectionsViewModel,
            onCreateConnection = { onNavigate(Screen.ConnectionForm()) },
            onEditConnection = { onNavigate(Screen.ConnectionForm(connectionId = it)) },
            onBack = { onGoBack() },
        )
        is Screen.ConnectionForm -> ConnectionFormScreen(
            viewModel = connectionsViewModel,
            connectionId = currentScreen.connectionId,
            onBack = {
                connectionsViewModel.clearEditingConnection()
                connectionsViewModel.loadConnections()
                onGoBack()
            },
        )
        is Screen.ReleaseList -> {
            ReleaseListScreen(
                viewModel = releaseListViewModel,
                onViewRelease = { onNavigate(Screen.ReleaseView(it)) },
                onBack = { onGoBack() },
            )
        }
        is Screen.ReleaseView -> {
            // Navigation uses a plain `when` without crossfade/animation, so switching
            // away fully disposes this branch. On re-entry, `remember` runs fresh,
            // creating a new ViewModel, and `DisposableEffect` fires `connect()` again.
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
                onBack = { onGoBack() },
                onCancel = { viewModel.cancelRelease() },
                onRerun = {
                    viewModel.rerunRelease { newReleaseId ->
                        onNavigate(Screen.ReleaseView(newReleaseId))
                    }
                },
                onArchive = { viewModel.archiveRelease() },
                onApproveBlock = { viewModel.approveBlock(it) },
                onBlockClick = {},
            )
        }
    }
}
