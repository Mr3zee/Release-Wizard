package com.github.mr3zee

import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.cookies.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*

/**
 * Creates a mock HttpClient that routes requests based on URL path suffix.
 * Each entry maps a path suffix to a JSON response body.
 * Paths not matched return empty JSON object.
 */
fun mockHttpClient(routes: Map<String, MockRoute>): HttpClient {
    return HttpClient(MockEngine { request ->
        val path = request.url.encodedPath
        val method = request.method
        // Match against full /api/v1 prefix — route keys are suffixes like "/auth/login"
        val route = routes.entries.firstOrNull { (key, r) ->
            path == "/api/v1$key" && (r.method == null || r.method == method)
        }?.value
        respond(
            content = route?.body ?: "{}",
            status = route?.status ?: HttpStatusCode.OK,
            headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
        )
    }) {
        install(ContentNegotiation) { json(AppJson) }
        install(HttpCookies)
        expectSuccess = true
    }
}

data class MockRoute(
    val body: String,
    val status: HttpStatusCode = HttpStatusCode.OK,
    val method: HttpMethod? = null,
)

fun json(body: String, status: HttpStatusCode = HttpStatusCode.OK, method: HttpMethod? = null) =
    MockRoute(body, status, method)
