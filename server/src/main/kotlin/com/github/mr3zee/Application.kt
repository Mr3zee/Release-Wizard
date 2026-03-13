package com.github.mr3zee

import com.github.mr3zee.auth.UserSession
import com.github.mr3zee.auth.authModule
import com.github.mr3zee.auth.authRoutes
import com.github.mr3zee.connections.connectionRoutes
import com.github.mr3zee.connections.connectionsModule
import com.github.mr3zee.execution.ExecutionEngine
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
import io.ktor.server.auth.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import io.ktor.server.websocket.*
import io.ktor.util.*
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

    install(Sessions) {
        cookie<UserSession>("SESSION") {
            cookie.path = "/"
            cookie.maxAgeInSeconds = 86400
            cookie.httpOnly = true
            transform(SessionTransportTransformerMessageAuthentication(hex(authConfig.sessionSignKey)))
        }
    }

    install(Authentication) {
        session<UserSession>("session-auth") {
            validate { it }
            challenge {
                call.respond(HttpStatusCode.Unauthorized, "Not authenticated")
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
            call.respondText(cause.message ?: "Bad request", status = HttpStatusCode.BadRequest)
        }
        exception<Throwable> { call, cause ->
            call.application.environment.log.error("Unhandled exception", cause)
            call.respondText("Internal server error", status = HttpStatusCode.InternalServerError)
        }
    }

    configureRouting()

    monitor.subscribe(ApplicationStopped) {
        try {
            val koin = org.koin.java.KoinJavaComponent.getKoin()
            koin.getOrNull<ExecutionEngine>()?.shutdown()
            koin.getOrNull<io.ktor.client.HttpClient>()?.close()
        } catch (_: IllegalStateException) {
            // Koin may already be stopped
        }
    }
}

fun Application.configureRouting() {
    routing {
        get("/") {
            call.respondText("Release Wizard API")
        }
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
