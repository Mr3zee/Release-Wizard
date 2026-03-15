package com.github.mr3zee.schedules

import com.github.mr3zee.NotFoundException
import com.github.mr3zee.api.ApiRoutes
import com.github.mr3zee.api.CreateScheduleRequest
import com.github.mr3zee.api.ScheduleListResponse
import com.github.mr3zee.api.ScheduleResponse
import com.github.mr3zee.api.ToggleScheduleRequest
import com.github.mr3zee.auth.userSession
import com.github.mr3zee.util.requireProjectId
import com.github.mr3zee.util.requireUuidParam
import io.ktor.http.*
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject

fun Route.scheduleRoutes() {
    val service by inject<ScheduleService>()

    route(ApiRoutes.Schedules.byProject("{projectId}")) {
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

private fun ApplicationCall.requireScheduleId(): String {
    return requireUuidParam("scheduleId")
}
