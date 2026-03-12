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
}
