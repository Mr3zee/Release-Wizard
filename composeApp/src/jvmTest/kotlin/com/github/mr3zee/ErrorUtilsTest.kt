package com.github.mr3zee

import com.github.mr3zee.api.ErrorResponse
import com.github.mr3zee.api.toUserMessage
import com.github.mr3zee.auth.AuthEvent
import com.github.mr3zee.auth.AuthEventBus
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.cookies.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

class ErrorUtilsTest {

    @Test
    fun `toUserMessage extracts error from JSON ErrorResponse`() = runBlocking {
        val client = HttpClient(MockEngine { _ ->
            val errorJson = AppJson.encodeToString(
                ErrorResponse.serializer(),
                ErrorResponse(error = "Release not found: abc", code = "NOT_FOUND"),
            )
            respond(
                content = errorJson,
                status = HttpStatusCode.NotFound,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }) {
            install(ContentNegotiation) { json(AppJson) }
            expectSuccess = true
        }

        try {
            client.get("http://localhost/api/v1/releases/abc")
            assertTrue(false, "Should have thrown")
        } catch (e: ClientRequestException) {
            val message = e.toUserMessage()
            assertEquals("Release not found: abc", message)
        }
    }

    @Test
    fun `toUserMessage falls back to status-based message for non-JSON body`() = runBlocking {
        val client = HttpClient(MockEngine { _ ->
            respond(
                content = "plain text error",
                status = HttpStatusCode.InternalServerError,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Text.Plain.toString()),
            )
        }) {
            expectSuccess = true
        }

        try {
            client.get("http://localhost/test")
            assertTrue(false, "Should have thrown")
        } catch (e: ServerResponseException) {
            val message = e.toUserMessage()
            assertEquals("Server error", message)
        }
    }

    @Test
    fun `AuthEventBus emits SessionExpired on 401`() = runBlocking {
        val client = HttpClient(MockEngine { _ ->
            respond(
                content = """{"error":"Not authenticated","code":"UNAUTHORIZED"}""",
                status = HttpStatusCode.Unauthorized,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }) {
            install(ContentNegotiation) { json(AppJson) }
            install(HttpCookies)
            expectSuccess = true
            HttpResponseValidator {
                handleResponseExceptionWithRequest { cause, request ->
                    if (cause is ClientRequestException && cause.response.status.value == 401) {
                        val isLoginRequest = request.url.encodedPath.endsWith("/auth/login")
                        if (!isLoginRequest) {
                            AuthEventBus.emitSessionExpired()
                        }
                    }
                    throw cause
                }
            }
        }

        val eventDeferred = async {
            // todo claude: use withTimeoutOrNull
            withTimeout(1000.milliseconds) {
                AuthEventBus.events.first()
            }
        }

        try {
            client.get("http://localhost/api/v1/releases")
        } catch (_: Exception) {
            // Expected
        }

        val event = eventDeferred.await()
        assertTrue(event is AuthEvent.SessionExpired)
    }

    @Test
    fun `login 401 does NOT emit SessionExpired`() = runBlocking {
        var sessionExpiredEmitted = false

        val client = HttpClient(MockEngine { _ ->
            respond(
                content = """{"error":"Invalid credentials","code":"INVALID_CREDENTIALS"}""",
                status = HttpStatusCode.Unauthorized,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }) {
            install(ContentNegotiation) { json(AppJson) }
            expectSuccess = true
            HttpResponseValidator {
                handleResponseExceptionWithRequest { cause, request ->
                    if (cause is ClientRequestException && cause.response.status.value == 401) {
                        val isLoginRequest = request.url.encodedPath.endsWith("/auth/login")
                        if (!isLoginRequest) {
                            sessionExpiredEmitted = true
                            AuthEventBus.emitSessionExpired()
                        }
                    }
                    throw cause
                }
            }
        }

        try {
            client.post("http://localhost/api/v1/auth/login")
        } catch (_: Exception) {
            // Expected
        }

        assertTrue(!sessionExpiredEmitted, "Login 401 should NOT emit SessionExpired")
    }
}
