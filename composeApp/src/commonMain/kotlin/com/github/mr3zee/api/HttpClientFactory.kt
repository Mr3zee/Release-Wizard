package com.github.mr3zee.api

import com.github.mr3zee.AppJson
import com.github.mr3zee.auth.AuthEventBus
import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.cookies.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.flow.MutableStateFlow

private const val CSRF_TOKEN_HEADER = "X-CSRF-Token"

fun createHttpClient(): HttpClient {
    val csrfToken = MutableStateFlow("")

    return HttpClient {
        install(ContentNegotiation) {
            json(AppJson)
        }
        install(WebSockets)
        install(HttpCookies)
        expectSuccess = true
        HttpResponseValidator {
            validateResponse { response ->
                // Capture CSRF token from any response
                response.headers[CSRF_TOKEN_HEADER]?.let { token ->
                    csrfToken.value = token
                }
            }
            handleResponseExceptionWithRequest { cause, request ->
                if (cause is ClientRequestException && cause.response.status.value == 401) {
                    // Don't emit session-expired for unauthenticated-by-design endpoints
                    val authExcludedPaths = setOf("/auth/login", "/auth/me", "/auth/register")
                    val isExcluded = authExcludedPaths.any { request.url.encodedPath.endsWith(it) }
                    if (!isExcluded) {
                        AuthEventBus.emitSessionExpired()
                    }
                }
                throw cause
            }
        }
        // Attach CSRF token to all outgoing requests
        install(DefaultRequest) {
            val token = csrfToken.value
            if (token.isNotEmpty()) {
                header(CSRF_TOKEN_HEADER, token)
            }
        }
    }
}

fun serverUrl(path: String): String = "${platformHttpBaseUrl()}$path"

fun wsServerUrl(path: String): String = "${platformWsBaseUrl()}$path"
