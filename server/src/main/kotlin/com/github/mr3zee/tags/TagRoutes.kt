package com.github.mr3zee.tags

import com.github.mr3zee.ForbiddenException
import com.github.mr3zee.api.ApiRoutes
import com.github.mr3zee.api.RenameTagRequest
import com.github.mr3zee.api.TagListResponse
import com.github.mr3zee.auth.UserSession
import com.github.mr3zee.model.UserRole
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import org.koin.ktor.ext.inject

fun Route.tagRoutes() {
    val service by inject<TagService>()

    route(ApiRoutes.Tags.BASE) {
        get {
            // Global tag list (admin only for cross-team view; team-scoped via team routes)
            requireTagAdmin()
            val tags = service.listAllTags()
            call.respond(TagListResponse(tags))
        }

        route("/{name}") {
            put {
                requireTagAdmin()
                val name = call.parameters["name"] ?: throw IllegalArgumentException("Missing tag name")
                val request = call.receive<RenameTagRequest>()
                // Admin global rename (no team scoping)
                val updated = service.renameTag(name, request.newName)
                call.respond(mapOf("updated" to updated))
            }

            delete {
                requireTagAdmin()
                val name = call.parameters["name"] ?: throw IllegalArgumentException("Missing tag name")
                // Admin global delete (no team scoping)
                val deleted = service.deleteTag(name)
                if (deleted > 0) {
                    call.respond(HttpStatusCode.NoContent)
                } else {
                    call.respond(HttpStatusCode.NotFound, "Tag not found")
                }
            }
        }
    }
}

private fun RoutingContext.requireTagAdmin() {
    val session = call.sessions.get<UserSession>()
        ?: throw ForbiddenException("Not authenticated")
    if (session.role != UserRole.ADMIN) {
        throw ForbiddenException("Admin access required for tag management")
    }
}
