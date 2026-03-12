package com.github.mr3zee.connections

import com.github.mr3zee.api.*
import com.github.mr3zee.model.ConnectionId
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject
import java.util.UUID

fun Route.connectionRoutes() {
    val service by inject<ConnectionsService>()

    route(ApiRoutes.Connections.BASE) {
        get {
            val connections = service.listConnections()
            call.respond(ConnectionListResponse(connections))
        }

        post {
            val request = call.receive<CreateConnectionRequest>()
            if (request.name.isBlank()) {
                call.respond(HttpStatusCode.BadRequest, "Connection name must not be blank")
                return@post
            }
            val connection = service.createConnection(request)
            call.respond(HttpStatusCode.Created, ConnectionResponse(connection))
        }

        route("/{id}") {
            get {
                val id = call.requireConnectionId() ?: return@get
                val connection = service.getConnection(id)
                if (connection != null) {
                    call.respond(ConnectionResponse(connection))
                } else {
                    call.respond(HttpStatusCode.NotFound, "Connection not found")
                }
            }

            put {
                val id = call.requireConnectionId() ?: return@put
                val request = call.receive<UpdateConnectionRequest>()
                val connection = service.updateConnection(id, request)
                if (connection != null) {
                    call.respond(ConnectionResponse(connection))
                } else {
                    call.respond(HttpStatusCode.NotFound, "Connection not found")
                }
            }

            delete {
                val id = call.requireConnectionId() ?: return@delete
                val deleted = service.deleteConnection(id)
                if (deleted) {
                    call.respond(HttpStatusCode.NoContent)
                } else {
                    call.respond(HttpStatusCode.NotFound, "Connection not found")
                }
            }

            post("/test") {
                val id = call.requireConnectionId() ?: return@post
                val connection = service.getConnection(id)
                if (connection == null) {
                    call.respond(HttpStatusCode.NotFound, "Connection not found")
                    return@post
                }
                // Stub: real testing will be implemented in Phase 6 (integrations)
                call.respond(ConnectionTestResult(success = true, message = "Connection exists (test not yet implemented)"))
            }
        }
    }
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
