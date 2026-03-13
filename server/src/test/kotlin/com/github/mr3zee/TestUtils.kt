package com.github.mr3zee

import com.github.mr3zee.api.ErrorResponse
import com.github.mr3zee.auth.UserSession
import com.github.mr3zee.auth.authModule
import com.github.mr3zee.connections.connectionsModule
import com.github.mr3zee.plugins.CorrelationId
import com.github.mr3zee.plugins.CorrelationIdKey
import com.github.mr3zee.plugins.RequestSizeLimit
import com.github.mr3zee.plugins.healthRoute
import com.github.mr3zee.projects.projectsModule
import com.github.mr3zee.releases.releasesModule
import com.github.mr3zee.notifications.notificationsModule
import com.github.mr3zee.schedules.schedulesModule
import com.github.mr3zee.triggers.triggersModule
import com.github.mr3zee.webhooks.webhooksModule
import com.github.mr3zee.execution.BlockExecutor
import com.github.mr3zee.execution.StubBlockExecutor
import io.ktor.client.HttpClient
import io.ktor.client.request.*
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation as ClientContentNegotiation
import io.ktor.client.plugins.cookies.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.ContentTransformationException
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.defaultheaders.*
import io.ktor.server.plugins.ratelimit.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import io.ktor.server.sessions.*
import io.ktor.server.testing.*
import io.ktor.server.websocket.*
import io.ktor.util.*
import kotlinx.serialization.SerializationException
import org.koin.dsl.module
import org.koin.ktor.plugin.Koin
import org.koin.logger.slf4jLogger
import kotlin.time.Duration.Companion.seconds

fun testDbConfig() = DatabaseConfig(
    url = "jdbc:h2:mem:test_${System.nanoTime()};DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
    user = "sa",
    password = "",
    driver = "org.h2.Driver",
)

fun testAuthConfig() = AuthConfig(
    sessionSignKey = "6162636465666768696a6b6c6d6e6f707172737475767778797a313233343536",
)

fun testEncryptionConfig() = EncryptionConfig(
    key = "YWJjZGVmZ2hpamtsbW5vcHFyc3R1dnd4eXoxMjM0NTY=",
)

fun testWebhookConfig() = WebhookConfig(
    baseUrl = "http://localhost:8080",
)

/**
 * Test override module:
 * - Replaces real executors with stubs (existing tests create blocks without connections)
 * - Replaces CIO HttpClient with MockEngine (connection tests and executors don't hit real APIs)
 */
val testOverrideModule = module {
    single<BlockExecutor> {
        StubBlockExecutor()
    }
    single<HttpClient> {
        createTestHttpClient()
    }
    single { CoroutineScope(SupervisorJob()) }
}

fun Application.testModule(dbConfig: DatabaseConfig = testDbConfig()) {
    val authConfig = testAuthConfig()
    install(Koin) {
        slf4jLogger()
        allowOverride(true)
        modules(
            appModule(dbConfig, testEncryptionConfig(), authConfig, testWebhookConfig()),
            authModule,
            projectsModule,
            connectionsModule,
            webhooksModule,
            releasesModule,
            notificationsModule,
            schedulesModule,
            triggersModule,
            testOverrideModule,
        )
    }

    install(DefaultHeaders) {
        header("X-Content-Type-Options", "nosniff")
        header("X-Frame-Options", "DENY")
        header("Referrer-Policy", "strict-origin-when-cross-origin")
    }

    install(RequestSizeLimit)

    install(RateLimit) {
        register(RateLimitName("login")) {
            rateLimiter(limit = 10, refillPeriod = 60.seconds)
        }
    }

    install(CorrelationId)

    install(Sessions) {
        cookie<UserSession>("SESSION") {
            cookie.path = "/"
            cookie.maxAgeInSeconds = 86400
            cookie.httpOnly = true
            cookie.extensions["SameSite"] = "Lax"
            transform(SessionTransportTransformerMessageAuthentication(hex(authConfig.sessionSignKey)))
        }
    }
    install(Authentication) {
        session<UserSession>("session-auth") {
            validate { it }
            challenge {
                val correlationId = call.attributes.getOrNull(CorrelationIdKey)
                call.respond(
                    HttpStatusCode.Unauthorized,
                    ErrorResponse(
                        error = "Not authenticated",
                        code = "UNAUTHORIZED",
                        correlationId = correlationId,
                    ),
                )
            }
        }
    }
    install(WebSockets) {
        pingPeriod = 15.seconds
        timeout = 15.seconds
    }
    install(ContentNegotiation) {
        json(AppJson)
    }
    install(StatusPages) {
        exception<IllegalArgumentException> { call, cause ->
            val correlationId = call.attributes.getOrNull(CorrelationIdKey)
            call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponse(
                    error = cause.message ?: "Bad request",
                    code = "BAD_REQUEST",
                    correlationId = correlationId,
                ),
            )
        }
        exception<SerializationException> { call, cause ->
            call.application.environment.log.debug("Serialization error", cause)
            val correlationId = call.attributes.getOrNull(CorrelationIdKey)
            call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponse(
                    error = "Invalid request body",
                    code = "INVALID_BODY",
                    correlationId = correlationId,
                ),
            )
        }
        exception<ContentTransformationException> { call, cause ->
            call.application.environment.log.debug("Content transformation error", cause)
            val correlationId = call.attributes.getOrNull(CorrelationIdKey)
            call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponse(
                    error = "Invalid request body",
                    code = "INVALID_BODY",
                    correlationId = correlationId,
                ),
            )
        }
        exception<ForbiddenException> { call, cause ->
            val correlationId = call.attributes.getOrNull(CorrelationIdKey)
            call.respond(
                HttpStatusCode.Forbidden,
                ErrorResponse(
                    error = cause.message ?: "Forbidden",
                    code = "FORBIDDEN",
                    correlationId = correlationId,
                ),
            )
        }
        exception<NotFoundException> { call, cause ->
            val correlationId = call.attributes.getOrNull(CorrelationIdKey)
            call.respond(
                HttpStatusCode.NotFound,
                ErrorResponse(
                    error = cause.message ?: "Not found",
                    code = "NOT_FOUND",
                    correlationId = correlationId,
                ),
            )
        }
        exception<Throwable> { call, cause ->
            call.application.environment.log.error("Unhandled exception", cause)
            val correlationId = call.attributes.getOrNull(CorrelationIdKey)
            call.respond(
                HttpStatusCode.InternalServerError,
                ErrorResponse(
                    error = "Internal server error",
                    code = "INTERNAL_ERROR",
                    correlationId = correlationId,
                ),
            )
        }
    }
    configureRouting()
}

fun ApplicationTestBuilder.jsonClient() = createClient {
    install(ClientContentNegotiation) {
        json(AppJson)
    }
    install(HttpCookies)
}

/**
 * Registers and logs in a test user. First user in a fresh DB is auto-promoted to ADMIN.
 */
suspend fun HttpClient.login(
    username: String = "admin",
    password: String = "adminpass",
) {
    // Register (idempotent — 409 on duplicate is fine)
    post(com.github.mr3zee.api.ApiRoutes.Auth.REGISTER) {
        contentType(io.ktor.http.ContentType.Application.Json)
        setBody(com.github.mr3zee.api.RegisterRequest(username = username, password = password))
    }
    // Login to get a session cookie
    post(com.github.mr3zee.api.ApiRoutes.Auth.LOGIN) {
        contentType(io.ktor.http.ContentType.Application.Json)
        setBody(com.github.mr3zee.api.LoginRequest(username = username, password = password))
    }
}
