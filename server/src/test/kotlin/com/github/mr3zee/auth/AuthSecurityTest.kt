package com.github.mr3zee.auth

import com.github.mr3zee.*
import com.github.mr3zee.api.*
import com.github.mr3zee.plugins.CorrelationId
import com.github.mr3zee.plugins.CorrelationIdKey
import com.github.mr3zee.plugins.CsrfProtection
import com.github.mr3zee.plugins.RequestSizeLimit
import com.github.mr3zee.plugins.SessionTtl
import com.github.mr3zee.projects.LockConflictException
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.ContentTransformationException
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.defaultheaders.*
import io.ktor.server.plugins.ratelimit.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import io.ktor.server.testing.*
import io.ktor.server.websocket.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.util.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.job
import kotlinx.serialization.SerializationException
import org.koin.dsl.module
import org.koin.ktor.plugin.Koin
import org.koin.logger.slf4jLogger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class AuthSecurityTest {

    @Test
    fun `session cookie is encrypted and not readable as base64`() = testApplication {
        application { testModule() }
        val client = jsonClient()

        val registerResponse = client.post(ApiRoutes.Auth.REGISTER) {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest(username = "enctest", password = "testpass123"))
        }
        assertEquals(HttpStatusCode.Created, registerResponse.status)

        val loginResponse = client.post(ApiRoutes.Auth.LOGIN) {
            contentType(ContentType.Application.Json)
            setBody(LoginRequest(username = "enctest", password = "testpass123"))
        }
        assertEquals(HttpStatusCode.OK, loginResponse.status)

        val setCookieHeader = loginResponse.headers.getAll(HttpHeaders.SetCookie)
            ?.firstOrNull { it.startsWith("SESSION=") }
        assertNotNull(setCookieHeader, "SESSION cookie should be set on login")

        val cookieValue = setCookieHeader.substringAfter("SESSION=").substringBefore(";")
        assertTrue(cookieValue.isNotEmpty(), "Cookie value should not be empty")

        val decodedAttempt = try {
            val decoded = java.util.Base64.getDecoder().decode(cookieValue)
            String(decoded, Charsets.UTF_8)
        } catch (_: IllegalArgumentException) {
            ""
        }
        assertFalse(
            decodedAttempt.contains("enctest") || decodedAttempt.contains("username"),
            "Encrypted session cookie should not reveal username when base64-decoded",
        )
    }

    @Test
    fun `duplicate username registration returns generic error without revealing username taken`() = testApplication {
        application { testModule() }
        val client = jsonClient()

        client.post(ApiRoutes.Auth.REGISTER) {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest(username = "dupuser", password = "testpass123"))
        }

        val response = client.post(ApiRoutes.Auth.REGISTER) {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest(username = "dupuser", password = "otherpass456"))
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        val error = response.body<ErrorResponse>()
        assertEquals("REGISTRATION_FAILED", error.code)
        assertFalse(
            error.error.contains("already taken", ignoreCase = true),
            "Error message should not reveal that the username is already taken",
        )
        assertFalse(
            error.error.contains("username", ignoreCase = true),
            "Error message should not mention 'username'",
        )
    }

    @Test
    fun `authenticated rate limiting returns 429 when limit exceeded`() = testApplication {
        application { testModuleWithLowRateLimit() }
        val client = jsonClient()
        client.login()

        var got429 = false
        repeat(10) {
            val response = client.get(ApiRoutes.Projects.BASE)
            if (response.status == HttpStatusCode.TooManyRequests) {
                got429 = true
                return@repeat
            }
        }
        assertTrue(got429, "Should receive 429 after exceeding authenticated-api rate limit")
    }

    @Test
    fun `500 error does not leak internal details`() = testApplication {
        application { testModuleWithThrowingRoute() }
        val client = jsonClient()
        client.login()

        val response = client.get("/api/test-error")
        assertEquals(HttpStatusCode.InternalServerError, response.status)
        val error = response.body<ErrorResponse>()
        assertEquals("INTERNAL_ERROR", error.code)
        assertEquals("Internal server error", error.error)
        assertFalse(
            error.error.contains("NullPointer") || error.error.contains("Exception"),
            "500 error should not leak internal exception details",
        )
    }

    @Test
    fun `IllegalArgumentException with UUID is sanitized`() = testApplication {
        application { testModuleWithThrowingRoute() }
        val client = jsonClient()
        client.login()

        val response = client.get("/api/test-bad-request-uuid")
        assertEquals(HttpStatusCode.BadRequest, response.status)
        val error = response.body<ErrorResponse>()
        assertFalse(
            error.error.contains("550e8400-e29b-41d4-a716-446655440000"),
            "Sanitized error should not contain raw UUIDs",
        )
        assertTrue(
            error.error.contains("[id]"),
            "Sanitized error should replace UUIDs with [id]",
        )
    }

    @Test
    fun `IllegalArgumentException with stack trace is fully sanitized`() = testApplication {
        application { testModuleWithThrowingRoute() }
        val client = jsonClient()
        client.login()

        val response = client.get("/api/test-bad-request-stacktrace")
        assertEquals(HttpStatusCode.BadRequest, response.status)
        val error = response.body<ErrorResponse>()
        assertEquals("Bad request", error.error)
    }
}

