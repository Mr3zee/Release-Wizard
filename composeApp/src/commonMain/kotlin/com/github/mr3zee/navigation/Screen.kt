package com.github.mr3zee.navigation

import com.github.mr3zee.model.ProjectId

sealed class Screen {
    data object ProjectList : Screen()
    data class ProjectEditor(val projectId: ProjectId?) : Screen()
    data object ReleaseList : Screen()
    data class ReleaseView(val releaseId: String) : Screen()
}
