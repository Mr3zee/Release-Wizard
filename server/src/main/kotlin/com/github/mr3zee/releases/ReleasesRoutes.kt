package com.github.mr3zee.releases

import com.github.mr3zee.NotFoundException
import com.github.mr3zee.api.*
import com.github.mr3zee.model.BlockId
import com.github.mr3zee.model.ReleaseId
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject
import java.util.UUID

fun Route.releaseRoutes() {
    val service by inject<ReleasesService>()

    route(ApiRoutes.Releases.BASE) {
        get {
            val releases = service.listReleases()
            call.respond(ReleaseListResponse(releases))
        }

        post {
            val request = call.receive<CreateReleaseRequest>()
            val release = service.startRelease(request)
            val executions = service.getBlockExecutions(release.id)
            call.respond(HttpStatusCode.Created, ReleaseResponse(release, executions))
        }

        route("/{id}") {
            get {
                val id = call.requireReleaseId()
                val release = service.getRelease(id)
                    ?: throw NotFoundException("Release not found: ${id.value}")
                val executions = service.getBlockExecutions(id)
                call.respond(ReleaseResponse(release, executions))
            }

            // todo claude: not API route from shared ApiRoutes
            post("/await") {
                val id = call.requireReleaseId()
                service.awaitRelease(id)
                val release = service.getRelease(id)
                    ?: throw NotFoundException("Release not found: ${id.value}")
                val executions = service.getBlockExecutions(id)
                call.respond(ReleaseResponse(release, executions))
            }

            // todo claude: not API route from shared ApiRoutes
            post("/cancel") {
                val id = call.requireReleaseId()
                val cancelled = service.cancelRelease(id)
                if (!cancelled) {
                    throw IllegalArgumentException("Cannot cancel release: ${id.value}")
                }
                val release = service.getRelease(id)!!
                val executions = service.getBlockExecutions(id)
                call.respond(ReleaseResponse(release, executions))
            }

            // todo claude: not API route from shared ApiRoutes
            route("/blocks/{blockId}") {
                // todo claude: not API route from shared ApiRoutes
                post("/restart") {
                    val releaseId = call.requireReleaseId()
                    val blockId = call.requireBlockId()
                    val restarted = service.restartBlock(releaseId, blockId)
                    if (!restarted) {
                        throw IllegalArgumentException("Cannot restart block: ${blockId.value}")
                    }
                    call.respond(HttpStatusCode.OK, mapOf("status" to "restarted"))
                }

                // todo claude: not API route from shared ApiRoutes
                post("/approve") {
                    val releaseId = call.requireReleaseId()
                    val blockId = call.requireBlockId()
                    val request = call.receive<ApproveBlockRequest>()
                    val approved = service.approveBlock(releaseId, blockId, request)
                    if (!approved) {
                        throw IllegalArgumentException("Cannot approve block: ${blockId.value}")
                    }
                    call.respond(HttpStatusCode.OK, mapOf("status" to "approved"))
                }
            }
        }
    }
}

private fun io.ktor.server.application.ApplicationCall.requireReleaseId(): ReleaseId {
    val raw = parameters["id"]
        ?: throw IllegalArgumentException("Missing id")
    try {
        UUID.fromString(raw)
    } catch (_: IllegalArgumentException) {
        throw IllegalArgumentException("Invalid release ID format")
    }
    return ReleaseId(raw)
}

private val BLOCK_ID_PATTERN = Regex("^[a-zA-Z0-9_.-]+$")

private fun io.ktor.server.application.ApplicationCall.requireBlockId(): BlockId {
    val raw = parameters["blockId"]
        ?: throw IllegalArgumentException("Missing blockId")
    if (raw.length > 100) {
        throw IllegalArgumentException("Invalid block ID: too long")
    }
    if (!BLOCK_ID_PATTERN.matches(raw)) {
        throw IllegalArgumentException("Invalid block ID format")
    }
    return BlockId(raw)
}
