package com.github.mr3zee.webhooks

import com.github.mr3zee.api.ApiRoutes
import com.github.mr3zee.api.StatusUpdatePayload
import io.ktor.http.*
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.SerializationException
import org.koin.ktor.ext.inject
import java.util.UUID

/**
 * Webhook receiver routes. These are placed OUTSIDE authentication
 * since external services call them directly.
 *
 * Only the custom status webhook remains — TC/GH build completion
 * is now handled via server-side API polling.
 */
fun Route.webhookRoutes() {
    val statusWebhookService by inject<StatusWebhookService>()

    route(ApiRoutes.Webhooks.BASE) {
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
