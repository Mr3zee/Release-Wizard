package com.github.mr3zee.testpanel.server.routes

import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

fun Routing.serverRoutes() {
    get("/app/rest/server") {
        call.respond(
            buildJsonObject {
                put("version", "2024.12-mock")
                put("serverName", "Mock TeamCity")
            }
        )
    }
}
