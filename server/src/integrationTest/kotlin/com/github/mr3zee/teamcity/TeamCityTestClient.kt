package com.github.mr3zee.teamcity

import com.github.mr3zee.AppJson
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*

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
