package com.github.mr3zee.teams

import com.github.mr3zee.api.*
import com.github.mr3zee.auth.userSession
import com.github.mr3zee.model.TeamId
import io.ktor.http.*
import io.ktor.server.plugins.ratelimit.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject
import java.util.UUID

fun Route.teamRoutes() {
    val service by inject<TeamService>()
    // TEAM-M1: Inject audit/tag dependencies at route scope instead of per-request inline injection
    val auditRepo by inject<com.github.mr3zee.audit.AuditRepository>()
    val teamAccessService by inject<TeamAccessService>()
    val tagService by inject<com.github.mr3zee.tags.TagService>()

    route(ApiRoutes.Teams.BASE) {
        post {
            val request = call.receive<CreateTeamRequest>()
            val response = service.createTeam(request, call.userSession())
            call.respond(HttpStatusCode.Created, response)
        }

        get {
            val offset = call.request.queryParameters["offset"]?.toIntOrNull() ?: 0
            val limit = (call.request.queryParameters["limit"]?.toIntOrNull() ?: 20).coerceIn(1, 200)
            val search = call.request.queryParameters["q"]
            call.respond(service.listTeams(offset, limit, search))
        }

        route("/{teamId}") {
            get {
                val teamId = call.requireTeamId() ?: return@get
                call.respond(service.getTeamDetail(teamId, call.userSession()))
            }

            put {
                val teamId = call.requireTeamId() ?: return@put
                val request = call.receive<UpdateTeamRequest>()
                call.respond(service.updateTeam(teamId, request, call.userSession()))
            }

            delete {
                val teamId = call.requireTeamId() ?: return@delete
                service.deleteTeam(teamId, call.userSession())
                call.respond(HttpStatusCode.NoContent)
            }

            // Members
            route("/members") {
                get {
                    val teamId = call.requireTeamId() ?: return@get
                    call.respond(service.listMembers(teamId, call.userSession()))
                }

                route("/{userId}") {
                    put {
                        val teamId = call.requireTeamId() ?: return@put
                        val userId = call.parameters["userId"]
                            ?: return@put call.respond(HttpStatusCode.BadRequest, ErrorResponse(error = "Missing userId", code = "VALIDATION_ERROR"))
                        val request = call.receive<UpdateMemberRoleRequest>()
                        service.updateMemberRole(teamId, userId, request, call.userSession())
                        call.respond(HttpStatusCode.OK, mapOf("status" to "updated"))
                    }

                    delete {
                        val teamId = call.requireTeamId() ?: return@delete
                        val userId = call.parameters["userId"]
                            ?: return@delete call.respond(HttpStatusCode.BadRequest, ErrorResponse(error = "Missing userId", code = "VALIDATION_ERROR"))
                        service.removeMember(teamId, userId, call.userSession())
                        call.respond(HttpStatusCode.NoContent)
                    }
                }
            }

            // Leave
            post("/leave") {
                val teamId = call.requireTeamId() ?: return@post
                service.leaveTeam(teamId, call.userSession())
                call.respond(HttpStatusCode.OK, mapOf("status" to "left"))
            }

            // Invites
            route("/invites") {
                // TEAM-M7: Rate limit on invite creation
                rateLimit(RateLimitName("invite")) {
                post {
                    val teamId = call.requireTeamId() ?: return@post
                    val request = call.receive<CreateInviteRequest>()
                    val invite = service.inviteUser(teamId, request, call.userSession())
                    call.respond(HttpStatusCode.Created, invite)
                }
                } // rateLimit("invite")

                get {
                    val teamId = call.requireTeamId() ?: return@get
                    call.respond(service.listTeamInvites(teamId, call.userSession()))
                }

                delete("/{inviteId}") {
                    val teamId = call.requireTeamId() ?: return@delete
                    val inviteId = call.parameters["inviteId"]
                        ?: return@delete call.respond(HttpStatusCode.BadRequest, ErrorResponse(error = "Missing inviteId", code = "VALIDATION_ERROR"))
                    service.cancelInvite(teamId, inviteId, call.userSession())
                    call.respond(HttpStatusCode.NoContent)
                }
            }

            // Join Requests
            route("/join-requests") {
                // TEAM-M7: Rate limit on join-request creation
                rateLimit(RateLimitName("invite")) {
                post {
                    val teamId = call.requireTeamId() ?: return@post
                    val joinRequest = service.submitJoinRequest(teamId, call.userSession())
                    call.respond(HttpStatusCode.Created, joinRequest)
                }
                } // rateLimit("invite")

                get {
                    val teamId = call.requireTeamId() ?: return@get
                    call.respond(service.listJoinRequests(teamId, call.userSession()))
                }

                post("/{requestId}/approve") {
                    val teamId = call.requireTeamId() ?: return@post
                    val requestId = call.parameters["requestId"]
                        ?: return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse(error = "Missing requestId", code = "VALIDATION_ERROR"))
                    service.approveJoinRequest(teamId, requestId, call.userSession())
                    call.respond(HttpStatusCode.OK, mapOf("status" to "approved"))
                }

                post("/{requestId}/reject") {
                    val teamId = call.requireTeamId() ?: return@post
                    val requestId = call.parameters["requestId"]
                        ?: return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse(error = "Missing requestId", code = "VALIDATION_ERROR"))
                    service.rejectJoinRequest(teamId, requestId, call.userSession())
                    call.respond(HttpStatusCode.OK, mapOf("status" to "rejected"))
                }
            }

            // Audit
            get("/audit") {
                val teamId = call.requireTeamId() ?: return@get
                val offset = call.request.queryParameters["offset"]?.toIntOrNull() ?: 0
                val limit = (call.request.queryParameters["limit"]?.toIntOrNull() ?: 50).coerceIn(1, 200)
                teamAccessService.checkMembership(teamId, call.userSession())
                val (events, totalCount) = auditRepo.findByTeam(teamId, offset, limit)
                call.respond(AuditEventListResponse(
                    events = events,
                    pagination = PaginationInfo(totalCount = totalCount, offset = offset, limit = limit),
                ))
            }

            // Tags (team-scoped)
            route("/tags") {
                get {
                    val teamId = call.requireTeamId() ?: return@get
                    teamAccessService.checkMembership(teamId, call.userSession())
                    val tags = tagService.listTagsByTeam(teamId.value)
                    call.respond(TagListResponse(tags))
                }

                put("/{name}") {
                    val teamId = call.requireTeamId() ?: return@put
                    val session = call.userSession()
                    teamAccessService.checkTeamLead(teamId, session)
                    val name = call.parameters["name"] ?: throw IllegalArgumentException("Missing tag name")
                    val request = call.receive<RenameTagRequest>()
                    val updated = tagService.renameTag(name, request.newName, teamId.value, session)
                    call.respond(mapOf("updated" to updated))
                }

                delete("/{name}") {
                    val teamId = call.requireTeamId() ?: return@delete
                    val session = call.userSession()
                    teamAccessService.checkTeamLead(teamId, session)
                    val name = call.parameters["name"] ?: throw IllegalArgumentException("Missing tag name")
                    val deleted = tagService.deleteTag(name, teamId.value, session)
                    if (deleted > 0) call.respond(HttpStatusCode.NoContent)
                    else call.respond(HttpStatusCode.NotFound, ErrorResponse(error = "Tag not found", code = "NOT_FOUND"))
                }
            }
        }
    }
}

