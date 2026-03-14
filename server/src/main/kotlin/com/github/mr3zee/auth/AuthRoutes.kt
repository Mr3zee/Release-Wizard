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
import java.security.SecureRandom
import kotlin.time.Clock

fun Route.authRoutes() {
    val authService by inject<AuthService>()
    val passwordValidator by inject<PasswordValidator>()

    rateLimit(RateLimitName("login")) {
        post(ApiRoutes.Auth.LOGIN) {
            val request = call.receive<LoginRequest>()
            val user = authService.validate(request.username, request.password)
            if (user != null) {
                val now = Clock.System.now().toEpochMilliseconds()
                val csrfToken = generateCsrfToken()
                call.sessions.set(
                    UserSession(
                        username = user.username,
                        userId = user.id.value,
                        role = user.role,
                        csrfToken = csrfToken,
                        createdAt = now,
                        lastAccessedAt = now,
                    )
                )

                call.respond(UserInfo(username = user.username, id = user.id.value, role = user.role))
            } else {
                respondUnauthorized(call, "Invalid credentials")
            }
        }

        post(ApiRoutes.Auth.REGISTER) {
            val request = call.receive<RegisterRequest>()
            if (request.username.isBlank()) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse(error = "Username must not be blank", code = "VALIDATION_ERROR"),
                )
                return@post
            }
            if (request.username.length > 64) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse(error = "Username must not exceed 64 characters", code = "VALIDATION_ERROR"),
                )
                return@post
            }
            val passwordErrors = passwordValidator.validate(request.password)
            if (passwordErrors.isNotEmpty()) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse(error = passwordErrors.joinToString("; "), code = "VALIDATION_ERROR"),
                )
                return@post
            }
            val user = authService.register(request.username, request.password)
            if (user != null) {
                val now = Clock.System.now().toEpochMilliseconds()
                val csrfToken = generateCsrfToken()
                call.sessions.set(
                    UserSession(
                        username = user.username,
                        userId = user.id.value,
                        role = user.role,
                        csrfToken = csrfToken,
                        createdAt = now,
                        lastAccessedAt = now,
                    )
                )

                call.respond(HttpStatusCode.Created, UserInfo(username = user.username, id = user.id.value, role = user.role))
            } else {
                call.respond(
                    HttpStatusCode.Conflict,
                    ErrorResponse(error = "Username already taken", code = "USERNAME_TAKEN"),
                )
            }
        }
    }

    get(ApiRoutes.Auth.ME) {
        val session = call.sessions.get<UserSession>()
        if (session != null) {
            call.respond(UserInfo(username = session.username, id = session.userId, role = session.role))
        } else {
            respondUnauthorized(call, "Not authenticated")
        }
    }

    // Authenticated endpoints (requires session)
    authenticate("session-auth") {
        post(ApiRoutes.Auth.LOGOUT) {
            call.sessions.clear<UserSession>()
            call.respond(HttpStatusCode.OK)
        }

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
            val result = authService.safeUpdateUserRole(UserId(userId), request.role)
            result.fold(
                onSuccess = { updated ->
                    if (updated) {
                        call.respond(HttpStatusCode.OK, mapOf("status" to "updated"))
                    } else {
                        call.respond(HttpStatusCode.NotFound, ErrorResponse(error = "User not found", code = "NOT_FOUND"))
                    }
                },
                onFailure = {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse(error = "Cannot demote the last admin", code = "LAST_ADMIN"))
                },
            )
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

private val csrfRandom = SecureRandom()

private fun generateCsrfToken(): String {
    val bytes = ByteArray(32)
    csrfRandom.nextBytes(bytes)
    return bytes.joinToString("") { "%02x".format(it) }
}
