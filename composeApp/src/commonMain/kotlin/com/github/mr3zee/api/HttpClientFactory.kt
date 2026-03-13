package com.github.mr3zee.api

import com.github.mr3zee.AppJson
import com.github.mr3zee.auth.AuthEventBus
import io.ktor.client.*
import io.ktor.client.plugins.*
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
        HttpResponseValidator {
            handleResponseExceptionWithRequest { cause, request ->
                if (cause is ClientRequestException && cause.response.status.value == 401) {
                    // Don't intercept login endpoint 401s — those mean "wrong credentials"
                    val isLoginRequest = request.url.encodedPath.endsWith("/auth/login")
                    if (!isLoginRequest) {
                        AuthEventBus.emitSessionExpired()
                    }
                }
                throw cause
            }
        }
    }
}

fun serverUrl(path: String): String = "${platformHttpBaseUrl()}$path"

fun wsServerUrl(path: String): String = "${platformWsBaseUrl()}$path"
