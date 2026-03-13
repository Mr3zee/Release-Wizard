package com.github.mr3zee.connections

import com.github.mr3zee.WebhookConfig
import com.github.mr3zee.api.*
import com.github.mr3zee.auth.userSession
import com.github.mr3zee.model.Connection
import com.github.mr3zee.model.ConnectionId
import com.github.mr3zee.model.ConnectionType
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject
import java.util.UUID

fun Route.connectionRoutes() {
    val service by inject<ConnectionsService>()
    val webhookConfig by inject<WebhookConfig>()

    route(ApiRoutes.Connections.BASE) {
        get {
            val connections = service.listConnections(call.userSession())
            val webhookUrls = connections.mapNotNull { conn ->
                webhookUrl(conn, webhookConfig)?.let { conn.id.value to it }
            }.toMap()
            call.respond(ConnectionListResponse(connections, webhookUrls))
        }

        post {
            val request = call.receive<CreateConnectionRequest>()
            if (request.name.isBlank()) {
                call.respond(HttpStatusCode.BadRequest, "Connection name must not be blank")
                return@post
            }
            val connection = service.createConnection(request, call.userSession())
            call.respond(HttpStatusCode.Created, ConnectionResponse(connection, webhookUrl(connection, webhookConfig)))
        }

        route("/{id}") {
            get {
                val id = call.requireConnectionId() ?: return@get
                val connection = service.getConnection(id, call.userSession())
                if (connection != null) {
                    call.respond(ConnectionResponse(connection, webhookUrl(connection, webhookConfig)))
                } else {
                    call.respond(HttpStatusCode.NotFound, "Connection not found")
                }
            }

            put {
                val id = call.requireConnectionId() ?: return@put
                val request = call.receive<UpdateConnectionRequest>()
                val connection = service.updateConnection(id, request, call.userSession())
                if (connection != null) {
                    call.respond(ConnectionResponse(connection, webhookUrl(connection, webhookConfig)))
                } else {
                    call.respond(HttpStatusCode.NotFound, "Connection not found")
                }
            }

            delete {
                val id = call.requireConnectionId() ?: return@delete
                val deleted = service.deleteConnection(id, call.userSession())
                if (deleted) {
                    call.respond(HttpStatusCode.NoContent)
                } else {
                    call.respond(HttpStatusCode.NotFound, "Connection not found")
                }
            }

            post("/test") {
                val id = call.requireConnectionId() ?: return@post
                val result = service.testConnection(id, call.userSession())
                if (result == null) {
                    call.respond(HttpStatusCode.NotFound, "Connection not found")
                    return@post
                }
                call.respond(result)
            }
        }
    }
}

private fun webhookUrl(connection: Connection, webhookConfig: WebhookConfig): String? {
    return when (connection.type) {
        ConnectionType.TEAMCITY -> ApiRoutes.Webhooks.teamcity(connection.id.value)
        ConnectionType.GITHUB -> ApiRoutes.Webhooks.github(connection.id.value)
        else -> null
    }?.let { path -> "${webhookConfig.baseUrl}$path" }
}

private suspend fun io.ktor.server.application.ApplicationCall.requireConnectionId(): ConnectionId? {
    val raw = parameters["id"]
    if (raw == null) {
        respond(HttpStatusCode.BadRequest, "Missing id")
        return null
    }
    return try {
        UUID.fromString(raw)
        ConnectionId(raw)
    } catch (_: IllegalArgumentException) {
        respond(HttpStatusCode.BadRequest, "Invalid connection ID format")
        null
    }
}
