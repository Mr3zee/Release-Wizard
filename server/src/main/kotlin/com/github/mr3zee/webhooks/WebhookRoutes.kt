package com.github.mr3zee.webhooks

import com.github.mr3zee.api.ApiRoutes
import com.github.mr3zee.api.StatusUpdatePayload
import com.github.mr3zee.model.ConnectionId
import io.ktor.http.*
import io.ktor.server.application.ApplicationCall
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.SerializationException
import org.koin.ktor.ext.inject
import java.util.UUID

/**
 * Webhook receiver routes. These are placed OUTSIDE authentication
 * since external services (TeamCity, GitHub) call them directly.
 */
fun Route.webhookRoutes() {
    val webhookService by inject<WebhookService>()
    val statusWebhookService by inject<StatusWebhookService>()

    route(ApiRoutes.Webhooks.BASE) {
        post("/teamcity/{connectionId}") {
            val connectionId = call.requireWebhookConnectionId() ?: return@post
            val secret = call.request.header("X-Webhook-Secret")
            val payload = call.receiveText()

            when (webhookService.processTeamCityWebhook(connectionId, secret, payload)) {
                WebhookResult.Accepted -> call.respond(HttpStatusCode.OK, "Webhook accepted")
                WebhookResult.ConnectionNotFound -> call.respond(HttpStatusCode.NotFound, "Connection not found")
                WebhookResult.InvalidConnectionType -> call.respond(HttpStatusCode.BadRequest, "Not a TeamCity connection")
                WebhookResult.InvalidSecret -> call.respond(HttpStatusCode.Unauthorized, "Invalid webhook secret")
                WebhookResult.NoPendingWebhooks -> call.respond(HttpStatusCode.OK, "No pending webhooks")
            }
        }

        post("/github/{connectionId}") {
            val connectionId = call.requireWebhookConnectionId() ?: return@post
            val signature = call.request.header("X-Hub-Signature-256")
            val payload = call.receiveText()

            when (webhookService.processGitHubWebhook(connectionId, signature, payload)) {
                WebhookResult.Accepted -> call.respond(HttpStatusCode.OK, "Webhook accepted")
                WebhookResult.ConnectionNotFound -> call.respond(HttpStatusCode.NotFound, "Connection not found")
                WebhookResult.InvalidConnectionType -> call.respond(HttpStatusCode.BadRequest, "Not a GitHub connection")
                WebhookResult.InvalidSecret -> call.respond(HttpStatusCode.Unauthorized, "Invalid webhook secret")
                WebhookResult.NoPendingWebhooks -> call.respond(HttpStatusCode.OK, "No pending webhooks")
            }
        }

        post("/status") {

            val authHeader = call.request.header("Authorization")
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                call.respond(HttpStatusCode.NotFound, "Not found")
                return@post
            }
            val tokenStr = authHeader.removePrefix("Bearer ").trim()
            val token = try {
                UUID.fromString(tokenStr)
            } catch (_: IllegalArgumentException) {
                call.respond(HttpStatusCode.NotFound, "Not found")
                return@post
            }

            // Reject oversized payloads (8 KB)
            val contentLength = call.request.contentLength()
            if (contentLength != null && contentLength > MAX_STATUS_PAYLOAD_SIZE) {
                call.respond(HttpStatusCode.BadRequest, "Payload too large")
                return@post
            }

            val payload = try {
                call.receive<StatusUpdatePayload>()
            } catch (_: SerializationException) {
                call.respond(HttpStatusCode.BadRequest, "Invalid JSON body")
                return@post
            } catch (_: ContentTransformationException) {
                call.respond(HttpStatusCode.BadRequest, "Invalid JSON body")
                return@post
            } catch (_: BadRequestException) {
                call.respond(HttpStatusCode.BadRequest, "Invalid JSON body")
                return@post
            }

            when (val result = statusWebhookService.processStatusUpdate(token, payload)) {
                StatusWebhookResult.Accepted -> call.respond(HttpStatusCode.OK, "Update accepted")
                StatusWebhookResult.NotFound -> call.respond(HttpStatusCode.NotFound, "Not found")
                is StatusWebhookResult.BadRequest -> call.respond(HttpStatusCode.BadRequest, result.message)
            }
        }
    }
}

private const val MAX_STATUS_PAYLOAD_SIZE = 8 * 1024L

private suspend fun ApplicationCall.requireWebhookConnectionId(): ConnectionId? {
    val raw = parameters["connectionId"]
    if (raw == null) {
        respond(HttpStatusCode.BadRequest, "Missing connectionId")
        return null
    }
    return try {
        UUID.fromString(raw)
        ConnectionId(raw)
    } catch (_: IllegalArgumentException) {
        respond(HttpStatusCode.BadRequest, "Invalid connection ID format")
        null
    }
}
