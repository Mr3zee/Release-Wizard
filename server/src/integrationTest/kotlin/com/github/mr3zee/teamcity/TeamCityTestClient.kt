package com.github.mr3zee.teamcity

import com.github.mr3zee.AppJson
import com.github.mr3zee.waitUntil
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

fun createTeamCityTestHttpClient(): HttpClient {
    return HttpClient(CIO) {
        install(ContentNegotiation) {
            json(AppJson)
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 30_000
            connectTimeoutMillis = 10_000
            socketTimeoutMillis = 30_000
        }
    }
}

/**
 * Polls TeamCity REST API until the build finishes (or times out).
 * Returns the final build JSON.
 */
suspend fun HttpClient.waitForBuildCompletion(
    config: TeamCityTestConfig,
    buildId: String,
    maxAttempts: Int = 120,
    delayMillis: Long = 1_000,
): JsonObject {
    var result: JsonObject? = null
    waitUntil(maxAttempts = maxAttempts, delayMillis = delayMillis) {
        val response = get("${config.serverUrl}/app/rest/builds/id:$buildId") {
            header("Authorization", "Bearer ${config.token}")
            header("Accept", "application/json")
        }
        val body = AppJson.decodeFromString<JsonObject>(response.bodyAsText())
        val state = body["state"]?.jsonPrimitive?.content
        if (state == "finished") {
            result = body
            true
        } else {
            false
        }
    }
    return result!!
}

/**
 * Cancels a build. Best-effort cleanup for tests.
 */
suspend fun HttpClient.cancelBuild(config: TeamCityTestConfig, buildId: String) {
    try {
        post("${config.serverUrl}/app/rest/builds/id:$buildId") {
            header("Authorization", "Bearer ${config.token}")
            contentType(ContentType.Application.Json)
            setBody("""{"comment":"Cancelled by integration test cleanup","readdIntoQueue":false}""")
        }
    } catch (_: Exception) {
        // best-effort
    }
}
