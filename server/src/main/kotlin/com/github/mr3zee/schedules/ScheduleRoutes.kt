package com.github.mr3zee.schedules

import com.github.mr3zee.NotFoundException
import com.github.mr3zee.api.CreateScheduleRequest
import com.github.mr3zee.api.ScheduleListResponse
import com.github.mr3zee.api.ScheduleResponse
import com.github.mr3zee.auth.userSession
import com.github.mr3zee.model.ProjectId
import io.ktor.http.*
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject
import java.util.UUID

fun Route.scheduleRoutes() {
    val service by inject<ScheduleService>()

    // todo claude: ApiRoutes not used
    route("/api/v1/projects/{projectId}/schedules") {
        get {
            val projectId = call.requireProjectId()
            val schedules = service.listByProject(projectId, call.userSession())
            call.respond(ScheduleListResponse(schedules))
        }

        post {
            val projectId = call.requireProjectId()
            val request = call.receive<CreateScheduleRequest>()
            val schedule = service.create(projectId, request, call.userSession())
            call.respond(HttpStatusCode.Created, ScheduleResponse(schedule))
        }

        route("/{scheduleId}") {
            get {
                val scheduleId = call.requireScheduleId()
                val schedule = service.getById(scheduleId, call.userSession())
                    ?: throw NotFoundException("Schedule not found: $scheduleId")
                call.respond(ScheduleResponse(schedule))
            }

            put {
                val scheduleId = call.requireScheduleId()
                val body = call.receive<ToggleScheduleRequest>()
                val schedule = service.toggle(scheduleId, body.enabled, call.userSession())
                    ?: throw NotFoundException("Schedule not found: $scheduleId")
                call.respond(ScheduleResponse(schedule))
            }

            delete {
                val scheduleId = call.requireScheduleId()
                val deleted = service.delete(scheduleId, call.userSession())
                if (!deleted) {
                    throw NotFoundException("Schedule not found: $scheduleId")
                }
                call.respond(HttpStatusCode.NoContent)
            }
        }
    }
}

@kotlinx.serialization.Serializable
data class ToggleScheduleRequest(val enabled: Boolean)

// todo claude: duplicate function
private fun ApplicationCall.requireProjectId(): ProjectId {
    val raw = parameters["projectId"]
        ?: throw IllegalArgumentException("Missing projectId")
    try {
        UUID.fromString(raw)
    } catch (_: IllegalArgumentException) {
        throw IllegalArgumentException("Invalid project ID format")
    }
    return ProjectId(raw)
}

private fun ApplicationCall.requireScheduleId(): String {
    val raw = parameters["scheduleId"]
        ?: throw IllegalArgumentException("Missing scheduleId")
    try {
        UUID.fromString(raw)
    } catch (_: IllegalArgumentException) {
        throw IllegalArgumentException("Invalid schedule ID format")
    }
    return raw
}
