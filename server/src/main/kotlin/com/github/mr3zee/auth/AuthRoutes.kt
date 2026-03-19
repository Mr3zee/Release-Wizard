package com.github.mr3zee.auth

import com.github.mr3zee.api.*
import com.github.mr3zee.model.UserRole
import com.github.mr3zee.model.UserId
import com.github.mr3zee.plugins.CorrelationIdKey
import com.github.mr3zee.teams.TeamRepository
import io.ktor.http.*
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.*
import io.ktor.server.plugins.ratelimit.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import org.koin.ktor.ext.inject
import org.slf4j.LoggerFactory
import java.security.SecureRandom
import kotlin.time.Clock

private val log = LoggerFactory.getLogger("com.github.mr3zee.auth.AuthRoutes")

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

                log.info("User '{}' logged in", user.username)
                call.respond(UserInfo(username = user.username, id = user.id.value, role = user.role))
            } else {
                log.warn("Failed login attempt for username '{}'", request.username)
                respondUnauthorized(call, "Invalid credentials")
            }
        }

        post(ApiRoutes.Auth.REGISTER) {
            val request = call.receive<RegisterRequest>()
            if (request.username.isBlank()) {
                log.warn("Registration rejected: blank username")
                call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse(error = "Username must not be blank", code = "VALIDATION_ERROR"),
                )
                return@post
            }
            if (request.username.length > 64) {
                log.warn("Registration rejected: username too long")
                call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse(error = "Username must not exceed 64 characters", code = "VALIDATION_ERROR"),
                )
                return@post
            }
            val passwordErrors = passwordValidator.validate(request.password)
            if (passwordErrors.isNotEmpty()) {
                log.warn("Registration rejected for '{}': password validation failed", request.username)
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

                log.info("User '{}' registered with role {}", user.username, user.role)
                call.respond(HttpStatusCode.Created, UserInfo(username = user.username, id = user.id.value, role = user.role))
            } else {
                log.warn("Registration rejected: username '{}' already taken", request.username)
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
            val teamRepository by inject<TeamRepository>()
            val userTeams = teamRepository.getUserTeams(session.userId)
            val teamInfos = userTeams.map { (team, role) ->
                UserTeamInfo(teamId = team.id, teamName = team.name, role = role)
            }
            call.respond(UserInfo(username = session.username, id = session.userId, role = session.role, teams = teamInfos))
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
            requireAdminSession(call) ?: return@get
            val users = authService.listUsers()
            call.respond(UserListResponse(users))
        }

        get(ApiRoutes.Auth.USERS + "/{userId}") {
            requireAdminSession(call) ?: return@get
            val userId = call.parameters["userId"]
                ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse(error = "Missing userId", code = "BAD_REQUEST"))
            val user = authService.getUserById(UserId(userId))
            if (user != null) {
                call.respond(UserInfo(username = user.username, id = user.id.value, role = user.role))
            } else {
                call.respond(HttpStatusCode.NotFound, ErrorResponse(error = "User not found", code = "NOT_FOUND"))
            }
        }

        put(ApiRoutes.Auth.USERS + "/{userId}/role") {
            requireAdminSession(call) ?: return@put
            val userId = call.parameters["userId"] ?: throw IllegalArgumentException("Missing userId")
            val request = call.receive<UpdateUserRoleRequest>()
            val result = authService.safeUpdateUserRole(UserId(userId), request.role)
            result.fold(
                onSuccess = { updated ->
                    if (updated) {
                        log.info("User {} role updated to {}", userId, request.role)
                        call.respond(HttpStatusCode.OK, mapOf("status" to "updated"))
                    } else {
                        log.warn("Role update failed: user {} not found", userId)
                        call.respond(HttpStatusCode.NotFound, ErrorResponse(error = "User not found", code = "NOT_FOUND"))
                    }
                },
                onFailure = {
                    log.warn("Role update rejected for user {}: cannot demote last admin", userId)
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse(error = "Cannot demote the last admin", code = "LAST_ADMIN"))
                },
            )
        }
    }
}

private suspend fun requireAdminSession(call: ApplicationCall): UserSession? {
    val session = call.sessions.get<UserSession>()
    if (session == null) {
        respondUnauthorized(call, "Not authenticated")
        return null
    }
    if (session.role != UserRole.ADMIN) {
        call.respond(HttpStatusCode.Forbidden, ErrorResponse(error = "Admin access required", code = "FORBIDDEN"))
        return null
    }
    return session
}

private suspend fun respondUnauthorized(call: ApplicationCall, message: String) {
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
