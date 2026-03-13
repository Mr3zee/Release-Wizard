package com.github.mr3zee.plugins

import com.github.mr3zee.api.ApiRoutes
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.koin.ktor.ext.inject
import org.slf4j.LoggerFactory

@Serializable
data class HealthStatus(
    val status: String,
    val error: String? = null,
)

private val log = LoggerFactory.getLogger("com.github.mr3zee.plugins.HealthRoute")

fun Route.healthRoute() {
    val db by inject<Database>()

    get(ApiRoutes.HEALTH) {
        try {
            transaction(db) {
                exec("SELECT 1")
            }
            call.respond(HealthStatus(status = "UP"))
        } catch (e: Exception) {
            log.warn("Health check failed", e)
            call.respond(HttpStatusCode.ServiceUnavailable, HealthStatus(status = "DOWN", error = "Database unavailable"))
        }
    }
}
