package com.github.mr3zee.usernotifications

import com.github.mr3zee.api.ApiRoutes
import com.github.mr3zee.api.ErrorResponse
import com.github.mr3zee.auth.userSession
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject
import java.util.UUID

private fun isValidUuid(value: String): Boolean = try {
    UUID.fromString(value); true
} catch (_: IllegalArgumentException) {
    false
}

fun Route.userNotificationRoutes() {
    val service by inject<UserNotificationService>()

    get(ApiRoutes.UserNotifications.BASE) {
        val session = call.userSession()
        val offset = (call.request.queryParameters["offset"]?.toIntOrNull() ?: 0).coerceAtLeast(0)
        val limit = (call.request.queryParameters["limit"]?.toIntOrNull() ?: 20).coerceIn(1, 100)
        val response = service.listNotifications(session.userId, offset, limit)
        call.respond(response)
    }

    get(ApiRoutes.UserNotifications.UNREAD_COUNT) {
        val session = call.userSession()
        val response = service.getUnreadCount(session.userId)
        call.respond(response)
    }

    post(ApiRoutes.UserNotifications.BASE + "/{id}/read") {
        val session = call.userSession()
        val notificationId = call.parameters["id"]
            ?: return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse(error = "Missing notification ID", code = "BAD_REQUEST"))
        if (!isValidUuid(notificationId)) {
            return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse(error = "Invalid notification ID", code = "BAD_REQUEST"))
        }
        val updated = service.markAsRead(notificationId, session.userId)
        if (updated) {
            call.respond(HttpStatusCode.OK, mapOf("status" to "read"))
        } else {
            call.respond(HttpStatusCode.NotFound, ErrorResponse(error = "Notification not found", code = "NOT_FOUND"))
        }
    }

    post(ApiRoutes.UserNotifications.MARK_ALL_READ) {
        val session = call.userSession()
        val count = service.markAllAsRead(session.userId)
        call.respond(HttpStatusCode.OK, mapOf("status" to "read", "count" to count.toString()))
    }

    delete(ApiRoutes.UserNotifications.DELETE_ALL_READ) {
        val session = call.userSession()
        val count = service.deleteAllRead(session.userId)
        call.respond(HttpStatusCode.OK, mapOf("status" to "deleted", "count" to count.toString()))
    }

    delete(ApiRoutes.UserNotifications.BASE + "/{id}") {
        val session = call.userSession()
        val notificationId = call.parameters["id"]
            ?: return@delete call.respond(HttpStatusCode.BadRequest, ErrorResponse(error = "Missing notification ID", code = "BAD_REQUEST"))
        if (!isValidUuid(notificationId)) {
            return@delete call.respond(HttpStatusCode.BadRequest, ErrorResponse(error = "Invalid notification ID", code = "BAD_REQUEST"))
        }
        val deleted = service.deleteNotification(notificationId, session.userId)
        if (deleted) {
            call.respond(HttpStatusCode.OK, mapOf("status" to "deleted"))
        } else {
            call.respond(HttpStatusCode.NotFound, ErrorResponse(error = "Notification not found", code = "NOT_FOUND"))
        }
    }
}
