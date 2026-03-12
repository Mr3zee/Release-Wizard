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

    object Connections {
        const val BASE = "$API_V1/connections"
        fun byId(id: String) = "$BASE/$id"
    }
}
