package io.github.mr3zee.rwizard

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.http.content.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun main() {
    embeddedServer(Netty, port = SERVER_PORT, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

fun Application.module() {
    routing {
        // Serve static files from resources/static directory
        staticResources("/", "static")

        // API endpoint example
        get("/api/greeting") {
            call.respondText("Ktor API: ${Greeting().greet()}", ContentType.Text.Plain)
        }
    }
}
