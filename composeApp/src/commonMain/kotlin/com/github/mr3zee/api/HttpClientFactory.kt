package com.github.mr3zee.api

import com.github.mr3zee.AppJson
import com.github.mr3zee.SERVER_PORT
import io.ktor.client.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.cookies.*
import io.ktor.client.plugins.websocket.*
import io.ktor.serialization.kotlinx.json.*

fun createHttpClient(): HttpClient {
    return HttpClient {
        install(ContentNegotiation) {
            json(AppJson)
        }
        install(WebSockets)
        install(HttpCookies)
        expectSuccess = true
    }
}

fun serverUrl(path: String): String = "http://localhost:$SERVER_PORT$path"

fun wsServerUrl(path: String): String = "ws://localhost:$SERVER_PORT$path"
