package com.github.mr3zee.mavenpublication

import com.github.mr3zee.NotFoundException
import com.github.mr3zee.api.ApiRoutes
import com.github.mr3zee.api.CreateMavenTriggerRequest
import com.github.mr3zee.api.MavenTriggerListResponse
import com.github.mr3zee.api.MavenTriggerResponse
import com.github.mr3zee.api.ToggleMavenTriggerRequest
import com.github.mr3zee.auth.userSession
import com.github.mr3zee.util.requireProjectId
import com.github.mr3zee.util.requireUuidParam
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject

fun Route.mavenTriggerRoutes() {
    val service by inject<MavenTriggerService>()

    route(ApiRoutes.MavenTriggers.byProject("{projectId}")) {
        get {
            val projectId = call.requireProjectId()
            val triggers = service.listByProject(projectId, call.userSession())
            call.respond(MavenTriggerListResponse(triggers))
        }

        post {
            val projectId = call.requireProjectId()
            val request = call.receive<CreateMavenTriggerRequest>()
            val trigger = service.create(projectId, request, call.userSession())
            call.respond(HttpStatusCode.Created, MavenTriggerResponse(trigger))
        }

        route("/{triggerId}") {
            get {
                val triggerId = call.requireMavenTriggerId()
                val trigger = service.getById(triggerId, call.userSession())
                    ?: throw NotFoundException("Maven trigger not found: $triggerId")
                call.respond(MavenTriggerResponse(trigger))
            }

            put {
                val triggerId = call.requireMavenTriggerId()
                val body = call.receive<ToggleMavenTriggerRequest>()
                val trigger = service.toggle(triggerId, body.enabled, call.userSession())
                    ?: throw NotFoundException("Maven trigger not found: $triggerId")
                call.respond(MavenTriggerResponse(trigger))
            }

            delete {
                val triggerId = call.requireMavenTriggerId()
                val deleted = service.delete(triggerId, call.userSession())
                if (!deleted) {
                    throw NotFoundException("Maven trigger not found: $triggerId")
                }
                call.respond(HttpStatusCode.NoContent)
            }
        }
    }
}

private fun ApplicationCall.requireMavenTriggerId(): String {
    return requireUuidParam("triggerId")
}
