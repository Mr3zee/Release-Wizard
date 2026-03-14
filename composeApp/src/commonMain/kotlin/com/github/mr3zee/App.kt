package com.github.mr3zee

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.github.mr3zee.api.AuthApiClient
import com.github.mr3zee.api.ConnectionApiClient
import com.github.mr3zee.api.ProjectApiClient
import com.github.mr3zee.api.ReleaseApiClient
import com.github.mr3zee.api.TeamApiClient
import com.github.mr3zee.api.createHttpClient
import com.github.mr3zee.auth.AuthEventBus
import com.github.mr3zee.auth.AuthEvent
import com.github.mr3zee.auth.AuthViewModel
import com.github.mr3zee.auth.LoginScreen
import com.github.mr3zee.connections.ConnectionsViewModel
import com.github.mr3zee.navigation.AppNavigation
import com.github.mr3zee.navigation.NavigationController
import com.github.mr3zee.navigation.Screen
import com.github.mr3zee.projects.ProjectListViewModel
import com.github.mr3zee.model.TeamId
import com.github.mr3zee.releases.ReleaseListViewModel
import com.github.mr3zee.theme.AppTheme
import com.github.mr3zee.theme.ThemePreference
import com.github.mr3zee.theme.loadThemePreference
import com.github.mr3zee.theme.saveThemePreference
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

@Composable
fun App() {
    val httpClient = remember { createHttpClient() }
    DisposableEffect(Unit) {
        onDispose { httpClient.close() }
    }
    val authApiClient = remember { AuthApiClient(httpClient) }
    val projectApiClient = remember { ProjectApiClient(httpClient) }
    val connectionApiClient = remember { ConnectionApiClient(httpClient) }
    val releaseApiClient = remember { ReleaseApiClient(httpClient) }
    val teamApiClient = remember { TeamApiClient(httpClient) }

    val activeTeamId = remember { MutableStateFlow<TeamId?>(null) }

    val authViewModel = remember { AuthViewModel(authApiClient) }
    val projectListViewModel = remember { ProjectListViewModel(projectApiClient, activeTeamId) }
    val connectionsViewModel = remember { ConnectionsViewModel(connectionApiClient, activeTeamId) }
    val releaseListViewModel = remember { ReleaseListViewModel(releaseApiClient, projectApiClient, activeTeamId) }

    val user by authViewModel.user.collectAsState()
    val isCheckingSession by authViewModel.isCheckingSession.collectAsState()

    var themePreference by remember { mutableStateOf(loadThemePreference()) }

    val navController = remember { NavigationController() }
    val currentScreen = navController.currentScreen

    // Auto-select team when user info changes, or redirect to team list if no teams
    LaunchedEffect(user) {
        val currentUser = user
        if (currentUser != null) {
            if (currentUser.teams.isNotEmpty()) {
                if (activeTeamId.value == null) {
                    activeTeamId.value = currentUser.teams.first().teamId
                }
            } else {
                navController.resetTo(Screen.TeamList)
            }
        }
    }

    LaunchedEffect(Unit) {
        authViewModel.checkSession()
    }

    LaunchedEffect(Unit) {
        AuthEventBus.events.collect { event ->
            when (event) {
                is AuthEvent.SessionExpired -> {
                    authViewModel.onSessionExpired()
                    activeTeamId.value = null
                    navController.resetTo(Screen.ProjectList)
                }
            }
        }
    }

    AppTheme(themePreference = themePreference) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            when {
                isCheckingSession -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }
                user == null -> {
                    LoginScreen(viewModel = authViewModel)
                }
                else -> {
                    AppNavigation(
                        currentScreen = currentScreen,
                        onNavigate = { navController.navigate(it) },
                        onGoBack = { navController.goBack() },
                        projectListViewModel = projectListViewModel,
                        projectApiClient = projectApiClient,
                        releaseApiClient = releaseApiClient,
                        releaseListViewModel = releaseListViewModel,
                        connectionsViewModel = connectionsViewModel,
                        teamApiClient = teamApiClient,
                        activeTeamId = activeTeamId,
                        userTeams = user?.teams ?: emptyList(),
                        onLogout = {
                            authViewModel.logout()
                            activeTeamId.value = null
                            navController.resetTo(Screen.ProjectList)
                        },
                        onTeamChanged = { teamId ->
                            activeTeamId.value = teamId
                            // ViewModels auto-reload when activeTeamId changes
                        },
                        onRefreshUser = { authViewModel.checkSession() },
                        themePreference = themePreference,
                        onThemeChange = {
                            themePreference = it
                            saveThemePreference(it)
                        },
                    )
                }
            }
        }
    }
}
