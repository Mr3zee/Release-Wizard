package com.github.mr3zee.plugins

import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.util.*
import java.util.UUID

val CorrelationIdKey = AttributeKey<String>("CorrelationId")

private const val UPSTREAM_HEADER = "X-Request-ID"
private const val RESPONSE_HEADER = "X-Correlation-Id"
private val SAFE_ID_PATTERN = Regex("^[a-zA-Z0-9._\\-]+$")

val CorrelationId = createApplicationPlugin(name = "CorrelationId") {
    onCall { call ->
        // INFRA-M2: Prefer upstream correlation ID from reverse proxy / API gateway.
        // Validate format to prevent log injection — only allow alphanumeric, hyphens, and underscores.
        val correlationId = call.request.headers[UPSTREAM_HEADER]
            ?.takeIf { it.isNotBlank() && it.length <= 128 && it.matches(SAFE_ID_PATTERN) }
            ?: UUID.randomUUID().toString()
        call.attributes.put(CorrelationIdKey, correlationId)
        if (!call.isHandled) {
            call.response.header(RESPONSE_HEADER, correlationId)
        }
    }
}
