package com.github.mr3zee.navigation

import androidx.compose.runtime.Composable
import com.github.mr3zee.projects.ProjectListScreen
import com.github.mr3zee.projects.ProjectListViewModel

@Composable
fun AppNavigation(
    currentScreen: Screen,
    onNavigate: (Screen) -> Unit,
    projectListViewModel: ProjectListViewModel,
) {
    when (currentScreen) {
        is Screen.ProjectList -> ProjectListScreen(
            viewModel = projectListViewModel,
            onCreateProject = { onNavigate(Screen.ProjectEditor(projectId = null)) },
            onEditProject = { onNavigate(Screen.ProjectEditor(projectId = it)) },
        )
        is Screen.ProjectEditor -> {
            // Phase 2: DAG editor
        }
        is Screen.ReleaseList -> {
            // Phase 5: Release list
        }
        is Screen.ReleaseView -> {
            // Phase 5: Release view
        }
    }
}
