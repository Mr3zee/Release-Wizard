package io.github.mr3zee.rwizard

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.mr3zee.rwizard.ui.navigation.NavigationManager
import io.github.mr3zee.rwizard.ui.navigation.Screen
import io.github.mr3zee.rwizard.ui.theme.ReleaseWizardTheme
import io.github.mr3zee.rwizard.ui.screens.*
import org.jetbrains.compose.ui.tooling.preview.Preview

@Composable
@Preview
fun App() {
    ReleaseWizardTheme {
        ReleaseWizardApp()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReleaseWizardApp() {
    val navigationManager = remember { NavigationManager() }
    var currentScreen by remember { mutableStateOf<Screen>(Screen.ProjectList) }
    
    LaunchedEffect(navigationManager) {
        navigationManager.currentScreen.collect {
            currentScreen = it
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Release Wizard",
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (val screen = currentScreen) {
                is Screen.ProjectList -> ProjectListScreen(
                    onNavigateToProject = { projectId ->
                        navigationManager.navigateTo(Screen.ProjectDetail(projectId))
                    },
                    onCreateProject = {
                        navigationManager.navigateTo(Screen.ProjectEditor(null))
                    },
                    onNavigateToConnections = {
                        navigationManager.navigateTo(Screen.ConnectionList)
                    }
                )
                
                is Screen.ProjectDetail -> ProjectDetailScreen(
                    projectId = screen.projectId,
                    onNavigateBack = {
                        navigationManager.navigateBack()
                    },
                    onEditProject = { projectId ->
                        navigationManager.navigateTo(Screen.ProjectEditor(projectId))
                    },
                    onStartRelease = { projectId ->
                        navigationManager.navigateTo(Screen.ReleaseCreation(projectId))
                    }
                )
                
                is Screen.ProjectEditor -> ProjectEditorScreen(
                    projectId = screen.projectId,
                    onNavigateBack = {
                        navigationManager.navigateBack()
                    },
                    onSaveProject = {
                        navigationManager.navigateBack()
                    }
                )
                
                is Screen.ReleaseCreation -> ReleaseCreationScreen(
                    projectId = screen.projectId,
                    onNavigateBack = {
                        navigationManager.navigateBack()
                    },
                    onStartRelease = { releaseId ->
                        navigationManager.navigateTo(Screen.ReleaseMonitor(releaseId))
                    }
                )
                
                is Screen.ReleaseMonitor -> ReleaseMonitorScreen(
                    releaseId = screen.releaseId,
                    onNavigateBack = {
                        navigationManager.navigateBack()
                    }
                )
                
                is Screen.ConnectionList -> ConnectionListScreen(
                    onNavigateBack = {
                        navigationManager.navigateBack()
                    },
                    onCreateConnection = {
                        navigationManager.navigateTo(Screen.ConnectionEditor(null))
                    },
                    onEditConnection = { connectionId ->
                        navigationManager.navigateTo(Screen.ConnectionEditor(connectionId))
                    }
                )
                
                is Screen.ConnectionEditor -> ConnectionEditorScreen(
                    connectionId = screen.connectionId,
                    onNavigateBack = {
                        navigationManager.navigateBack()
                    },
                    onSaveConnection = {
                        navigationManager.navigateBack()
                    }
                )
            }
        }
    }
}
