package com.github.mr3zee.navigation

import androidx.compose.runtime.*
import com.github.mr3zee.api.AuthApiClient
import com.github.mr3zee.api.ConnectionApiClient
import com.github.mr3zee.api.MavenTriggerApiClient
import com.github.mr3zee.api.ProjectApiClient
import com.github.mr3zee.api.ReleaseApiClient
import com.github.mr3zee.api.ScheduleApiClient
import com.github.mr3zee.api.TeamApiClient
import com.github.mr3zee.api.UserTeamInfo
import com.github.mr3zee.api.WebhookTriggerApiClient
import com.github.mr3zee.automation.ProjectAutomationScreen
import com.github.mr3zee.automation.ProjectAutomationViewModel
import com.github.mr3zee.connections.ConnectionFormScreen
import com.github.mr3zee.connections.ConnectionListScreen
import com.github.mr3zee.connections.ConnectionsViewModel
import com.github.mr3zee.editor.DagEditorScreen
import com.github.mr3zee.editor.DagEditorViewModel
import com.github.mr3zee.model.TeamId
import com.github.mr3zee.model.TeamRole
import com.github.mr3zee.profile.AdminUsersScreen
import com.github.mr3zee.profile.AdminUsersViewModel
import com.github.mr3zee.profile.ProfileScreen
import com.github.mr3zee.profile.ProfileViewModel
import com.github.mr3zee.projects.ProjectListScreen
import com.github.mr3zee.projects.ProjectListViewModel
import com.github.mr3zee.releases.*
import com.github.mr3zee.teams.*

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
    connectionApiClient: ConnectionApiClient,
    teamApiClient: TeamApiClient,
    scheduleApiClient: ScheduleApiClient,
    webhookTriggerApiClient: WebhookTriggerApiClient,
    mavenTriggerApiClient: MavenTriggerApiClient,
    userTeams: List<UserTeamInfo>,
    currentUserId: String? = null,
    currentUserRole: com.github.mr3zee.model.UserRole? = null,
    onLogout: () -> Unit,
    onAccountDeleted: () -> Unit = onLogout,
    onTeamChanged: (TeamId) -> Unit,
    onRefreshUser: () -> Unit,
    profileViewModel: ProfileViewModel? = null,
    authApiClient: AuthApiClient? = null,
) {
    val isTeamLead = userTeams.any { it.role == TeamRole.TEAM_LEAD }
    // Track teams created in this session so the UI shows "Member" immediately,
    // without waiting for the async checkSession/user-refresh round-trip.
    var localCreatedTeamIds by remember { mutableStateOf(emptySet<TeamId>()) }

    when (currentScreen) {
        is Screen.ProjectList -> ProjectListScreen(
            viewModel = projectListViewModel,
            onEditProject = { onNavigate(Screen.ProjectEditor(projectId = it)) },
            isTeamLead = isTeamLead,
        )
        is Screen.ProjectEditor -> {
            val projectId = currentScreen.projectId
            if (projectId != null) {
                val canForceUnlock = currentUserRole == com.github.mr3zee.model.UserRole.ADMIN ||
                    userTeams.any { it.role == TeamRole.TEAM_LEAD }
                val viewModel = remember(projectId) {
                    DagEditorViewModel(
                        projectId = projectId,
                        apiClient = projectApiClient,
                        connectionApiClient = connectionApiClient,
                        currentUserId = currentUserId,
                        canForceUnlock = canForceUnlock,
                    )
                }

                DisposableEffect(viewModel) {
                    onDispose { viewModel.releaseLock() }
                }

                // DagEditorScreen handles its own Ctrl+S/Z/C/V/A/Delete via onKeyEvent
                DagEditorScreen(
                    viewModel = viewModel,
                    onBack = {
                        projectListViewModel.loadProjects()
                        onGoBack()
                    },
                    onOpenAutomation = {
                        onNavigate(Screen.ProjectAutomation(projectId))
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
        is Screen.ReleaseList -> ReleaseListScreen(
            viewModel = releaseListViewModel,
            onViewRelease = { onNavigate(Screen.ReleaseView(it)) },
            isTeamLead = isTeamLead,
        )
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
            val vmError by viewModel.error.collectAsState()

            ReleaseDetailScreen(
                release = release,
                blockExecutions = blockExecutions,
                isConnected = isConnected,
                reconnectAttempt = reconnectAttempt,
                error = vmError,
                onBack = { onGoBack() },
                onCancel = { viewModel.cancelRelease() },
                onStopRelease = { viewModel.stopRelease() },
                onResumeRelease = { viewModel.resumeRelease() },
                onStopBlock = { viewModel.stopBlock(it) },
                onRerun = {
                    viewModel.rerunRelease { newReleaseId ->
                        onNavigate(Screen.ReleaseView(newReleaseId))
                    }
                },
                onArchive = { viewModel.archiveRelease() },
                onApproveBlock = { viewModel.approveBlock(it) },
                onBlockClick = {},
                onDismissError = { viewModel.dismissError() },
            )
        }
        is Screen.TeamList -> {
            val viewModel = remember { TeamListViewModel(teamApiClient) }
            LaunchedEffect(Unit) { onRefreshUser() }
            TeamListScreen(
                viewModel = viewModel,
                onTeamClick = { onNavigate(Screen.TeamDetail(it)) },
                onTeamCreated = { teamId ->
                    localCreatedTeamIds = localCreatedTeamIds + teamId
                    onTeamChanged(teamId)
                    onRefreshUser()
                    onNavigate(Screen.TeamDetail(teamId))
                },
                onMyInvites = { onNavigate(Screen.MyInvites) },
                memberTeamIds = userTeams.map { it.teamId }.toSet() + localCreatedTeamIds,
                onInviteAccepted = { onRefreshUser() },
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
                onTeamDeleted = { onGoBack(); onGoBack() },
                currentUserId = currentUserId,
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
        is Screen.ProjectAutomation -> {
            val viewModel = remember(currentScreen.projectId) {
                ProjectAutomationViewModel(
                    projectId = currentScreen.projectId,
                    scheduleClient = scheduleApiClient,
                    webhookClient = webhookTriggerApiClient,
                    mavenClient = mavenTriggerApiClient,
                )
            }
            ProjectAutomationScreen(
                viewModel = viewModel,
                onBack = { onGoBack() },
            )
        }
        is Screen.Profile -> {
            val vm = profileViewModel ?: return
            LaunchedEffect(Unit) { vm.loadProfile() }
            ProfileScreen(
                viewModel = vm,
                currentUserRole = currentUserRole,
                onBack = { onGoBack() },
                onNavigateToTeam = { onNavigate(Screen.TeamDetail(it)) },
                onNavigateToAdminUsers = { onNavigate(Screen.AdminUsers) },
                onAccountDeleted = onAccountDeleted,
            )
        }
        is Screen.AdminUsers -> {
            val client = authApiClient ?: return
            val viewModel = remember { AdminUsersViewModel(client) }
            AdminUsersScreen(
                viewModel = viewModel,
                onBack = { onGoBack() },
            )
        }
        is Screen.ResetPassword -> {
            // Handled in App.kt before auth gate, this branch shouldn't be reached
            LaunchedEffect(Unit) { onGoBack() }
        }
    }
}
