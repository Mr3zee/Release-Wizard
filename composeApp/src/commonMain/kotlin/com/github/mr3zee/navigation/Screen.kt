package com.github.mr3zee.navigation

import com.github.mr3zee.model.ConnectionId
import com.github.mr3zee.model.ProjectId
import com.github.mr3zee.model.ReleaseId

sealed class Screen {
    data object ProjectList : Screen()
    data class ProjectEditor(val projectId: ProjectId?) : Screen()
    data object ReleaseList : Screen()
    data class ReleaseView(val releaseId: ReleaseId) : Screen()
    data object ConnectionList : Screen()
    data class ConnectionForm(val connectionId: ConnectionId? = null) : Screen()
}
