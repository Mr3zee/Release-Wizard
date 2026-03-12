package com.github.mr3zee

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.github.mr3zee.api.ProjectApiClient
import com.github.mr3zee.api.createHttpClient
import com.github.mr3zee.navigation.AppNavigation
import com.github.mr3zee.navigation.Screen
import com.github.mr3zee.projects.ProjectListViewModel

@Composable
fun App() {
    val httpClient = remember { createHttpClient() }
    DisposableEffect(Unit) {
        onDispose { httpClient.close() }
    }
    val projectApiClient = remember { ProjectApiClient(httpClient) }
    val projectListViewModel = remember { ProjectListViewModel(projectApiClient) }

    var currentScreen by remember { mutableStateOf<Screen>(Screen.ProjectList) }

    MaterialTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            AppNavigation(
                currentScreen = currentScreen,
                onNavigate = { currentScreen = it },
                projectListViewModel = projectListViewModel,
            )
        }
    }
}
