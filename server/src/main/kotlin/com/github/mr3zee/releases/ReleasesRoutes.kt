package com.github.mr3zee.releases

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
            try {
                val release = service.startRelease(request)
                val executions = service.getBlockExecutions(release.id)
                call.respond(HttpStatusCode.Created, ReleaseResponse(release, executions))
            } catch (e: IllegalArgumentException) {
                call.respond(HttpStatusCode.BadRequest, e.message ?: "Invalid request")
            }
        }

        route("/{id}") {
            get {
                val id = call.requireReleaseId() ?: return@get
                val release = service.getRelease(id)
                if (release != null) {
                    val executions = service.getBlockExecutions(id)
                    call.respond(ReleaseResponse(release, executions))
                } else {
                    call.respond(HttpStatusCode.NotFound, "Release not found")
                }
            }

            post("/await") {
                val id = call.requireReleaseId() ?: return@post
                service.awaitRelease(id)
                val release = service.getRelease(id)
                if (release != null) {
                    val executions = service.getBlockExecutions(id)
                    call.respond(ReleaseResponse(release, executions))
                } else {
                    call.respond(HttpStatusCode.NotFound, "Release not found")
                }
            }

            post("/cancel") {
                val id = call.requireReleaseId() ?: return@post
                val cancelled = service.cancelRelease(id)
                if (cancelled) {
                    val release = service.getRelease(id)!!
                    val executions = service.getBlockExecutions(id)
                    call.respond(ReleaseResponse(release, executions))
                } else {
                    call.respond(HttpStatusCode.BadRequest, "Cannot cancel release")
                }
            }

            route("/blocks/{blockId}") {
                post("/restart") {
                    val releaseId = call.requireReleaseId() ?: return@post
                    val blockId = call.requireBlockId() ?: return@post
                    val restarted = service.restartBlock(releaseId, blockId)
                    if (restarted) {
                        call.respond(HttpStatusCode.OK, "Block restarted")
                    } else {
                        call.respond(HttpStatusCode.BadRequest, "Cannot restart block")
                    }
                }

                post("/approve") {
                    val releaseId = call.requireReleaseId() ?: return@post
                    val blockId = call.requireBlockId() ?: return@post
                    val request = call.receive<ApproveBlockRequest>()
                    val approved = service.approveBlock(releaseId, blockId, request)
                    if (approved) {
                        call.respond(HttpStatusCode.OK, "Block approved")
                    } else {
                        call.respond(HttpStatusCode.BadRequest, "Cannot approve block")
                    }
                }
            }
        }
    }
}

private suspend fun io.ktor.server.application.ApplicationCall.requireReleaseId(): ReleaseId? {
    val raw = parameters["id"]
    if (raw == null) {
        respond(HttpStatusCode.BadRequest, "Missing id")
        return null
    }
    return try {
        UUID.fromString(raw)
        ReleaseId(raw)
    } catch (_: IllegalArgumentException) {
        respond(HttpStatusCode.BadRequest, "Invalid release ID format")
        null
    }
}

private suspend fun io.ktor.server.application.ApplicationCall.requireBlockId(): BlockId? {
    val raw = parameters["blockId"]
    if (raw == null) {
        respond(HttpStatusCode.BadRequest, "Missing blockId")
        return null
    }
    return BlockId(raw)
}
