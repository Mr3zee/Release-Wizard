package com.github.mr3zee.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.github.mr3zee.api.ProjectApiClient
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
) {
    when (currentScreen) {
        is Screen.ProjectList -> ProjectListScreen(
            viewModel = projectListViewModel,
            onCreateProject = { onNavigate(Screen.ProjectEditor(projectId = null)) },
            onEditProject = { onNavigate(Screen.ProjectEditor(projectId = it)) },
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
                // New project — go back for now (create via list screen)
                onNavigate(Screen.ProjectList)
            }
        }
        is Screen.ReleaseList -> {
            // Phase 5: Release list
        }
        is Screen.ReleaseView -> {
            // Phase 5: Release view
        }
    }
}
