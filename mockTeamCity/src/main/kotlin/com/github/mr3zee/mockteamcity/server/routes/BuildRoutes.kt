package com.github.mr3zee.mockteamcity.server.routes

import com.github.mr3zee.mockteamcity.model.MockTeamCityState
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.*

fun Routing.buildRoutes(state: MockTeamCityState) {
    get("/app/rest/builds/{locator}") {
        val locator = call.parameters["locator"] ?: ""
        // Avoid matching the sub-builds query route (handled by subBuildRoutes)
        if (!locator.startsWith("id:")) {
            call.respondText("Unsupported locator format: $locator", status = HttpStatusCode.BadRequest)
            return@get
        }
        val buildId = locator.removePrefix("id:").toIntOrNull()
        if (buildId == null) {
            call.respondText("Invalid build ID", status = HttpStatusCode.BadRequest)
            return@get
        }

        val build = state.currentState().builds.find { it.id == buildId }
        if (build == null) {
            call.respondText("Build not found: $buildId", status = HttpStatusCode.NotFound)
            return@get
        }

        call.respond(
            buildJsonObject {
                put("id", build.id)
                put("state", build.state.name.lowercase())
                if (build.status != null) put("status", build.status.name)
                put("number", build.number)
                putBuildType(state, build.buildTypeId)
                if (build.startDate != null) put("startDate", build.startDate)
                if (build.finishDate != null) put("finishDate", build.finishDate)
            }
        )
    }

    post("/app/rest/builds/{locator}") {
        val locator = call.parameters["locator"] ?: ""
        val buildId = locator.removePrefix("id:").toIntOrNull()
        if (buildId == null) {
            call.respondText("Invalid build ID", status = HttpStatusCode.BadRequest)
            return@post
        }

        // Cancel request — check for buildCancelRequest in body
        val body = call.receiveText()
        if (body.contains("buildCancelRequest")) {
            state.cancelBuild(buildId)
            call.respondText("Build $buildId cancelled", status = HttpStatusCode.OK)
        } else {
            call.respondText("Unknown action", status = HttpStatusCode.BadRequest)
        }
    }
}

fun JsonObjectBuilder.putBuildType(state: MockTeamCityState, buildTypeId: String) {
    val bt = state.currentState().buildTypes.find { it.id == buildTypeId }
    putJsonObject("buildType") {
        put("id", buildTypeId)
        put("name", bt?.name ?: buildTypeId)
    }
}
