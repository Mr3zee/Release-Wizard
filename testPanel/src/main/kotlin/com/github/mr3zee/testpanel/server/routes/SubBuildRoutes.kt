package com.github.mr3zee.testpanel.server.routes

import com.github.mr3zee.testpanel.model.TestPanelState
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.*

fun Routing.subBuildRoutes(state: TestPanelState) {
    get("/app/rest/builds") {
        val locator = call.request.queryParameters["locator"] ?: ""

        // Parse: snapshotDependency:(to:(id:{buildId}),includeInitial:true)
        val idMatch = Regex("""to:\(id:(\d+)\)""").find(locator)
        val topBuildId = idMatch?.groupValues?.get(1)?.toIntOrNull()

        if (topBuildId == null) {
            call.respond(buildJsonObject { putJsonArray("build") {} })
            return@get
        }

        val currentState = state.currentState()
        val topBuild = currentState.builds.find { it.id == topBuildId }
        val subBuilds = currentState.builds.filter { it.parentBuildId == topBuildId }
        val allBuilds = listOfNotNull(topBuild) + subBuilds

        call.respond(
            buildJsonObject {
                putJsonArray("build") {
                    for (build in allBuilds) {
                        addJsonObject {
                            put("id", build.id)
                            put("number", build.number)
                            putBuildType(state, build.buildTypeId)
                            put("state", build.state.name.lowercase())
                            if (build.status != null) {
                                put("status", build.status.name)
                            }
                            if (build.startDate != null) put("startDate", build.startDate)
                            if (build.finishDate != null) put("finishDate", build.finishDate)
                        }
                    }
                }
            }
        )
    }
}
