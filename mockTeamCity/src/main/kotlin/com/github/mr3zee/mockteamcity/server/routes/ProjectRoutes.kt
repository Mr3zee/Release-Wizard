package com.github.mr3zee.mockteamcity.server.routes

import com.github.mr3zee.mockteamcity.model.MockTeamCityState
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.*

fun Routing.projectRoutes(state: MockTeamCityState) {
    get("/app/rest/projects") {
        val projects = state.currentState().projects
        call.respond(
            buildJsonObject {
                putJsonArray("project") {
                    for (p in projects) {
                        addJsonObject {
                            put("id", p.id)
                            put("name", p.name)
                            if (p.parentProjectId != null) {
                                put("parentProjectId", p.parentProjectId)
                            }
                        }
                    }
                }
            }
        )
    }
}