private fun Application.testModuleWithLowRateLimit() {
    val dbConfig = testDbConfig()
    val authConfig = testAuthConfig()
    val executionScope = CoroutineScope(SupervisorJob(coroutineContext.job) + Dispatchers.Default)

    install(Koin) {
        slf4jLogger()
        allowOverride(true)
        modules(
            appModule(dbConfig, testEncryptionConfig(), authConfig, testWebhookConfig(), testPasswordPolicyConfig()),
            authModule,
            com.github.mr3zee.projects.projectsModule,
            com.github.mr3zee.projects.projectLockModule,
            com.github.mr3zee.connections.connectionsModule,
            com.github.mr3zee.webhooks.webhooksModule,
            com.github.mr3zee.releases.releasesModule,
            com.github.mr3zee.notifications.notificationsModule,
            com.github.mr3zee.schedules.schedulesModule,
            com.github.mr3zee.triggers.triggersModule,
            com.github.mr3zee.mavenpublication.mavenTriggerModule,
            com.github.mr3zee.tags.tagsModule,
            com.github.mr3zee.teams.teamsModule,
            module { single { executionScope } },
            testOverrideModule,
        )
    }

    install(DefaultHeaders) {
        header("X-Content-Type-Options", "nosniff")
    }
    install(RequestSizeLimit)
    install(RateLimit) {
        register(RateLimitName("login")) {
            rateLimiter(limit = 10, refillPeriod = 60.seconds)
            requestKey { call -> call.request.local.remoteHost }
        }
        register(RateLimitName("webhook")) {
            rateLimiter(limit = 30, refillPeriod = 60.seconds)
            requestKey { call -> call.request.local.remoteHost }
        }
        register(RateLimitName("invite")) {
            rateLimiter(limit = 10, refillPeriod = 60.seconds)
            requestKey { call -> call.request.local.remoteHost }
        }
        register(RateLimitName("create-team")) {
            rateLimiter(limit = 10, refillPeriod = 60.seconds)
            requestKey { call -> call.sessions.get<UserSession>()?.userId ?: call.request.local.remoteHost }
        }
        register(RateLimitName("create-project")) {
            rateLimiter(limit = 10, refillPeriod = 60.seconds)
            requestKey { call -> call.sessions.get<UserSession>()?.userId ?: call.request.local.remoteHost }
        }
        register(RateLimitName("test-connection")) {
            rateLimiter(limit = 5, refillPeriod = 60.seconds)
            requestKey { call -> call.sessions.get<UserSession>()?.userId ?: call.request.local.remoteHost }
        }
        register(RateLimitName("authenticated-api")) {
            rateLimiter(limit = 3, refillPeriod = 60.seconds)
            requestKey { call -> call.sessions.get<UserSession>()?.userId ?: call.request.local.remoteHost }
        }
        register(RateLimitName("password-reset")) {
            rateLimiter(limit = 5, refillPeriod = 60.seconds)
            requestKey { call -> call.request.local.remoteHost }
        }
    }
    install(CorrelationId)
    install(Sessions) {
        cookie<UserSession>("SESSION") {
            cookie.path = "/"
            cookie.maxAgeInSeconds = 86400
            cookie.httpOnly = true
            cookie.extensions["SameSite"] = "Lax"
            if (authConfig.sessionEncryptKey.isNotEmpty()) {
                transform(SessionTransportTransformerEncrypt(hex(authConfig.sessionEncryptKey), hex(authConfig.sessionSignKey)))
            } else {
                transform(SessionTransportTransformerMessageAuthentication(hex(authConfig.sessionSignKey)))
            }
        }
    }
    install(Authentication) {
        session<UserSession>("session-auth") {
            validate { it }
            challenge {
                val correlationId = call.attributes.getOrNull(CorrelationIdKey)
                call.respond(
                    HttpStatusCode.Unauthorized,
                    ErrorResponse(error = "Not authenticated", code = "UNAUTHORIZED", correlationId = correlationId),
                )
            }
        }
    }
    install(SessionTtl)
    install(CsrfProtection)
    install(WebSockets) {
        pingPeriod = 15.seconds
        timeout = 15.seconds
    }
    install(ContentNegotiation) {
        json(AppJson)
    }
    install(StatusPages) {
        exception<IllegalArgumentException> { call, cause ->
            call.application.environment.log.debug("Validation error", cause)
            val correlationId = call.attributes.getOrNull(CorrelationIdKey)
            val sanitizedMessage = sanitizeErrorMessage(cause.message)
            call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponse(error = sanitizedMessage, code = "BAD_REQUEST", correlationId = correlationId),
            )
        }
        exception<SerializationException> { call, cause ->
            call.application.environment.log.debug("Serialization error", cause)
            val correlationId = call.attributes.getOrNull(CorrelationIdKey)
            call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponse(error = "Invalid request body", code = "INVALID_BODY", correlationId = correlationId),
            )
        }
        exception<ContentTransformationException> { call, cause ->
            call.application.environment.log.debug("Content transformation error", cause)
            val correlationId = call.attributes.getOrNull(CorrelationIdKey)
            call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponse(error = "Invalid request body", code = "INVALID_BODY", correlationId = correlationId),
            )
        }
        exception<ForbiddenException> { call, cause ->
            val correlationId = call.attributes.getOrNull(CorrelationIdKey)
            call.respond(
                HttpStatusCode.Forbidden,
                ErrorResponse(error = cause.message ?: "Forbidden", code = "FORBIDDEN", correlationId = correlationId),
            )
        }
        exception<NotFoundException> { call, cause ->
            val correlationId = call.attributes.getOrNull(CorrelationIdKey)
            call.respond(
                HttpStatusCode.NotFound,
                ErrorResponse(error = cause.message ?: "Not found", code = "NOT_FOUND", correlationId = correlationId),
            )
        }
        exception<LockConflictException> { call, cause ->
            call.respond(
                HttpStatusCode.Conflict,
                ProjectLockConflictResponse(
                    error = cause.message ?: "Project is locked",
                    code = "LOCK_CONFLICT",
                    lock = cause.lock,
                ),
            )
        }
        exception<Throwable> { call, cause ->
            call.application.environment.log.error("Unhandled exception", cause)
            val correlationId = call.attributes.getOrNull(CorrelationIdKey)
            call.respond(
                HttpStatusCode.InternalServerError,
                ErrorResponse(error = "Internal server error", code = "INTERNAL_ERROR", correlationId = correlationId),
            )
        }
    }
    configureRouting()
}

private fun Application.testModuleWithThrowingRoute() {
    testModule()
    routing {
        authenticate("session-auth") {
            get("/api/test-error") {
                throw RuntimeException("Sensitive internal error: database connection to postgres://admin:secret@db.internal:5432/mydb failed")
            }
            get("/api/test-bad-request-uuid") {
                throw IllegalArgumentException("Invalid entity with id 550e8400-e29b-41d4-a716-446655440000")
            }
            get("/api/test-bad-request-stacktrace") {
                throw IllegalArgumentException("Error occurred\n\tat com.example.Foo\$Bar.method(Foo.kt:42)")
            }
        }
    }
}