fun Route.myInviteRoutes() {
    val service by inject<TeamService>()

    get(ApiRoutes.Auth.MyInvites.BASE) {
        call.respond(service.getMyInvites(call.userSession()))
    }

    post(ApiRoutes.Auth.MyInvites.BASE + "/{inviteId}/accept") {
        val inviteId = call.parameters["inviteId"]
            ?: return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse(error = "Missing inviteId", code = "VALIDATION_ERROR"))
        service.acceptInvite(inviteId, call.userSession())
        call.respond(HttpStatusCode.OK, mapOf("status" to "accepted"))
    }

    post(ApiRoutes.Auth.MyInvites.BASE + "/{inviteId}/decline") {
        val inviteId = call.parameters["inviteId"]
            ?: return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse(error = "Missing inviteId", code = "VALIDATION_ERROR"))
        service.declineInvite(inviteId, call.userSession())
        call.respond(HttpStatusCode.OK, mapOf("status" to "declined"))
    }
}

private suspend fun io.ktor.server.application.ApplicationCall.requireTeamId(): TeamId? {
    val raw = parameters["teamId"]
    if (raw == null) {
        respond(HttpStatusCode.BadRequest, ErrorResponse(error = "Missing teamId", code = "VALIDATION_ERROR"))
        return null
    }
    return try {
        UUID.fromString(raw)
        TeamId(raw)
    } catch (_: IllegalArgumentException) {
        respond(HttpStatusCode.BadRequest, ErrorResponse(error = "Invalid team ID format", code = "VALIDATION_ERROR"))
        null
    }
}
