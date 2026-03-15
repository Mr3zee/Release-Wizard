package com.github.mr3zee.projects

import com.github.mr3zee.api.ApiRoutes
import com.github.mr3zee.api.ErrorResponse
import com.github.mr3zee.auth.userSession
import com.github.mr3zee.model.ProjectId
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject
import java.util.*

fun Route.projectLockRoutes() {
    val service by inject<ProjectLockService>()

    route(ApiRoutes.Projects.BASE) {
        route("/{id}/lock") {
            post {
                val id = call.requireProjectId() ?: return@post
                val lock = service.acquireLock(id, call.userSession())
                call.respond(HttpStatusCode.OK, lock)
            }

            delete {
                val id = call.requireProjectId() ?: return@delete
                val force = call.request.queryParameters["force"]?.toBooleanStrictOrNull() == true
                if (force) {
                    service.forceReleaseLock(id, call.userSession())
                } else {
                    service.releaseLock(id, call.userSession())
                }
                call.respond(HttpStatusCode.NoContent)
            }

            get {
                val id = call.requireProjectId() ?: return@get
                val lock = service.getLockInfo(id, call.userSession())
                if (lock != null) {
                    call.respond(HttpStatusCode.OK, lock)
                } else {
                    call.respond(
                        HttpStatusCode.NotFound,
                        ErrorResponse(error = "No active lock", code = "NOT_FOUND"),
                    )
                }
            }

            put("/heartbeat") {
                val id = call.requireProjectId() ?: return@put
                val lock = service.heartbeat(id, call.userSession())
                if (lock != null) {
                    call.respond(HttpStatusCode.OK, lock)
                } else {
                    call.respond(
                        HttpStatusCode.Conflict,
                        ErrorResponse(error = "Lock not held or expired", code = "LOCK_NOT_HELD"),
                    )
                }
            }
        }
    }
}

internal suspend fun ApplicationCall.requireProjectId(): ProjectId? {
    val raw = parameters["id"]
    if (raw == null) {
        respond(HttpStatusCode.BadRequest, ErrorResponse(error = "Missing id", code = "VALIDATION_ERROR"))
        return null
    }
    return try {
        UUID.fromString(raw)
        ProjectId(raw)
    } catch (_: IllegalArgumentException) {
        respond(HttpStatusCode.BadRequest, ErrorResponse(error = "Invalid project ID format", code = "VALIDATION_ERROR"))
        null
    }
}
