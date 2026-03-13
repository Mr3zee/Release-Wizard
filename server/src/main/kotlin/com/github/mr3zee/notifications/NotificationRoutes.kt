package com.github.mr3zee.notifications

import com.github.mr3zee.NotFoundException
import com.github.mr3zee.api.CreateNotificationConfigRequest
import com.github.mr3zee.api.NotificationConfigListResponse
import com.github.mr3zee.auth.userSession
import com.github.mr3zee.model.ProjectId
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject
import java.util.UUID

fun Route.notificationRoutes() {
    val service by inject<NotificationService>()

    route("/api/v1/projects/{projectId}/notifications") {
        get {
            val projectId = call.requireProjectId()
            val configs = service.listByProject(projectId, call.userSession())
            call.respond(NotificationConfigListResponse(configs))
        }

        post {
            val projectId = call.requireProjectId()
            val request = call.receive<CreateNotificationConfigRequest>()
            if (request.projectId != projectId) {
                throw IllegalArgumentException("Project ID in URL does not match request body")
            }
            val response = service.create(request, call.userSession())
            call.respond(HttpStatusCode.Created, response)
        }

        route("/{notificationId}") {
            delete {
                val notificationId = call.requireNotificationId()
                val deleted = service.delete(notificationId, call.userSession())
                if (!deleted) {
                    throw NotFoundException("Notification config not found: $notificationId")
                }
                call.respond(HttpStatusCode.NoContent)
            }
        }
    }
}

private fun io.ktor.server.application.ApplicationCall.requireProjectId(): ProjectId {
    val raw = parameters["projectId"]
        ?: throw IllegalArgumentException("Missing projectId")
    try {
        UUID.fromString(raw)
    } catch (_: IllegalArgumentException) {
        throw IllegalArgumentException("Invalid project ID format")
    }
    return ProjectId(raw)
}

private fun io.ktor.server.application.ApplicationCall.requireNotificationId(): String {
    val raw = parameters["notificationId"]
        ?: throw IllegalArgumentException("Missing notificationId")
    try {
        UUID.fromString(raw)
    } catch (_: IllegalArgumentException) {
        throw IllegalArgumentException("Invalid notification ID format")
    }
    return raw
}
