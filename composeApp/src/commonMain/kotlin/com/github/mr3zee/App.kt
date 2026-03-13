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
import com.github.mr3zee.api.createHttpClient
import com.github.mr3zee.auth.AuthViewModel
import com.github.mr3zee.auth.LoginScreen
import com.github.mr3zee.connections.ConnectionsViewModel
import com.github.mr3zee.navigation.AppNavigation
import com.github.mr3zee.navigation.Screen
import com.github.mr3zee.projects.ProjectListViewModel
import com.github.mr3zee.releases.ReleaseListViewModel

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

    val authViewModel = remember { AuthViewModel(authApiClient) }
    val projectListViewModel = remember { ProjectListViewModel(projectApiClient) }
    val connectionsViewModel = remember { ConnectionsViewModel(connectionApiClient) }
    val releaseListViewModel = remember { ReleaseListViewModel(releaseApiClient, projectApiClient) }

    val user by authViewModel.user.collectAsState()
    val isCheckingSession by authViewModel.isCheckingSession.collectAsState()

    LaunchedEffect(Unit) {
        authViewModel.checkSession()
    }

    var currentScreen by remember { mutableStateOf<Screen>(Screen.ProjectList) }

    MaterialTheme {
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
                        onNavigate = { currentScreen = it },
                        projectListViewModel = projectListViewModel,
                        projectApiClient = projectApiClient,
                        releaseApiClient = releaseApiClient,
                        releaseListViewModel = releaseListViewModel,
                        connectionsViewModel = connectionsViewModel,
                        onLogout = {
                            authViewModel.logout()
                            currentScreen = Screen.ProjectList
                        },
                    )
                }
            }
        }
    }
}
