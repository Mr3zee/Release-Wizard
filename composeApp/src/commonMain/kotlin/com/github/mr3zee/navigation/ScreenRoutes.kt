package com.github.mr3zee.navigation

import com.github.mr3zee.model.ConnectionId
import com.github.mr3zee.model.ProjectId
import com.github.mr3zee.model.ReleaseId
import com.github.mr3zee.model.TeamId

/** Converts a [Screen] to its URL path for browser location sync. */
fun Screen.toUrlPath(): String = when (this) {
    Screen.ProjectList -> "/projects"
    is Screen.ProjectEditor -> {
        val id = projectId?.value ?: "new"
        "/projects/$id/edit"
    }
    is Screen.ProjectAutomation -> "/projects/${projectId.value}/automation"
    Screen.ReleaseList -> "/releases"
    is Screen.ReleaseView -> "/releases/${releaseId.value}"
    Screen.ConnectionList -> "/connections"
    is Screen.ConnectionForm -> {
        if (connectionId != null) "/connections/${connectionId.value}/edit"
        else "/connections/new"
    }
    Screen.TeamList -> "/teams"
    is Screen.TeamDetail -> "/teams/${teamId.value}"
    is Screen.TeamManage -> "/teams/${teamId.value}/manage"
    Screen.MyInvites -> "/teams/my-invites"
    is Screen.AuditLog -> "/teams/${teamId.value}/audit"
}

/**
 * Parses a URL path into a [Screen], or returns null if the path is not recognized.
 * Handles trailing slashes.
 */
fun parseUrlPath(path: String): Screen? {
    val normalized = path.trimEnd('/')
    if (normalized.isEmpty() || normalized == "/") return Screen.ProjectList

    val segments = normalized.removePrefix("/").split("/")
    return when {
        // /projects
        segments == listOf("projects") -> Screen.ProjectList
        // /projects/{id}/edit
        segments.size == 3 && segments[0] == "projects" && segments[2] == "edit" -> {
            val id = segments[1]
            if (id == "new") Screen.ProjectEditor(projectId = null)
            else Screen.ProjectEditor(projectId = ProjectId(id))
        }
        // /projects/{id}/automation
        segments.size == 3 && segments[0] == "projects" && segments[2] == "automation" -> {
            Screen.ProjectAutomation(projectId = ProjectId(segments[1]))
        }
        // /releases
        segments == listOf("releases") -> Screen.ReleaseList
        // /releases/{id}
        segments.size == 2 && segments[0] == "releases" -> {
            Screen.ReleaseView(releaseId = ReleaseId(segments[1]))
        }
        // /connections
        segments == listOf("connections") -> Screen.ConnectionList
        // /connections/new
        segments == listOf("connections", "new") -> Screen.ConnectionForm(connectionId = null)
        // /connections/{id}/edit
        segments.size == 3 && segments[0] == "connections" && segments[2] == "edit" -> {
            Screen.ConnectionForm(connectionId = ConnectionId(segments[1]))
        }
        // /teams
        segments == listOf("teams") -> Screen.TeamList
        // /teams/my-invites
        segments == listOf("teams", "my-invites") -> Screen.MyInvites
        // /teams/{id}/manage
        segments.size == 3 && segments[0] == "teams" && segments[2] == "manage" -> {
            Screen.TeamManage(teamId = TeamId(segments[1]))
        }
        // /teams/{id}/audit
        segments.size == 3 && segments[0] == "teams" && segments[2] == "audit" -> {
            Screen.AuditLog(teamId = TeamId(segments[1]))
        }
        // /teams/{id}
        segments.size == 2 && segments[0] == "teams" -> {
            Screen.TeamDetail(teamId = TeamId(segments[1]))
        }
        else -> null
    }
}
