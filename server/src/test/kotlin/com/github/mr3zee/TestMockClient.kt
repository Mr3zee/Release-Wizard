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
        url.contains("api.github.com/repos/") && url.contains("/actions/workflows") && !url.contains("/contents/") ->
            respond("""{"total_count":1,"workflows":[{"id":1,"name":"CI","path":".github/workflows/ci.yml","state":"active"}]}""", HttpStatusCode.OK, jsonHeaders)
        url.contains("api.github.com/repos/") && url.contains("/contents/") ->
            respond("""{"content":"${java.util.Base64.getEncoder().encodeToString("on:\n  workflow_dispatch:\n    inputs:\n      environment:\n        description: Target env\n        default: staging\n        type: choice\n".toByteArray())}"}""", HttpStatusCode.OK, jsonHeaders)
        url.contains("api.github.com/repos/") && !url.contains("/releases") ->
            respond("""{"id":1,"full_name":"test/repo"}""", HttpStatusCode.OK, jsonHeaders)
        url.contains("/app/rest/server") ->
            respond("""{"version":"2023.11"}""", HttpStatusCode.OK, jsonHeaders)
        url.contains("/app/rest/projects") ->
            respond("""{"project":[{"id":"_Root","name":"<Root project>"},{"id":"Proj","name":"Project","parentProjectId":"_Root"}]}""", HttpStatusCode.OK, jsonHeaders)
        url.contains("/app/rest/buildTypes") && url.contains("/parameters") ->
            respond("""{"property":[{"name":"env.VERSION","value":"1.0","own":true,"type":{"rawValue":"text"}}]}""", HttpStatusCode.OK, jsonHeaders)
        url.contains("/app/rest/buildTypes") ->
            respond("""{"buildType":[{"id":"Proj_Build","name":"Build","projectId":"Proj"}]}""", HttpStatusCode.OK, jsonHeaders)
        url.contains("/api/v1/publisher/status") ->
            respond("""{"status":"ok"}""", HttpStatusCode.OK, jsonHeaders)
        url.contains("maven-metadata.xml") ->
            respond(
                """<?xml version="1.0"?><metadata><versioning><versions><version>1.0.0</version><version>1.1.0</version></versions></versioning></metadata>""",
                HttpStatusCode.OK,
                headersOf(HttpHeaders.ContentType, ContentType.Application.Xml.toString()),
            )
        else ->
            respond("ok", HttpStatusCode.OK, jsonHeaders)
    }
}) {
    install(ContentNegotiation) {
        json(AppJson)
    }
}
