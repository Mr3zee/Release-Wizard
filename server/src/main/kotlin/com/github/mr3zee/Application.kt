package com.github.mr3zee

import com.github.mr3zee.api.ErrorResponse
import com.github.mr3zee.auth.UserSession
import com.github.mr3zee.auth.authModule
import com.github.mr3zee.auth.authRoutes
import com.github.mr3zee.connections.connectionRoutes
import com.github.mr3zee.connections.connectionsModule
import com.github.mr3zee.execution.ExecutionEngine
import com.github.mr3zee.execution.RecoveryService
import com.github.mr3zee.plugins.CorrelationId
import com.github.mr3zee.plugins.CorrelationIdKey
import com.github.mr3zee.plugins.healthRoute
import com.github.mr3zee.projects.projectRoutes
import com.github.mr3zee.projects.projectsModule
import com.github.mr3zee.releases.releaseRoutes
import com.github.mr3zee.releases.releaseWebSocketRoutes
import com.github.mr3zee.releases.releasesModule
import com.github.mr3zee.webhooks.webhookRoutes
import com.github.mr3zee.webhooks.webhooksModule
import io.ktor.events.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import com.github.mr3zee.plugins.RequestSizeLimit
import io.ktor.client.HttpClient
import io.ktor.server.auth.*
import io.ktor.server.plugins.ContentTransformationException
import io.ktor.server.plugins.calllogging.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.defaultheaders.*
import io.ktor.server.plugins.ratelimit.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import io.ktor.server.websocket.*
import io.ktor.util.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerializationException
import org.koin.java.KoinJavaComponent.getKoin
import org.koin.ktor.plugin.Koin
import org.koin.logger.slf4jLogger
import kotlin.time.Duration.Companion.seconds

fun Application.module() {
    val dbConfig = environment.config.databaseConfig()
    val authConfig = environment.config.authConfig()
    val encryptionConfig = environment.config.encryptionConfig()
    val webhookConfig = environment.config.webhookConfig()

    install(Koin) {
        slf4jLogger()
        modules(
            appModule(dbConfig, encryptionConfig, authConfig, webhookConfig),
            authModule,
            projectsModule,
            connectionsModule,
            webhooksModule,
            releasesModule,
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

    // Install CorrelationId BEFORE CallLogging so MDC is available
    install(CorrelationId)

    install(CallLogging) {
        filter { call -> call.request.uri.startsWith("/api/") }
        mdc("correlationId") { it.attributes.getOrNull(CorrelationIdKey) }
    }

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

    install(ContentNegotiation) {
        json(AppJson)
    }

    install(WebSockets) {
        pingPeriod = 15.seconds
        timeout = 15.seconds
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

    monitor.subscribe(ApplicationStarted) {
        try {
            val koin = getKoin()
            val recoveryService = koin.getOrNull<RecoveryService>()
            if (recoveryService != null) {
                runBlocking {
                    recoveryService.recover()
                }
            }
        } catch (e: Exception) {
            environment.log.error("Release recovery failed", e)
        }
    }

    monitor.subscribe(ApplicationStopped) {
        try {
            val koin = getKoin()
            koin.getOrNull<ExecutionEngine>()?.shutdown()
            koin.getOrNull<HttpClient>()?.close()
        } catch (_: IllegalStateException) {
            // Koin may already be stopped
        }
    }
}

fun Application.configureRouting() {
    routing {
        get("/") {
            call.respond(mapOf(
                "service" to "Release Wizard API",
                "version" to (System.getenv("APP_VERSION") ?: "dev"),
            ))
        }
        healthRoute()
        authRoutes()
        webhookRoutes()
        authenticate("session-auth") {
            projectRoutes()
            connectionRoutes()
            releaseRoutes()
            releaseWebSocketRoutes()
        }
    }
}
