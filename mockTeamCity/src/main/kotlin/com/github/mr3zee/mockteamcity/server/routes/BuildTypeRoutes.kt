package com.github.mr3zee.mockteamcity.server.routes

import com.github.mr3zee.mockteamcity.model.MockTeamCityState
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.*
import java.net.URLDecoder

fun Routing.buildTypeRoutes(state: MockTeamCityState) {
    get("/app/rest/buildTypes") {
        val buildTypes = state.currentState().buildTypes
        call.respond(
            buildJsonObject {
                putJsonArray("buildType") {
                    for (bt in buildTypes) {
                        addJsonObject {
                            put("id", bt.id)
                            put("name", bt.name)
                            put("projectId", bt.projectId)
                        }
                    }
                }
            }
        )
    }

    get("/app/rest/buildTypes/{locator}/parameters") {
        val locator = URLDecoder.decode(call.parameters["locator"] ?: "", Charsets.UTF_8.name())
        val buildTypeId = locator.removePrefix("id:")
        val bt = state.currentState().buildTypes.find { it.id == buildTypeId }
        if (bt == null) {
            call.respondText("Build type not found: $buildTypeId", status = HttpStatusCode.NotFound)
            return@get
        }
        call.respond(
            buildJsonObject {
                putJsonArray("property") {
                    for (param in bt.parameters.filter { it.own }) {
                        addJsonObject {
                            put("name", param.name)
                            put("value", param.value)
                            put("own", param.own)
                            if (param.typeRawValue.isNotEmpty()) {
                                putJsonObject("type") {
                                    put("rawValue", param.typeRawValue)
                                }
                            }
                        }
                    }
                }
            }
        )
    }
}
