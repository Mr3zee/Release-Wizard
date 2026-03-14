package com.github.mr3zee.navigation

import com.github.mr3zee.model.ConnectionId
import com.github.mr3zee.model.ProjectId
import com.github.mr3zee.model.ReleaseId
import com.github.mr3zee.model.TeamId

sealed class Screen {
    data object ProjectList : Screen()
    data class ProjectEditor(val projectId: ProjectId?) : Screen()
    data object ReleaseList : Screen()
    data class ReleaseView(val releaseId: ReleaseId) : Screen()
    data object ConnectionList : Screen()
    data class ConnectionForm(val connectionId: ConnectionId? = null) : Screen()
    data object TeamList : Screen()
    data class TeamDetail(val teamId: TeamId) : Screen()
    data class TeamManage(val teamId: TeamId) : Screen()
    data object MyInvites : Screen()
    data class AuditLog(val teamId: TeamId) : Screen()
}
