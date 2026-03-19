package com.github.mr3zee.notifications

import com.github.mr3zee.NotFoundException
import com.github.mr3zee.api.ApiRoutes
import com.github.mr3zee.api.CreateNotificationConfigRequest
import com.github.mr3zee.api.NotificationConfigListResponse
import com.github.mr3zee.auth.userSession
import com.github.mr3zee.util.requireProjectId
import com.github.mr3zee.util.requireUuidParam
import io.ktor.http.*
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("com.github.mr3zee.notifications.NotificationRoutes")

fun Route.notificationRoutes() {
    val service by inject<NotificationService>()

    route(ApiRoutes.Notifications.byProject("{projectId}")) {
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
            log.info("Notification config created: {} for project {}", response.id, projectId.value)
            call.respond(HttpStatusCode.Created, response)
        }

        route("/{notificationId}") {
            delete {
                val notificationId = call.requireNotificationId()
                val deleted = service.delete(notificationId, call.userSession())
                if (!deleted) {
                    throw NotFoundException("Notification config not found: $notificationId")
                }
                log.info("Notification config deleted: {}", notificationId)
                call.respond(HttpStatusCode.NoContent)
            }
        }
    }
}

private fun ApplicationCall.requireNotificationId(): String {
    return requireUuidParam("notificationId")
}
