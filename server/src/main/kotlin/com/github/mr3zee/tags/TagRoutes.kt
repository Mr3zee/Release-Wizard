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
            val tags = service.listAllTags()
            call.respond(TagListResponse(tags))
        }

        route("/{name}") {
            put {
                // todo claude: duplicate 6 lines
                val session = call.sessions.get<UserSession>()
                    ?: throw ForbiddenException("Not authenticated")
                if (session.role != UserRole.ADMIN) {
                    throw ForbiddenException("Admin access required for tag management")
                }
                val name = call.parameters["name"] ?: throw IllegalArgumentException("Missing tag name")
                val request = call.receive<RenameTagRequest>()
                val updated = service.renameTag(name, request.newName)
                call.respond(mapOf("updated" to updated))
            }

            delete {
                // todo claude: duplicate 6 lines
                val session = call.sessions.get<UserSession>()
                    ?: throw ForbiddenException("Not authenticated")
                if (session.role != UserRole.ADMIN) {
                    throw ForbiddenException("Admin access required for tag management")
                }
                val name = call.parameters["name"] ?: throw IllegalArgumentException("Missing tag name")
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
