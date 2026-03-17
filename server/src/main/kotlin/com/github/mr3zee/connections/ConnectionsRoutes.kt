package com.github.mr3zee.connections

import com.github.mr3zee.ForbiddenException
import com.github.mr3zee.NotFoundException
import com.github.mr3zee.WebhookConfig
import com.github.mr3zee.api.*
import com.github.mr3zee.auth.userSession
import kotlin.coroutines.cancellation.CancellationException
import com.github.mr3zee.model.Connection
import com.github.mr3zee.model.ConnectionId
import com.github.mr3zee.model.ConnectionType
import com.github.mr3zee.model.TeamId
import io.ktor.http.*
import io.ktor.server.application.ApplicationCall
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
            val offset = call.request.queryParameters["offset"]?.toIntOrNull() ?: 0
            val limit = (call.request.queryParameters["limit"]?.toIntOrNull() ?: 20).coerceIn(1, 200)
            val search = call.request.queryParameters["q"]
            val typeStr = call.request.queryParameters["type"]
            val type = typeStr?.let { runCatching { ConnectionType.valueOf(it) }.getOrNull() }
            val teamId = call.request.queryParameters["teamId"]?.let { TeamId(it) }
            val (connections, totalCount) = service.listConnections(
                call.userSession(), teamId, offset, limit, search, type,
            )
            val webhookUrls = connections.mapNotNull { conn ->
                webhookUrl(conn, webhookConfig)?.let { conn.id.value to it }
            }.toMap()
            call.respond(ConnectionListResponse(
                connections = connections,
                webhookUrls = webhookUrls,
                pagination = PaginationInfo(totalCount = totalCount, offset = offset, limit = limit),
            ))
        }

        post {
            val request = call.receive<CreateConnectionRequest>()
            if (request.name.isBlank()) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse(error = "Connection name must not be blank", code = "VALIDATION_ERROR"))
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
                    call.respond(HttpStatusCode.NotFound, ErrorResponse(error = "Connection not found", code = "NOT_FOUND"))
                }
            }

            put {
                val id = call.requireConnectionId() ?: return@put
                val request = call.receive<UpdateConnectionRequest>()
                val connection = service.updateConnection(id, request, call.userSession())
                if (connection != null) {
                    call.respond(ConnectionResponse(connection, webhookUrl(connection, webhookConfig)))
                } else {
                    call.respond(HttpStatusCode.NotFound, ErrorResponse(error = "Connection not found", code = "NOT_FOUND"))
                }
            }

            delete {
                val id = call.requireConnectionId() ?: return@delete
                val deleted = service.deleteConnection(id, call.userSession())
                if (deleted) {
                    call.respond(HttpStatusCode.NoContent)
                } else {
                    call.respond(HttpStatusCode.NotFound, ErrorResponse(error = "Connection not found", code = "NOT_FOUND"))
                }
            }

            post("/test") {
                val id = call.requireConnectionId() ?: return@post
                val result = service.testConnection(id, call.userSession())
                if (result == null) {
                    call.respond(HttpStatusCode.NotFound, ErrorResponse(error = "Connection not found", code = "NOT_FOUND"))
                    return@post
                }
                call.respond(result)
            }

            get("/teamcity/build-types") {
                val id = call.requireConnectionId() ?: return@get
                try {
                    val result = service.fetchExternalConfigs(id, ConnectionType.TEAMCITY, call.userSession())
                    call.respond(result)
                } catch (e: CancellationException) { throw e }
                  catch (e: NotFoundException) { throw e }
                  catch (e: ForbiddenException) { throw e }
                  catch (e: IllegalArgumentException) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse(error = e.message ?: "Invalid request", code = "BAD_REQUEST"))
                } catch (e: UnsupportedOperationException) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse(error = e.message ?: "Not supported", code = "BAD_REQUEST"))
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.BadGateway, ErrorResponse(error = "Failed to fetch build types", code = "BAD_GATEWAY"))
                }
            }

            get("/github/workflows") {
                val id = call.requireConnectionId() ?: return@get
                try {
                    val result = service.fetchExternalConfigs(id, ConnectionType.GITHUB, call.userSession())
                    call.respond(result)
                } catch (e: CancellationException) { throw e }
                  catch (e: NotFoundException) { throw e }
                  catch (e: ForbiddenException) { throw e }
                  catch (e: IllegalArgumentException) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse(error = e.message ?: "Invalid request", code = "BAD_REQUEST"))
                } catch (e: UnsupportedOperationException) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse(error = e.message ?: "Not supported", code = "BAD_REQUEST"))
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.BadGateway, ErrorResponse(error = "Failed to fetch workflows", code = "BAD_GATEWAY"))
                }
            }

            get("/github/workflows/{workflowFile}/parameters") {
                val id = call.requireConnectionId() ?: return@get
                val workflowFile = call.parameters["workflowFile"]
                if (workflowFile.isNullOrBlank()) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse(error = "Missing workflowFile", code = "VALIDATION_ERROR"))
                    return@get
                }
                try {
                    val result = service.fetchExternalConfigParameters(id, workflowFile, call.userSession())
                    call.respond(result)
                } catch (e: CancellationException) { throw e }
                  catch (e: NotFoundException) { throw e }
                  catch (e: ForbiddenException) { throw e }
                  catch (e: Exception) {
                    call.respond(HttpStatusCode.BadGateway, ErrorResponse(error = "Failed to fetch workflow parameters", code = "BAD_GATEWAY"))
                }
            }

            get("/teamcity/build-types/{buildTypeId}/parameters") {
                val id = call.requireConnectionId() ?: return@get
                val buildTypeId = call.parameters["buildTypeId"]
                if (buildTypeId.isNullOrBlank()) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse(error = "Missing buildTypeId", code = "VALIDATION_ERROR"))
                    return@get
                }
                try {
                    val result = service.fetchExternalConfigParameters(id, buildTypeId, call.userSession())
                    call.respond(result)
                } catch (e: CancellationException) { throw e }
                  catch (e: NotFoundException) { throw e }
                  catch (e: ForbiddenException) { throw e }
                  catch (e: IllegalArgumentException) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse(error = e.message ?: "Invalid request", code = "BAD_REQUEST"))
                } catch (e: UnsupportedOperationException) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse(error = e.message ?: "Not supported", code = "BAD_REQUEST"))
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.BadGateway, ErrorResponse(error = "Failed to fetch parameters", code = "BAD_GATEWAY"))
                }
            }
        }
    }
}

@Suppress("UNUSED_PARAMETER")
private fun webhookUrl(connection: Connection, webhookConfig: WebhookConfig): String? {
    // TC/GH webhook endpoints removed — build completion is now via server-side polling
    return null
}

private suspend fun ApplicationCall.requireConnectionId(): ConnectionId? {
    val raw = parameters["id"]
    if (raw == null) {
        respond(HttpStatusCode.BadRequest, ErrorResponse(error = "Missing id", code = "VALIDATION_ERROR"))
        return null
    }
    return try {
        UUID.fromString(raw)
        ConnectionId(raw)
    } catch (_: IllegalArgumentException) {
        respond(HttpStatusCode.BadRequest, ErrorResponse(error = "Invalid connection ID format", code = "VALIDATION_ERROR"))
        null
    }
}
