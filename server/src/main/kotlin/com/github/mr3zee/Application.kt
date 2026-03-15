package com.github.mr3zee

import com.github.mr3zee.api.ErrorResponse
import com.github.mr3zee.auth.UserSession
import com.github.mr3zee.auth.authModule
import com.github.mr3zee.auth.authRoutes
import com.github.mr3zee.connections.connectionRoutes
import com.github.mr3zee.connections.connectionsModule
import com.github.mr3zee.execution.ExecutionEngine
import com.github.mr3zee.execution.RecoveryService
import com.github.mr3zee.notifications.NotificationListener
import com.github.mr3zee.notifications.notificationRoutes
import com.github.mr3zee.notifications.notificationsModule
import com.github.mr3zee.plugins.CorrelationId
import com.github.mr3zee.plugins.CorrelationIdKey
import com.github.mr3zee.plugins.CsrfProtection
import com.github.mr3zee.plugins.SessionTtl
import com.github.mr3zee.plugins.healthRoute
import com.github.mr3zee.projects.LockConflictException
import com.github.mr3zee.projects.projectLockModule
import com.github.mr3zee.projects.projectLockRoutes
import com.github.mr3zee.projects.projectRoutes
import com.github.mr3zee.projects.projectsModule
import com.github.mr3zee.releases.releaseRoutes
import com.github.mr3zee.releases.releaseWebSocketRoutes
import com.github.mr3zee.releases.releasesModule
import com.github.mr3zee.schedules.SchedulerService
import com.github.mr3zee.schedules.scheduleRoutes
import com.github.mr3zee.schedules.schedulesModule
import com.github.mr3zee.tags.tagRoutes
import com.github.mr3zee.tags.tagsModule
import com.github.mr3zee.teams.myInviteRoutes
import com.github.mr3zee.teams.teamRoutes
import com.github.mr3zee.teams.teamsModule
import com.github.mr3zee.triggers.triggerRoutes
import com.github.mr3zee.triggers.triggerWebhookRoutes
import com.github.mr3zee.triggers.triggersModule
import com.github.mr3zee.webhooks.webhookRoutes
import com.github.mr3zee.webhooks.webhooksModule
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import com.github.mr3zee.plugins.RequestSizeLimit
import io.ktor.client.HttpClient
import io.ktor.server.auth.*
import io.ktor.server.plugins.ContentTransformationException
import io.ktor.server.plugins.calllogging.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.defaultheaders.*
import io.ktor.server.plugins.ratelimit.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import io.ktor.server.websocket.*
import io.ktor.util.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.serialization.SerializationException
import org.koin.dsl.module
import org.koin.java.KoinJavaComponent.getKoin
import org.koin.ktor.plugin.Koin
import org.koin.logger.slf4jLogger
import kotlin.time.Duration.Companion.seconds

