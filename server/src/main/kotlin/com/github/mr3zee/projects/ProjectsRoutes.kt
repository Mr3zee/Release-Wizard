package com.github.mr3zee.projects

import com.github.mr3zee.api.*
import com.github.mr3zee.auth.userSession
import com.github.mr3zee.model.ProjectId
import io.ktor.http.*
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject
import java.util.*

fun Route.projectRoutes() {
    val service by inject<ProjectsService>()

    route(ApiRoutes.Projects.BASE) {
        get {
            val offset = call.request.queryParameters["offset"]?.toIntOrNull() ?: 0
            val limit = (call.request.queryParameters["limit"]?.toIntOrNull() ?: 20).coerceIn(1, 200)
            val search = call.request.queryParameters["q"]
            val (projects, totalCount) = service.listProjects(call.userSession(), offset, limit, search)
            call.respond(ProjectListResponse(
                projects = projects,
                pagination = PaginationInfo(totalCount = totalCount, offset = offset, limit = limit),
            ))
        }

        post {
            val request = call.receive<CreateProjectRequest>()
            if (request.name.isBlank()) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse(error = "Project name must not be blank", code = "VALIDATION_ERROR"))
                return@post
            }
            val project = service.createProject(request, call.userSession())
            call.respond(HttpStatusCode.Created, ProjectResponse(project))
        }

        route("/{id}") {
            get {
                val id = call.requireProjectId() ?: return@get
                val project = service.getProject(id, call.userSession())
                if (project != null) {
                    call.respond(ProjectResponse(project))
                } else {
                    call.respond(HttpStatusCode.NotFound, ErrorResponse(error = "Project not found", code = "NOT_FOUND"))
                }
            }

            put {
                val id = call.requireProjectId() ?: return@put
                val request = call.receive<UpdateProjectRequest>()
                val project = service.updateProject(id, request, call.userSession())
                if (project != null) {
                    call.respond(ProjectResponse(project))
                } else {
                    call.respond(HttpStatusCode.NotFound, ErrorResponse(error = "Project not found", code = "NOT_FOUND"))
                }
            }

            delete {
                val id = call.requireProjectId() ?: return@delete
                val deleted = service.deleteProject(id, call.userSession())
                if (deleted) {
                    call.respond(HttpStatusCode.NoContent)
                } else {
                    call.respond(HttpStatusCode.NotFound, ErrorResponse(error = "Project not found", code = "NOT_FOUND"))
                }
            }
        }
    }
}

// todo claude: duplicate function
private suspend fun ApplicationCall.requireProjectId(): ProjectId? {
    val raw = parameters["id"]
    if (raw == null) {
        respond(HttpStatusCode.BadRequest, ErrorResponse(error = "Missing id", code = "VALIDATION_ERROR"))
        return null
    }
    return try {
        UUID.fromString(raw)
        ProjectId(raw)
    } catch (_: IllegalArgumentException) {
        respond(HttpStatusCode.BadRequest, ErrorResponse(error = "Invalid project ID format", code = "VALIDATION_ERROR"))
        null
    }
}
