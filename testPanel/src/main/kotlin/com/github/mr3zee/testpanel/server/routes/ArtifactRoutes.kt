package com.github.mr3zee.testpanel.server.routes

import com.github.mr3zee.testpanel.model.TestPanelState
import io.ktor.http.*
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.*

fun Routing.artifactRoutes(state: TestPanelState) {
    get("/app/rest/builds/{locator}/artifacts/children/{subpath...}") {
        handleArtifactRequest(call, state, call.parameters["locator"], call.parameters.getAll("subpath"))
    }

    get("/app/rest/builds/{locator}/artifacts/children") {
        handleArtifactRequest(call, state, call.parameters["locator"], null)
    }
}

private suspend fun handleArtifactRequest(
    call: ApplicationCall,
    state: TestPanelState,
    locator: String?,
    subpathParts: List<String>?,
) {
    val buildId = locator?.removePrefix("id:")?.toIntOrNull()
    if (buildId == null) {
        call.respondText("Invalid build ID", status = HttpStatusCode.BadRequest)
        return
    }

    val build = state.currentState().builds.find { it.id == buildId }
    if (build == null) {
        call.respondText("Build not found", status = HttpStatusCode.NotFound)
        return
    }

    val bt = state.currentState().buildTypes.find { it.id == build.buildTypeId }
    var artifacts = bt?.artifactTemplates.orEmpty()

    // Navigate into subdirectory if subpath is provided
    val subpath = subpathParts?.joinToString("/")
    if (!subpath.isNullOrEmpty()) {
        val segments = subpath.split("/")
        for (segment in segments) {
            val dir = artifacts.find { it.name == segment && it.children.isNotEmpty() }
            if (dir == null) {
                call.respondText("Artifact path not found: $subpath", status = HttpStatusCode.NotFound)
                return
            }
            artifacts = dir.children
        }
    }

    call.respond(
        buildJsonObject {
            putJsonArray("file") {
                for (artifact in artifacts) {
                    addJsonObject {
                        put("name", artifact.name)
                        if (artifact.children.isEmpty()) {
                            put("size", artifact.size)
                        } else {
                            putJsonObject("children") {
                                val basePath = "/app/rest/builds/id:$buildId/artifacts/children"
                                val childPath = if (subpath.isNullOrEmpty()) artifact.name
                                else "$subpath/${artifact.name}"
                                put("href", "$basePath/$childPath")
                            }
                        }
                    }
                }
            }
        }
    )
}
