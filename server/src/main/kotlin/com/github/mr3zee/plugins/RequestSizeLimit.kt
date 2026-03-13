package com.github.mr3zee.plugins

import com.github.mr3zee.api.ErrorResponse
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*

private const val MAX_CONTENT_LENGTH = 1_048_576L // 1 MB

/**
 * Lightweight request body size limit plugin.
 * Checks the Content-Length header and responds 413 if it exceeds 1 MB.
 * Note: This does not guard against chunked transfer encoding without Content-Length.
 * For full protection, configure Netty's maxContentLength at the engine level.
 */
val RequestSizeLimit = createApplicationPlugin(name = "RequestSizeLimit") {
    onCall { call ->
        val contentLength = call.request.headers[HttpHeaders.ContentLength]?.toLongOrNull()
        if (contentLength != null && contentLength > MAX_CONTENT_LENGTH) {
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