fun Application.module() {
    val dbConfig = environment.config.databaseConfig()
    val authConfig = environment.config.authConfig()
    val encryptionConfig = environment.config.encryptionConfig()
    val webhookConfig = environment.config.webhookConfig()
    val passwordPolicyConfig = environment.config.passwordPolicyConfig()
    val corsConfig = environment.config.corsConfig()

    val appVersion = environment.config.propertyOrNull("app.version")?.getString() ?: "dev"

    val executionScope = CoroutineScope(SupervisorJob(coroutineContext.job) + Dispatchers.Default)

    install(Koin) {
        slf4jLogger()
        modules(
            appModule(dbConfig, encryptionConfig, authConfig, webhookConfig, passwordPolicyConfig),
            authModule,
            projectsModule,
            projectLockModule,
            connectionsModule,
            webhooksModule,
            releasesModule,
            notificationsModule,
            schedulesModule,
            triggersModule,
            tagsModule,
            teamsModule,
            module { single { executionScope } },
        )
    }

    install(DefaultHeaders) {
        header("X-Content-Type-Options", "nosniff")
        header("X-Frame-Options", "DENY")
        header("Referrer-Policy", "strict-origin-when-cross-origin")
        header("Strict-Transport-Security", "max-age=63072000; includeSubDomains; preload")
        header("Permissions-Policy", "camera=(), microphone=(), geolocation=()")
    }

    install(CORS) {
        val log = this@module.environment.log
        for (origin in corsConfig.allowedOrigins) {
            if (!origin.startsWith("https://")) {
                log.warn(
                    "CORS origin '{}' does not use https:// — it will be served over https only. " +
                        "Ensure this is intentional (e.g., local development).",
                    origin,
                )
            }
            val host = origin
                .removePrefix("https://")
                .removePrefix("http://")
                .trimEnd('/')
            require(host.isNotBlank()) { "CORS origin '$origin' does not contain a valid host" }
            allowHost(host, schemes = listOf("https"))
        }
        if (corsConfig.allowedOrigins.isEmpty()) {
            // Development fallback: no origins allowed (strict by default)
        }
        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.Authorization)
        allowHeader("X-CSRF-Token")
        exposeHeader("X-CSRF-Token")
        allowCredentials = true
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Delete)
        allowMethod(HttpMethod.Patch)
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
            cookie.maxAgeInSeconds = authConfig.sessionTtlSeconds
            cookie.httpOnly = true
            cookie.secure = this@module.environment.config.propertyOrNull("app.auth.secureCookie")?.getString()?.toBooleanStrictOrNull() ?: true
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

    // Session TTL must be installed after Sessions (reads/writes session cookie)
    install(SessionTtl)

    // CSRF must be installed after Sessions (reads session for token validation)
    install(CsrfProtection)

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
        exception<LockConflictException> { call, cause ->
            call.respond(
                HttpStatusCode.Conflict,
                com.github.mr3zee.api.ProjectLockConflictResponse(
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
                ErrorResponse(
                    error = "Internal server error",
                    code = "INTERNAL_ERROR",
                    correlationId = correlationId,
                ),
            )
        }
    }

    configureRouting(appVersion)

    monitor.subscribe(ApplicationStarted) {
        try {
            val koin = getKoin()

            // Start notification listener BEFORE recovery so events are captured
            val listener = koin.getOrNull<NotificationListener>()
            val engine = koin.getOrNull<ExecutionEngine>()
            val scope = koin.getOrNull<CoroutineScope>()
            if (listener != null && engine != null && scope != null) {
                listener.start(engine, scope)
            }

            // Then run recovery asynchronously (events emitted here will be captured by listener)
            val recoveryService = koin.getOrNull<RecoveryService>()
            if (recoveryService != null && scope != null) {
                scope.launch {
                    try {
                        recoveryService.recover()
                    } catch (e: Exception) {
                        environment.log.error("Release recovery failed", e)
                    }
                }
            }

            // Start scheduler service (with missed schedule recovery)
            val schedulerService = koin.getOrNull<SchedulerService>()
            if (schedulerService != null && scope != null) {
                schedulerService.start(scope)
            }
        } catch (e: Exception) {
            environment.log.error("Application startup failed", e)
        }
    }

    monitor.subscribe(ApplicationStopped) {
        try {
            val koin = getKoin()
            koin.getOrNull<SchedulerService>()?.stop()
            koin.getOrNull<HttpClient>()?.close()
        } catch (_: IllegalStateException) {
            // Koin may already be stopped
        }
    }
}

fun Application.configureRouting(appVersion: String = "dev") {
    routing {
        get("/") {
            call.respond(mapOf(
                "service" to "Release Wizard API",
                "version" to appVersion,
            ))
        }
        healthRoute()
        authRoutes()
        webhookRoutes()
        triggerWebhookRoutes()
        authenticate("session-auth") {
            teamRoutes()
            myInviteRoutes()
            projectRoutes()
            projectLockRoutes()
            connectionRoutes()
            releaseRoutes()
            releaseWebSocketRoutes()
            notificationRoutes()
            scheduleRoutes()
            triggerRoutes()
            tagRoutes()
        }
    }
}
