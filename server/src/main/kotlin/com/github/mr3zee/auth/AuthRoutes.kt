package com.github.mr3zee.auth

import com.github.mr3zee.api.*
import com.github.mr3zee.model.UserRole
import com.github.mr3zee.model.UserId
import com.github.mr3zee.plugins.CorrelationIdKey
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.ratelimit.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import org.koin.ktor.ext.inject

fun Route.authRoutes() {
    val authService by inject<AuthService>()

    rateLimit(RateLimitName("login")) {
        post(ApiRoutes.Auth.LOGIN) {
            val request = call.receive<LoginRequest>()
            val user = authService.validate(request.username, request.password)
            if (user != null) {
                call.sessions.set(UserSession(username = user.username, userId = user.id.value, role = user.role))
                call.respond(UserInfo(username = user.username, id = user.id.value, role = user.role))
            } else {
                respondUnauthorized(call, "Invalid credentials")
            }
        }

        post(ApiRoutes.Auth.REGISTER) {
            val request = call.receive<RegisterRequest>()
            if (request.username.isBlank() || request.password.length < 8) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse(error = "Username must not be blank and password must be at least 8 characters", code = "VALIDATION_ERROR"),
                )
                return@post
            }
            val user = authService.register(request.username, request.password)
            if (user != null) {
                call.sessions.set(UserSession(username = user.username, userId = user.id.value, role = user.role))
                call.respond(HttpStatusCode.Created, UserInfo(username = user.username, id = user.id.value, role = user.role))
            } else {
                call.respond(
                    HttpStatusCode.Conflict,
                    ErrorResponse(error = "Username already taken", code = "USERNAME_TAKEN"),
                )
            }
        }
    }

    post(ApiRoutes.Auth.LOGOUT) {
        call.sessions.clear<UserSession>()
        call.respond(HttpStatusCode.OK)
    }

    get(ApiRoutes.Auth.ME) {
        val session = call.sessions.get<UserSession>()
        if (session != null) {
            call.respond(UserInfo(username = session.username, id = session.userId, role = session.role))
        } else {
            respondUnauthorized(call, "Not authenticated")
        }
    }

    // Admin-only user management (requires auth)
    authenticate("session-auth") {
        get(ApiRoutes.Auth.USERS) {
            val session = call.sessions.get<UserSession>()!!
            if (session.role != UserRole.ADMIN) {
                call.respond(HttpStatusCode.Forbidden, ErrorResponse(error = "Admin access required", code = "FORBIDDEN"))
                return@get
            }
            val users = authService.listUsers()
            call.respond(UserListResponse(users))
        }

        put(ApiRoutes.Auth.USERS + "/{userId}/role") {
            val session = call.sessions.get<UserSession>()!!
            if (session.role != UserRole.ADMIN) {
                call.respond(HttpStatusCode.Forbidden, ErrorResponse(error = "Admin access required", code = "FORBIDDEN"))
                return@put
            }
            val userId = call.parameters["userId"] ?: throw IllegalArgumentException("Missing userId")
            val request = call.receive<UpdateUserRoleRequest>()
            // Prevent demoting the last admin
            if (request.role != UserRole.ADMIN) {
                val users = authService.listUsers()
                val adminCount = users.count { it.role == UserRole.ADMIN }
                val isTargetCurrentlyAdmin = users.find { it.id.value == userId }?.role == UserRole.ADMIN
                if (isTargetCurrentlyAdmin && adminCount <= 1) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse(error = "Cannot demote the last admin", code = "LAST_ADMIN"))
                    return@put
                }
            }
            val updated = authService.updateUserRole(UserId(userId), request.role)
            if (updated) {
                call.respond(HttpStatusCode.OK, mapOf("status" to "updated"))
            } else {
                call.respond(HttpStatusCode.NotFound, ErrorResponse(error = "User not found", code = "NOT_FOUND"))
            }
        }
    }
}

private suspend fun respondUnauthorized(call: io.ktor.server.application.ApplicationCall, message: String) {
    val correlationId = call.attributes.getOrNull(CorrelationIdKey)
    call.respond(
        HttpStatusCode.Unauthorized,
        ErrorResponse(
            error = message,
            code = "INVALID_CREDENTIALS",
            correlationId = correlationId,
        ),
    )
}
