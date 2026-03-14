package com.github.mr3zee.api

object ApiRoutes {
    const val API_V1 = "/api/v1"

    object Projects {
        const val BASE = "$API_V1/projects"
        fun byId(id: String) = "$BASE/$id"
    }

    object Releases {
        const val BASE = "$API_V1/releases"
        fun byId(id: String) = "$BASE/$id"
        fun cancel(id: String) = "$BASE/$id/cancel"
        fun rerun(id: String) = "$BASE/$id/rerun"
        fun archive(id: String) = "$BASE/$id/archive"
        fun await(id: String) = "$BASE/$id/await"
        fun blockExecution(releaseId: String, blockId: String) = "$BASE/$releaseId/blocks/$blockId"
        fun restartBlock(releaseId: String, blockId: String) = "${blockExecution(releaseId, blockId)}/restart"
        fun approveBlock(releaseId: String, blockId: String) = "${blockExecution(releaseId, blockId)}/approve"
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
    }

    object Connections {
        const val BASE = "$API_V1/connections"
        fun byId(id: String) = "$BASE/$id"
        fun test(id: String) = "$BASE/$id/test"
    }

    object Notifications {
        fun byProject(projectId: String) = "$API_V1/projects/$projectId/notifications"
        fun byId(projectId: String, notificationId: String) = "${byProject(projectId)}/$notificationId"
    }

    object Webhooks {
        const val BASE = "$API_V1/webhooks"
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

    object Tags {
        const val BASE = "$API_V1/tags"
        fun byName(name: String) = "$BASE/$name"
    }

    const val HEALTH = "/health"
}
