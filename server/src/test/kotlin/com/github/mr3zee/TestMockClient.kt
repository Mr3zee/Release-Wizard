package com.github.mr3zee

import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*

/**
 * Creates a mock HttpClient for integration tests.
 * Returns appropriate stub responses for each external service API,
 * so connection tests and executors don't hit real APIs.
 */
fun createTestHttpClient(): HttpClient = HttpClient(MockEngine { request ->
    val url = request.url.toString()
    val jsonHeaders = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
    when {
        url.contains("api.github.com/repos/") && !url.contains("/releases") ->
            respond("""{"id":1,"full_name":"test/repo"}""", HttpStatusCode.OK, jsonHeaders)
        url.contains("/app/rest/server") ->
            respond("""{"version":"2023.11"}""", HttpStatusCode.OK, jsonHeaders)
        url.contains("/api/v1/publisher/status") ->
            respond("""{"status":"ok"}""", HttpStatusCode.OK, jsonHeaders)
        else ->
            respond("ok", HttpStatusCode.OK, jsonHeaders)
    }
}) {
    install(ContentNegotiation) {
        json(AppJson)
    }
}
