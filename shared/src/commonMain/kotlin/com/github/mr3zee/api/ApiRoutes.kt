package com.github.mr3zee.api

object ApiRoutes {
    const val API_V1 = "/api/v1"

    object Projects {
        const val BASE = "$API_V1/projects"
        fun byId(id: String) = "$BASE/$id"
        fun lock(id: String) = "$BASE/$id/lock"
        fun lockHeartbeat(id: String) = "$BASE/$id/lock/heartbeat"
    }

    object Releases {
        const val BASE = "$API_V1/releases"
        fun byId(id: String) = "$BASE/$id"
        fun cancel(id: String) = "$BASE/$id/cancel"
        fun rerun(id: String) = "$BASE/$id/rerun"
        fun archive(id: String) = "$BASE/$id/archive"
        fun await(id: String) = "$BASE/$id/await"
        fun blockExecution(releaseId: String, blockId: String) = "$BASE/$releaseId/blocks/$blockId"
        fun stop(id: String) = "$BASE/$id/stop"
        fun resume(id: String) = "$BASE/$id/resume"
        fun restartBlock(releaseId: String, blockId: String) = "${blockExecution(releaseId, blockId)}/restart"
        fun approveBlock(releaseId: String, blockId: String) = "${blockExecution(releaseId, blockId)}/approve"
        fun stopBlock(releaseId: String, blockId: String) = "${blockExecution(releaseId, blockId)}/stop"
        fun ws(id: String) = "$BASE/$id/ws"
    }

    object Auth {
        const val LOGIN = "$API_V1/auth/login"
        const val LOGOUT = "$API_V1/auth/logout"
        const val REGISTER = "$API_V1/auth/register"
        const val ME = "$API_V1/auth/me"
        const val USERS = "$API_V1/auth/users"
        fun userById(id: String) = "$USERS/$id"
        fun userRole(id: String) = "$USERS/$id/role"

        object MyInvites {
            const val BASE = "$API_V1/auth/me/invites"
            fun byId(id: String) = "$BASE/$id"
            fun accept(id: String) = "$BASE/$id/accept"
            fun decline(id: String) = "$BASE/$id/decline"
        }
    }

    object Connections {
        const val BASE = "$API_V1/connections"
        fun byId(id: String) = "$BASE/$id"
        fun test(id: String) = "$BASE/$id/test"
        fun teamcityBuildTypes(id: String) = "$BASE/$id/teamcity/build-types"
        fun teamcityBuildTypeParameters(id: String, buildTypeId: String) = "$BASE/$id/teamcity/build-types/$buildTypeId/parameters"
        fun githubWorkflows(id: String) = "$BASE/$id/github/workflows"
        fun githubWorkflowParameters(id: String, workflowFile: String) = "$BASE/$id/github/workflows/$workflowFile/parameters"
    }

    object Teams {
        const val BASE = "$API_V1/teams"
        fun byId(id: String) = "$BASE/$id"

        fun members(teamId: String) = "$BASE/$teamId/members"
        fun member(teamId: String, userId: String) = "$BASE/$teamId/members/$userId"
        fun leave(teamId: String) = "$BASE/$teamId/leave"

        fun invites(teamId: String) = "$BASE/$teamId/invites"
        fun invite(teamId: String, inviteId: String) = "$BASE/$teamId/invites/$inviteId"

        fun joinRequests(teamId: String) = "$BASE/$teamId/join-requests"
        fun approveJoinRequest(teamId: String, requestId: String) = "$BASE/$teamId/join-requests/$requestId/approve"
        fun rejectJoinRequest(teamId: String, requestId: String) = "$BASE/$teamId/join-requests/$requestId/reject"

        fun audit(teamId: String) = "$BASE/$teamId/audit"

        fun tags(teamId: String) = "$BASE/$teamId/tags"
    }

    object Notifications {
        fun byProject(projectId: String) = "$API_V1/projects/$projectId/notifications"
        fun byId(projectId: String, notificationId: String) = "${byProject(projectId)}/$notificationId"
    }

    object Webhooks {
        const val BASE = "$API_V1/webhooks"
        const val STATUS = "$BASE/status"
        fun teamcity(connectionId: String) = "$BASE/teamcity/$connectionId"
        fun github(connectionId: String) = "$BASE/github/$connectionId"
    }

    object Schedules {
        fun byProject(projectId: String) = "$API_V1/projects/$projectId/schedules"
        fun byId(projectId: String, scheduleId: String) = "${byProject(projectId)}/$scheduleId"
    }

    object Triggers {
        fun byProject(projectId: String) = "$API_V1/projects/$projectId/triggers"
        fun byId(projectId: String, triggerId: String) = "${byProject(projectId)}/$triggerId"
        fun webhook(triggerId: String) = "$API_V1/triggers/webhook/$triggerId"
    }

    object MavenTriggers {
        fun byProject(projectId: String) = "$API_V1/projects/$projectId/maven-triggers"
        fun byId(projectId: String, triggerId: String) = "${byProject(projectId)}/$triggerId"
    }

    object Tags {
        const val BASE = "$API_V1/tags"
    }

    const val HEALTH = "/health"
}
