package com.github.mr3zee.plugins

import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.util.*
import java.util.UUID

val CorrelationIdKey = AttributeKey<String>("CorrelationId")

val CorrelationId = createApplicationPlugin(name = "CorrelationId") {
    onCall { call ->
        val correlationId = UUID.randomUUID().toString()
        call.attributes.put(CorrelationIdKey, correlationId)
        call.response.header("X-Correlation-Id", correlationId)
    }
}
