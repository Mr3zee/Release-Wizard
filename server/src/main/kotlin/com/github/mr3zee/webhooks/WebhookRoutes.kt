package com.github.mr3zee.webhooks

import com.github.mr3zee.api.ApiRoutes
import com.github.mr3zee.api.StatusUpdatePayload
import io.ktor.http.*
import io.ktor.server.plugins.ratelimit.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.SerializationException
import org.koin.ktor.ext.inject
import org.slf4j.LoggerFactory
import java.util.UUID

private val log = LoggerFactory.getLogger("com.github.mr3zee.webhooks.WebhookRoutes")

/**
 * Webhook receiver routes. These are placed OUTSIDE authentication
 * since external services call them directly.
 *
 * Only the custom status webhook remains — TC/GH build completion
 * is now handled via server-side API polling.
 */
fun Route.webhookRoutes() {
    val statusWebhookService by inject<StatusWebhookService>()

    // HOOK-M1: Rate limit on public webhook endpoint to prevent abuse
    rateLimit(RateLimitName("webhook")) {
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

            // BUG-6: Reject oversized payloads — read raw text with size cap to guard
            // against both Content-Length and chunked transfer encoding bypass.
            val rawText = try {
                val text = call.receiveText()
                if (text.length > MAX_STATUS_PAYLOAD_SIZE) {
                    call.respond(HttpStatusCode.BadRequest, "Payload too large")
                    return@post
                }
                text
            } catch (_: Exception) {
                call.respond(HttpStatusCode.BadRequest, "Failed to read request body")
                return@post
            }

            val payload = try {
                com.github.mr3zee.AppJson.decodeFromString<StatusUpdatePayload>(rawText)
            } catch (_: SerializationException) {
                call.respond(HttpStatusCode.BadRequest, "Invalid JSON body")
                return@post
            } catch (_: Exception) {
                call.respond(HttpStatusCode.BadRequest, "Invalid JSON body")
                return@post
            }

            // HOOK-M2: Mask bearer token in log output to prevent credential exposure
            val maskedToken = if (tokenStr.length > 8) tokenStr.take(8) + "..." else "***"

            when (val result = statusWebhookService.processStatusUpdate(token, payload)) {
                StatusWebhookResult.Accepted -> {
                    log.info("Status webhook accepted for token {}", maskedToken)
                    call.respond(HttpStatusCode.OK, "Update accepted")
                }
                StatusWebhookResult.NotFound -> {
                    log.warn("Status webhook rejected: token {} not found", maskedToken)
                    call.respond(HttpStatusCode.NotFound, "Not found")
                }
                is StatusWebhookResult.BadRequest -> {
                    log.warn("Status webhook bad request: {}", result.message)
                    call.respond(HttpStatusCode.BadRequest, result.message)
                }
            }
        }
    }
    } // rateLimit("webhook")
}

private const val MAX_STATUS_PAYLOAD_SIZE = 8 * 1024
