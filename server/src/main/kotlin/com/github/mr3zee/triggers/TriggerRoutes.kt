package com.github.mr3zee.triggers

import com.github.mr3zee.NotFoundException
import com.github.mr3zee.api.ApiRoutes
import com.github.mr3zee.api.CreateTriggerRequest
import com.github.mr3zee.api.ToggleTriggerRequest
import com.github.mr3zee.api.TriggerListResponse
import com.github.mr3zee.auth.userSession
import com.github.mr3zee.util.requireProjectId
import com.github.mr3zee.util.requireUuidParam
import io.ktor.http.*
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject
import java.util.UUID

/**
 * Authenticated trigger CRUD routes (inside session-auth).
 */
fun Route.triggerRoutes() {
    val service by inject<TriggerService>()

    route(ApiRoutes.Triggers.byProject("{projectId}")) {
        get {
            val projectId = call.requireProjectId()
            val triggers = service.listByProject(projectId, call.userSession())
            call.respond(TriggerListResponse(triggers))
        }

        post {
            val projectId = call.requireProjectId()
            val request = call.receive<CreateTriggerRequest>()
            val trigger = service.create(projectId, request, call.userSession(), "")
            call.respond(HttpStatusCode.Created, trigger)
        }

        route("/{triggerId}") {
            get {
                val triggerId = call.requireTriggerId()
                val trigger = service.getById(triggerId, call.userSession())
                    ?: throw NotFoundException("Trigger not found: $triggerId")
                call.respond(trigger)
            }

            put {
                val triggerId = call.requireTriggerId()
                val body = call.receive<ToggleTriggerRequest>()
                val trigger = service.toggle(triggerId, body.enabled, call.userSession())
                    ?: throw NotFoundException("Trigger not found: $triggerId")
                call.respond(trigger)
            }

            delete {
                val triggerId = call.requireTriggerId()
                val deleted = service.delete(triggerId, call.userSession())
                if (!deleted) {
                    throw NotFoundException("Trigger not found: $triggerId")
                }
                call.respond(HttpStatusCode.NoContent)
            }
        }
    }
}

/**
 * Unauthenticated webhook endpoint.
 * Accepts the secret from the Authorization header (Bearer token) only.
 */
fun Route.triggerWebhookRoutes() {
    val service by inject<TriggerService>()

    post(ApiRoutes.Triggers.webhook("{triggerId}")) {
        val triggerId = call.parameters["triggerId"]
        if (triggerId == null) {
            call.respond(HttpStatusCode.BadRequest, "Missing triggerId")
            return@post
        }

        try {
            UUID.fromString(triggerId)
        } catch (_: IllegalArgumentException) {
            call.respond(HttpStatusCode.BadRequest, "Invalid trigger ID format")
            return@post
        }

        val secret = call.request.header("Authorization")?.removePrefix("Bearer ")
        if (secret.isNullOrBlank()) {
            call.respond(HttpStatusCode.Unauthorized, "Missing secret — use Authorization: Bearer <secret> header")
            return@post
        }

        val fired = service.fireWebhook(triggerId, secret)
        if (fired) {
            call.respond(HttpStatusCode.OK, "Webhook triggered")
        } else {
            call.respond(HttpStatusCode.Unauthorized, "Invalid trigger or secret")
        }
    }
}

private fun ApplicationCall.requireTriggerId(): String {
    return requireUuidParam("triggerId")
}
