package com.github.mr3zee.plugins

import com.github.mr3zee.api.ApiRoutes
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.koin.ktor.ext.inject
import org.slf4j.LoggerFactory

@Serializable
data class HealthStatus(
    val status: String,
    val version: String? = null,
    val error: String? = null,
)

private val log = LoggerFactory.getLogger("com.github.mr3zee.plugins.HealthRoute")

fun Route.healthRoute(appVersion: String) {
    val db by inject<Database>()

    suspend fun RoutingContext.respondHealth() {
        try {
            withContext(Dispatchers.IO) {
                suspendTransaction(db) {
                    @Suppress("SqlNoDataSourceInspection")
                    exec("SELECT 1")
                }
            }
            call.respond(HealthStatus(status = "UP", version = appVersion))
        } catch (e: Exception) {
            log.warn("Health check failed", e)
            call.respond(HttpStatusCode.ServiceUnavailable, HealthStatus(status = "DOWN", version = appVersion, error = "Database unavailable"))
        }
    }

    get(ApiRoutes.HEALTH) { respondHealth() }
    get(ApiRoutes.HEALTHZ) { respondHealth() }
}
