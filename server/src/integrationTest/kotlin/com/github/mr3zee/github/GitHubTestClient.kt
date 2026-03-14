package com.github.mr3zee.github

import com.github.mr3zee.AppJson
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.JsonObject

fun createGitHubTestHttpClient(): HttpClient {
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

suspend fun HttpClient.deleteGitHubRelease(config: GitHubTestConfig, releaseId: Long) {
    delete("https://api.github.com/repos/${config.owner}/${config.repo}/releases/$releaseId") {
        header("Authorization", "Bearer ${config.token}")
        header("Accept", "application/vnd.github+json")
    }
}

suspend fun HttpClient.deleteGitHubTag(config: GitHubTestConfig, tagName: String) {
    delete("https://api.github.com/repos/${config.owner}/${config.repo}/git/refs/tags/$tagName") {
        header("Authorization", "Bearer ${config.token}")
        header("Accept", "application/vnd.github+json")
    }
}

suspend fun HttpClient.findGitHubReleaseByTag(config: GitHubTestConfig, tag: String): JsonObject? {
    val response = get("https://api.github.com/repos/${config.owner}/${config.repo}/releases/tags/$tag") {
        header("Authorization", "Bearer ${config.token}")
        header("Accept", "application/vnd.github+json")
    }
    return when {
        response.status.isSuccess() -> AppJson.decodeFromString<JsonObject>(response.bodyAsText())
        response.status == HttpStatusCode.NotFound -> null
        else -> throw RuntimeException("GitHub API error fetching release by tag '$tag': ${response.status}")
    }
}
