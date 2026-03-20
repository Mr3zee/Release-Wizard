package com.github.mr3zee.plugins

import com.github.mr3zee.api.ErrorResponse
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("com.github.mr3zee.plugins.RequestSizeLimit")

private const val MAX_CONTENT_LENGTH = 1_048_576L // 1 MB

/**
 * Lightweight request body size limit plugin.
 * Checks the Content-Length header and responds 413 if it exceeds 1 MB.
 *
 * INFRA-H1: After responding 413, route handlers will still execute but any attempt
 * to read the body via `call.receive()` will throw because the response is already committed.
 * For chunked transfer encoding without Content-Length, protection must be configured at the
 * engine level (e.g., Netty's maxContentLength).
 */
val RequestSizeLimit = createApplicationPlugin(name = "RequestSizeLimit") {
    onCall { call ->
        val contentLength = call.request.headers[HttpHeaders.ContentLength]?.toLongOrNull()
        if (contentLength != null && contentLength > MAX_CONTENT_LENGTH) {
            log.warn("Request rejected: payload too large ({} bytes) for {} {}",
                contentLength, call.request.local.method.value, call.request.local.uri)
            call.respond(
                HttpStatusCode.PayloadTooLarge,
                ErrorResponse(
                    error = "Request body too large",
                    code = "PAYLOAD_TOO_LARGE",
                ),
            )
        }
    }
}
