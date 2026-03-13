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
        fun await(id: String) = "$BASE/$id/await"
        // todo claude: unused
        fun blockExecution(releaseId: String, blockId: String) = "$BASE/$releaseId/blocks/$blockId"
        // todo claude: unused
        fun restartBlock(releaseId: String, blockId: String) = "$BASE/$releaseId/blocks/$blockId/restart"
        fun approveBlock(releaseId: String, blockId: String) = "$BASE/$releaseId/blocks/$blockId/approve"
        fun ws(id: String) = "$BASE/$id/ws"
    }

    object Auth {
        const val LOGIN = "$API_V1/auth/login"
        const val LOGOUT = "$API_V1/auth/logout"
        const val ME = "$API_V1/auth/me"
    }

    object Connections {
        const val BASE = "$API_V1/connections"
        fun byId(id: String) = "$BASE/$id"
        fun test(id: String) = "$BASE/$id/test"
    }

    object Webhooks {
        const val BASE = "$API_V1/webhooks"
        // todo claude: unused
        fun byTypeAndConnection(type: String, connectionId: String) = "$BASE/$type/$connectionId"
        fun teamcity(connectionId: String) = "$BASE/teamcity/$connectionId"
        fun github(connectionId: String) = "$BASE/github/$connectionId"
    }

    const val HEALTH = "/health"
}
