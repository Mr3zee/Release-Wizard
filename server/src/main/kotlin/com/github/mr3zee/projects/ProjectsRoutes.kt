package com.github.mr3zee.projects

import com.github.mr3zee.api.*
import com.github.mr3zee.auth.userSession
import com.github.mr3zee.model.ProjectId
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject
import java.util.UUID

fun Route.projectRoutes() {
    val service by inject<ProjectsService>()

    route(ApiRoutes.Projects.BASE) {
        get {
            val projects = service.listProjects(call.userSession())
            call.respond(ProjectListResponse(projects))
        }

        post {
            val request = call.receive<CreateProjectRequest>()
            if (request.name.isBlank()) {
                call.respond(HttpStatusCode.BadRequest, "Project name must not be blank")
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
                    call.respond(HttpStatusCode.NotFound, "Project not found")
                }
            }

            put {
                val id = call.requireProjectId() ?: return@put
                val request = call.receive<UpdateProjectRequest>()
                val project = service.updateProject(id, request, call.userSession())
                if (project != null) {
                    call.respond(ProjectResponse(project))
                } else {
                    call.respond(HttpStatusCode.NotFound, "Project not found")
                }
            }

            delete {
                val id = call.requireProjectId() ?: return@delete
                val deleted = service.deleteProject(id, call.userSession())
                if (deleted) {
                    call.respond(HttpStatusCode.NoContent)
                } else {
                    call.respond(HttpStatusCode.NotFound, "Project not found")
                }
            }
        }
    }
}

private suspend fun io.ktor.server.application.ApplicationCall.requireProjectId(): ProjectId? {
    val raw = parameters["id"]
    if (raw == null) {
        respond(HttpStatusCode.BadRequest, "Missing id")
        return null
    }
    return try {
        UUID.fromString(raw)
        ProjectId(raw)
    } catch (_: IllegalArgumentException) {
        respond(HttpStatusCode.BadRequest, "Invalid project ID format")
        null
    }
}
