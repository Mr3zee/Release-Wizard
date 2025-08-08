package io.github.mr3zee.rwizard

import io.github.mr3zee.rwizard.database.DatabaseConfig
import io.github.mr3zee.rwizard.services.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.http.content.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import kotlinx.serialization.json.Json
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

fun main() {
    // Initialize database
    DatabaseConfig.init()
    
    embeddedServer(Netty, port = SERVER_PORT, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

fun Application.module() {
    // Install content negotiation for JSON
    install(ContentNegotiation) {
        json(Json {
            prettyPrint = true
            isLenient = true
            ignoreUnknownKeys = true
        })
    }
    
    // Install WebSocket support
    install(WebSockets) {
        pingPeriod = 15.seconds
        timeout = 15.seconds
        maxFrameSize = Long.MAX_VALUE
        masking = false
    }
    
    routing {
        // Serve static files from resources/static directory
        staticResources("/", "static")
        
        // Health check endpoint
        get("/api/health") {
            call.respond(mapOf("status" to "healthy", "timestamp" to System.currentTimeMillis()))
        }
    }
}
