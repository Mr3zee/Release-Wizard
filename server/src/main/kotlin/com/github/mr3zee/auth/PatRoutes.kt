package com.github.mr3zee.auth

import com.github.mr3zee.ForbiddenException
import com.github.mr3zee.NotFoundException
import com.github.mr3zee.api.ApiRoutes
import com.github.mr3zee.api.CreatePatRequest
import com.github.mr3zee.model.UserId
import com.github.mr3zee.model.UserRole
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject

fun Route.patRoutes() {
    val patService by inject<PatService>()

    // --- User routes: manage own tokens ---
    route(ApiRoutes.Pats.BASE) {
        get {
            val session = call.userSession()
            val tokens = patService.listByUser(UserId(session.userId))
            call.respond(tokens)
        }

        post {
            val session = call.userSession()
            val request = call.receive<CreatePatRequest>()
            val response = patService.create(UserId(session.userId), request.name, request.expiresInDays)
            call.respond(HttpStatusCode.Created, response)
        }

        delete("/{id}") {
            val session = call.userSession()
            val patId = call.parameters["id"] ?: throw IllegalArgumentException("Missing token ID")
            val revoked = patService.revoke(patId, UserId(session.userId))
            if (revoked) {
                call.respond(HttpStatusCode.NoContent)
            } else {
                throw NotFoundException("Token not found or already revoked")
            }
        }
    }

    // --- Admin routes: manage any user's tokens ---
    route("${ApiRoutes.API_V1}/auth/users/{userId}/tokens") {
        get {
            requireAdmin()
            val userId = call.parameters["userId"] ?: throw IllegalArgumentException("Missing user ID")
            val tokens = patService.listByUser(UserId(userId))
            call.respond(tokens)
        }

        delete("/{tokenId}") {
            requireAdmin()
            val tokenId = call.parameters["tokenId"] ?: throw IllegalArgumentException("Missing token ID")
            val revoked = patService.adminRevoke(tokenId)
            if (revoked) {
                call.respond(HttpStatusCode.NoContent)
            } else {
                throw NotFoundException("Token not found or already revoked")
            }
        }
    }
}

private fun RoutingContext.requireAdmin() {
    val session = call.userSession()
    if (session.role != UserRole.ADMIN) {
        throw ForbiddenException("Admin access required")
    }
}
