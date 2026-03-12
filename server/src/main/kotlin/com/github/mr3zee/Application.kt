package com.github.mr3zee

import com.github.mr3zee.projects.projectRoutes
import com.github.mr3zee.projects.projectsModule
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.dsl.module
import org.koin.ktor.plugin.Koin
import org.koin.logger.slf4jLogger

fun Application.module() {
    val dbConfig = environment.config.databaseConfig()

    install(Koin) {
        slf4jLogger()
        modules(
            appModule(dbConfig),
            projectsModule,
        )
    }

    install(ContentNegotiation) {
        json(AppJson)
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
}

fun Application.configureRouting() {
    routing {
        get("/") {
            call.respondText("Release Wizard API")
        }
        projectRoutes()
    }
}
