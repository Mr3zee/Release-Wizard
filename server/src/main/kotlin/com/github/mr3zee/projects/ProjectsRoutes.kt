package com.github.mr3zee.projects

import com.github.mr3zee.api.*
import com.github.mr3zee.auth.userSession
import com.github.mr3zee.model.TeamId
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("com.github.mr3zee.projects.ProjectsRoutes")

fun Route.projectRoutes() {
    val service by inject<ProjectsService>()

    route(ApiRoutes.Projects.BASE) {
        get {
            val offset = call.request.queryParameters["offset"]?.toIntOrNull() ?: 0
            val limit = (call.request.queryParameters["limit"]?.toIntOrNull() ?: 20).coerceIn(1, 200)
            val search = call.request.queryParameters["q"]
            val teamId = call.request.queryParameters["teamId"]?.let { TeamId(it) }
            val (projects, totalCount) = service.listProjects(call.userSession(), teamId, offset, limit, search)
            call.respond(ProjectListResponse(
                projects = projects,
                pagination = PaginationInfo(totalCount = totalCount, offset = offset, limit = limit),
            ))
        }

        post {
            val request = call.receive<CreateProjectRequest>()
            // PROJ-H4: Blank-name validation moved to service layer
            val project = service.createProject(request, call.userSession())
            log.info("Project created: {} (name='{}')", project.id.value, project.name)
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
                    log.info("Project deleted: {}", id.value)
                    call.respond(HttpStatusCode.NoContent)
                } else {
                    log.warn("Project delete failed: {} not found", id.value)
                    call.respond(HttpStatusCode.NotFound, ErrorResponse(error = "Project not found", code = "NOT_FOUND"))
                }
            }
        }
    }
}

// requireProjectId is in ProjectLockRoutes.kt (internal visibility, shared by both route files)
