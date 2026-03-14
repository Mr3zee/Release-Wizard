package com.github.mr3zee.navigation

import androidx.compose.runtime.*
import com.github.mr3zee.api.ProjectApiClient
import com.github.mr3zee.api.ReleaseApiClient
import com.github.mr3zee.api.TeamApiClient
import com.github.mr3zee.api.UserTeamInfo
import com.github.mr3zee.connections.ConnectionFormScreen
import com.github.mr3zee.connections.ConnectionListScreen
import com.github.mr3zee.connections.ConnectionsViewModel
import com.github.mr3zee.editor.DagEditorScreen
import com.github.mr3zee.editor.DagEditorViewModel
import com.github.mr3zee.model.TeamId
import com.github.mr3zee.model.TeamRole
import com.github.mr3zee.projects.ProjectListScreen
import com.github.mr3zee.projects.ProjectListViewModel
import com.github.mr3zee.releases.*
import com.github.mr3zee.teams.*
import com.github.mr3zee.theme.ThemePreference
import kotlinx.coroutines.flow.StateFlow

@Composable
fun AppNavigation(
    currentScreen: Screen,
    onNavigate: (Screen) -> Unit,
    onGoBack: () -> Boolean,
    projectListViewModel: ProjectListViewModel,
    projectApiClient: ProjectApiClient,
    releaseApiClient: ReleaseApiClient,
    releaseListViewModel: ReleaseListViewModel,
    connectionsViewModel: ConnectionsViewModel,
    teamApiClient: TeamApiClient,
    activeTeamId: StateFlow<TeamId?>,
    userTeams: List<UserTeamInfo>,
    onLogout: () -> Unit,
    onTeamChanged: (TeamId) -> Unit,
    onRefreshUser: () -> Unit,
    themePreference: ThemePreference = ThemePreference.SYSTEM,
    onThemeChange: (ThemePreference) -> Unit = {},
) {
    when (currentScreen) {
        is Screen.ProjectList -> ProjectListScreen(
            viewModel = projectListViewModel,
            onEditProject = { onNavigate(Screen.ProjectEditor(projectId = it)) },
            onConnections = { onNavigate(Screen.ConnectionList) },
            onReleases = { onNavigate(Screen.ReleaseList) },
            onTeams = { onNavigate(Screen.TeamList) },
            onLogout = onLogout,
            themePreference = themePreference,
            onThemeChange = onThemeChange,
            activeTeamId = activeTeamId,
            userTeams = userTeams,
            onTeamChanged = onTeamChanged,
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
        is Screen.TeamList -> {
            val viewModel = remember { TeamListViewModel(teamApiClient) }
            TeamListScreen(
                viewModel = viewModel,
                onTeamClick = { onNavigate(Screen.TeamDetail(it)) },
                onTeamCreated = { teamId ->
                    onTeamChanged(teamId)
                    onRefreshUser()
                    onNavigate(Screen.ProjectList)
                },
                onMyInvites = { onNavigate(Screen.MyInvites) },
                onBack = { onGoBack() },
                memberTeamIds = userTeams.map { it.teamId }.toSet(),
            )
        }
        is Screen.TeamDetail -> {
            val viewModel = remember(currentScreen.teamId) {
                TeamDetailViewModel(currentScreen.teamId, teamApiClient)
            }
            val isTeamLead = userTeams.any { it.teamId == currentScreen.teamId && it.role == TeamRole.TEAM_LEAD }
            TeamDetailScreen(
                viewModel = viewModel,
                onManage = { onNavigate(Screen.TeamManage(currentScreen.teamId)) },
                onAuditLog = { onNavigate(Screen.AuditLog(currentScreen.teamId)) },
                onBack = { onGoBack() },
                isTeamLead = isTeamLead,
            )
        }
        is Screen.TeamManage -> {
            val viewModel = remember(currentScreen.teamId) {
                TeamManageViewModel(currentScreen.teamId, teamApiClient)
            }
            TeamManageScreen(
                viewModel = viewModel,
                onBack = { onGoBack() },
            )
        }
        is Screen.MyInvites -> {
            val viewModel = remember { MyInvitesViewModel(teamApiClient) }
            MyInvitesScreen(
                viewModel = viewModel,
                onBack = { onGoBack() },
                onInviteAccepted = {
                    onRefreshUser()
                },
            )
        }
        is Screen.AuditLog -> {
            val viewModel = remember(currentScreen.teamId) {
                AuditLogViewModel(currentScreen.teamId, teamApiClient)
            }
            AuditLogScreen(
                viewModel = viewModel,
                onBack = { onGoBack() },
            )
        }
    }
}
