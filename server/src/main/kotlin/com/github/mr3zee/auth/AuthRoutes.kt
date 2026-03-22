package com.github.mr3zee.auth

import com.github.mr3zee.PasswordPolicyConfig
import com.github.mr3zee.WebhookConfig
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
import kotlin.time.Clock

private val log = LoggerFactory.getLogger("com.github.mr3zee.auth.AuthRoutes")

fun Route.authRoutes() {
    val authService by inject<AuthService>()
    val passwordValidator by inject<PasswordValidator>()
    val accountLockout by inject<AccountLockoutService>()
    val teamRepository by inject<TeamRepository>()
    val passwordResetService by inject<PasswordResetService>()
    val passwordPolicyConfig by inject<PasswordPolicyConfig>()
    val webhookConfig by inject<WebhookConfig>()
    val oauthService by inject<OAuthService>()
    val oauthConfig by inject<com.github.mr3zee.OAuthConfig>()

    // Public: password policy + available OAuth providers (no auth needed)
    rateLimit(RateLimitName("authenticated-api")) {
        get(ApiRoutes.Auth.PASSWORD_POLICY) {
            call.response.header(HttpHeaders.CacheControl, "public, max-age=3600")
            val availableProviders = buildList {
                if (oauthConfig.isGoogleConfigured) add(OAuthProvider.GOOGLE)
            }
            call.respond(
                PasswordPolicyResponse(
                    minLength = passwordPolicyConfig.minLength,
                    maxLength = PasswordValidator.MAX_PASSWORD_LENGTH,
                    requireUppercase = passwordPolicyConfig.requireUppercase,
                    requireDigit = passwordPolicyConfig.requireDigit,
                    requireSpecial = passwordPolicyConfig.requireSpecial,
                    oauthProviders = availableProviders,
                )
            )
        }
    }

    rateLimit(RateLimitName("login")) {
        post(ApiRoutes.Auth.LOGIN) {
            val request = call.receive<LoginRequest>()

            // AUTH-H4: Per-username account lockout with exponential backoff
            val lockRemaining = accountLockout.checkLocked(request.username)
            if (lockRemaining != null) {
                log.warn("Login blocked for '{}': account locked for {}", request.username, lockRemaining)
                call.response.header(HttpHeaders.RetryAfter, lockRemaining.inWholeSeconds.toString())
                call.respond(
                    HttpStatusCode.TooManyRequests,
                    ErrorResponse(
                        error = "Account temporarily locked. Try again later.",
                        code = "ACCOUNT_LOCKED",
                    ),
                )
                return@post
            }

            when (val result = authService.validate(request.username, request.password)) {
                is LoginResult.Success -> {
                    val user = result.user
                    accountLockout.recordSuccess(request.username)
                    val now = Clock.System.now().toEpochMilliseconds()
                    val csrfToken = generateCsrfToken()
                    call.sessions.set(
                        UserSession(
                            username = user.username,
                            userId = user.id.value,
                            role = user.role,
                            csrfToken = csrfToken,
                            clientType = request.clientType,
                            createdAt = now,
                            lastAccessedAt = now,
                            approved = user.approved,
                        )
                    )

                    log.info("User '{}' logged in (approved={})", user.username, user.approved)
                    call.respond(UserInfo(username = user.username, id = user.id.value, role = user.role, approved = user.approved))
                }
                is LoginResult.OAuthOnly -> {
                    // SEC-L1: Same HTTP status and error code as invalid credentials
                    // to prevent account type enumeration.
                    accountLockout.recordFailure(request.username)
                    log.warn("Failed login attempt for username '{}'", request.username)
                    respondUnauthorized(call, "Invalid credentials")
                }
                is LoginResult.InvalidCredentials -> {
                    accountLockout.recordFailure(request.username)
                    log.warn("Failed login attempt for username '{}'", request.username)
                    respondUnauthorized(call, "Invalid credentials")
                }
            }
        }

        post(ApiRoutes.Auth.REGISTER) {
            val request = call.receive<RegisterRequest>()
            val trimmedUsername = request.username.trim()
            if (trimmedUsername.isBlank()) {
                log.warn("Registration rejected: blank username")
                call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse(error = "Username must not be blank", code = "VALIDATION_ERROR"),
                )
                return@post
            }
            if (trimmedUsername.length > 64) {
                log.warn("Registration rejected: username too long")
                call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse(error = "Username must not exceed 64 characters", code = "VALIDATION_ERROR"),
                )
                return@post
            }
            val passwordErrors = passwordValidator.validate(request.password)
            if (passwordErrors.isNotEmpty()) {
                log.warn("Registration rejected for '{}': password validation failed", trimmedUsername)
                call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse(error = passwordErrors.joinToString("; "), code = "VALIDATION_ERROR"),
                )
                return@post
            }
            val user = authService.register(trimmedUsername, request.password)
            if (user != null) {
                val now = Clock.System.now().toEpochMilliseconds()
                val csrfToken = generateCsrfToken()
                call.sessions.set(
                    UserSession(
                        username = user.username,
                        userId = user.id.value,
                        role = user.role,
                        csrfToken = csrfToken,
                        clientType = request.clientType,
                        createdAt = now,
                        lastAccessedAt = now,
                        approved = user.approved,
                    )
                )

                log.info("User '{}' registered with role {} (approved={})", user.username, user.role, user.approved)
                call.respond(HttpStatusCode.Created, UserInfo(username = user.username, id = user.id.value, role = user.role, approved = user.approved))
            } else {
                log.warn("Registration rejected: username '{}' already taken", trimmedUsername)
                call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse(error = "Username already taken", code = "USERNAME_TAKEN"),
                )
            }
        }
    }

    // AUTH-H1: /me moved inside authenticate block to use proper session validation
    authenticate("session-auth") {
        get(ApiRoutes.Auth.ME) {
            val session = call.userSession()
            // Fetch full user from DB to include createdAt
            val user = authService.getUserById(UserId(session.userId))
            val userTeams = teamRepository.getUserTeams(session.userId)
            val teamInfos = userTeams.map { (team, role) ->
                UserTeamInfo(teamId = team.id, teamName = team.name, role = role)
            }
            val userId = UserId(session.userId)
            val hasPassword = oauthService.hasPassword(userId)
            val oauthProviders = oauthService.getOAuthProviders(userId)
            call.respond(
                UserInfo(
                    username = session.username,
                    id = session.userId,
                    role = session.role,
                    teams = teamInfos,
                    createdAt = user?.createdAt,
                    hasPassword = hasPassword,
                    oauthProviders = oauthProviders,
                    approved = user?.approved ?: session.approved,
                )
            )
        }

        post(ApiRoutes.Auth.LOGOUT) {
            call.sessions.clear<UserSession>()
            call.respond(HttpStatusCode.OK)
        }

        put(ApiRoutes.Auth.CHANGE_USERNAME) {
            val session = call.userSession()
            val request = call.receive<ChangeUsernameRequest>()

            // Validate username format (same rules as register)
            if (request.newUsername.isBlank()) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse(error = "Username must not be blank", code = "VALIDATION_ERROR"),
                )
                return@put
            }
            if (request.newUsername.length > 64) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse(error = "Username must not exceed 64 characters", code = "VALIDATION_ERROR"),
                )
                return@put
            }

            val result = authService.changeUsername(
                UserId(session.userId),
                request.newUsername,
                request.currentPassword,
            )
            result.fold(
                onSuccess = { updatedUser ->
                    // Update the session with the new username
                    call.sessions.set(session.copy(username = updatedUser.username))
                    log.info("User '{}' changed username to '{}'", session.userId, updatedUser.username)
                    val userTeams = teamRepository.getUserTeams(session.userId)
                    val teamInfos = userTeams.map { (team, role) ->
                        UserTeamInfo(teamId = team.id, teamName = team.name, role = role)
                    }
                    val userId = UserId(session.userId)
                    val hasPassword = oauthService.hasPassword(userId)
                    val oauthProviders = oauthService.getOAuthProviders(userId)
                    call.respond(
                        UserInfo(
                            username = updatedUser.username,
                            id = updatedUser.id.value,
                            role = updatedUser.role,
                            teams = teamInfos,
                            createdAt = updatedUser.createdAt,
                            hasPassword = hasPassword,
                            oauthProviders = oauthProviders,
                            approved = updatedUser.approved,
                        )
                    )
                },
                onFailure = { error ->
                    val message = error.message ?: "Username change failed"
                    when {
                        message == "INVALID_PASSWORD" -> {
                            call.respond(
                                HttpStatusCode.BadRequest,
                                ErrorResponse(error = "Invalid password", code = "INVALID_PASSWORD"),
                            )
                        }
                        message == "USERNAME_TAKEN" -> {
                            call.respond(
                                HttpStatusCode.BadRequest,
                                ErrorResponse(error = "Username already taken", code = "USERNAME_TAKEN"),
                            )
                        }
                        message.startsWith("VALIDATION_ERROR:") -> {
                            call.respond(
                                HttpStatusCode.BadRequest,
                                ErrorResponse(
                                    error = message.removePrefix("VALIDATION_ERROR:"),
                                    code = "VALIDATION_ERROR",
                                ),
                            )
                        }
                        else -> {
                            call.respond(
                                HttpStatusCode.BadRequest,
                                ErrorResponse(error = message, code = "BAD_REQUEST"),
                            )
                        }
                    }
                },
            )
        }

        put(ApiRoutes.Auth.CHANGE_PASSWORD) {
            val session = call.userSession()
            val request = call.receive<ChangePasswordRequest>()

            // Validate new password
            val passwordErrors = passwordValidator.validate(request.newPassword)
            if (passwordErrors.isNotEmpty()) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse(error = passwordErrors.joinToString("; "), code = "VALIDATION_ERROR"),
                )
                return@put
            }

            // Reject same-password change — avoids unnecessary hashing and misleading passwordChangedAt update
            if (request.currentPassword == request.newPassword) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse(error = "New password must be different from current password", code = "SAME_PASSWORD"),
                )
                return@put
            }

            val result = authService.changePassword(
                UserId(session.userId),
                request.currentPassword,
                request.newPassword,
            )
            result.fold(
                onSuccess = {
                    log.info("User '{}' changed password", session.userId)
                    call.respond(HttpStatusCode.OK)
                },
                onFailure = { error ->
                    val message = error.message ?: "Password change failed"
                    if (message == "INVALID_PASSWORD") {
                        call.respond(
                            HttpStatusCode.BadRequest,
                            ErrorResponse(error = "Invalid current password", code = "INVALID_PASSWORD"),
                        )
                    } else {
                        call.respond(
                            HttpStatusCode.BadRequest,
                            ErrorResponse(error = message, code = "BAD_REQUEST"),
                        )
                    }
                },
            )
        }

        delete(ApiRoutes.Auth.DELETE_ACCOUNT) {
            val session = call.userSession()
            val request = call.receive<DeleteAccountRequest>()

            val result = authService.deleteAccountSafe(
                UserId(session.userId),
                request.confirmUsername,
                request.currentPassword,
            )
            result.fold(
                onSuccess = {
                    log.info("User '{}' deleted their account", session.userId)
                    call.sessions.clear<UserSession>()
                    call.respond(HttpStatusCode.OK)
                },
                onFailure = { error ->
                    val message = error.message ?: "Account deletion failed"
                    val code = when (message) {
                        "LAST_ADMIN" -> "LAST_ADMIN"
                        "LAST_TEAM_LEAD" -> "LAST_TEAM_LEAD"
                        "INVALID_PASSWORD" -> "INVALID_PASSWORD"
                        "USERNAME_MISMATCH" -> "USERNAME_MISMATCH"
                        else -> "BAD_REQUEST"
                    }
                    val humanMessage = when (message) {
                        "LAST_ADMIN" -> "Cannot delete the last admin account"
                        "LAST_TEAM_LEAD" -> "Cannot delete account: you are the last lead in one or more teams"
                        "INVALID_PASSWORD" -> "Invalid password"
                        "USERNAME_MISMATCH" -> "Username confirmation does not match"
                        else -> message
                    }
                    val status = when (code) {
                        "LAST_ADMIN", "LAST_TEAM_LEAD" -> HttpStatusCode.Conflict
                        else -> HttpStatusCode.BadRequest
                    }
                    call.respond(status, ErrorResponse(error = humanMessage, code = code))
                },
            )
        }

        // Admin-only: generate password reset token
        post(ApiRoutes.Auth.GENERATE_PASSWORD_RESET) {
            val session = call.userSession()
            requireAdmin(call, session) ?: return@post
            val request = call.receive<GeneratePasswordResetRequest>()

            val result = passwordResetService.generateToken(
                UserId(request.userId),
                UserId(session.userId),
            )
            result.fold(
                onSuccess = { generated ->
                    // SEC-M1: Use server-side configured base URL instead of attacker-controllable Origin header
                    val baseUrl = webhookConfig.baseUrl.trimEnd('/')
                    val resetUrl = "$baseUrl/reset-password/${generated.rawToken}"
                    call.respond(PasswordResetLinkResponse(token = generated.rawToken, resetUrl = resetUrl, expiresAt = generated.expiresAtMillis))
                },
                onFailure = { error ->
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse(error = error.message ?: "Token generation failed", code = "BAD_REQUEST"),
                    )
                },
            )
        }

        // AUTH-H2: Use userSession() + role check instead of requireAdminSession
        get(ApiRoutes.Auth.USERS) {
            val session = call.userSession()
            requireAdmin(call, session) ?: return@get
            val users = authService.listUsers()
            call.respond(UserListResponse(users))
        }

        get(ApiRoutes.Auth.USERS + "/{userId}") {
            val session = call.userSession()
            requireAdmin(call, session) ?: return@get
            val userId = call.parameters["userId"]
                ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse(error = "Missing userId", code = "BAD_REQUEST"))
            val user = authService.getUserById(UserId(userId))
            if (user != null) {
                call.respond(UserInfo(username = user.username, id = user.id.value, role = user.role, approved = user.approved))
            } else {
                call.respond(HttpStatusCode.NotFound, ErrorResponse(error = "User not found", code = "NOT_FOUND"))
            }
        }

        put(ApiRoutes.Auth.USERS + "/{userId}/role") {
            val session = call.userSession()
            requireAdmin(call, session) ?: return@put
            val userId = call.parameters["userId"]
                ?: return@put call.respond(HttpStatusCode.BadRequest, ErrorResponse(error = "Missing userId", code = "BAD_REQUEST"))
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

        // Admin-only: approve a pending user
        post(ApiRoutes.Auth.USERS + "/{userId}/approve") {
            val session = call.userSession()
            requireAdmin(call, session) ?: return@post
            val userId = call.parameters["userId"]
                ?: return@post call.respond(HttpStatusCode.BadRequest, ErrorResponse(error = "Missing userId", code = "BAD_REQUEST"))
            val updated = authService.approveUser(UserId(userId))
            if (updated) {
                log.info("Admin '{}' approved user '{}'", session.userId, userId)
                call.respond(HttpStatusCode.OK, mapOf("status" to "approved"))
            } else {
                call.respond(HttpStatusCode.NotFound, ErrorResponse(error = "User not found", code = "NOT_FOUND"))
            }
        }

        // Admin-only: delete (reject) a user
        delete(ApiRoutes.Auth.USERS + "/{userId}") {
            val session = call.userSession()
            requireAdmin(call, session) ?: return@delete
            val userId = call.parameters["userId"]
                ?: return@delete call.respond(HttpStatusCode.BadRequest, ErrorResponse(error = "Missing userId", code = "BAD_REQUEST"))
            val result = authService.adminDeleteUser(UserId(userId), UserId(session.userId))
            result.fold(
                onSuccess = { deleted ->
                    if (deleted) {
                        log.info("Admin '{}' deleted user '{}'", session.userId, userId)
                        call.respond(HttpStatusCode.OK, mapOf("status" to "deleted"))
                    } else {
                        call.respond(HttpStatusCode.NotFound, ErrorResponse(error = "User not found", code = "NOT_FOUND"))
                    }
                },
                onFailure = { error ->
                    val message = error.message ?: "Deletion failed"
                    val code = when (message) {
                        "LAST_ADMIN" -> "LAST_ADMIN"
                        "LAST_TEAM_LEAD" -> "LAST_TEAM_LEAD"
                        "CANNOT_DELETE_SELF" -> "CANNOT_DELETE_SELF"
                        else -> "BAD_REQUEST"
                    }
                    val humanMessage = when (message) {
                        "LAST_ADMIN" -> "Cannot delete the last admin account"
                        "LAST_TEAM_LEAD" -> "Cannot delete: user is the last lead in one or more teams"
                        "CANNOT_DELETE_SELF" -> "Cannot delete your own account via admin panel"
                        else -> message
                    }
                    val status = when (code) {
                        "LAST_ADMIN", "LAST_TEAM_LEAD" -> HttpStatusCode.Conflict
                        else -> HttpStatusCode.BadRequest
                    }
                    call.respond(status, ErrorResponse(error = humanMessage, code = code))
                },
            )
        }
    }

    // Unauthenticated password reset endpoints with dedicated rate limiter
    rateLimit(RateLimitName("password-reset")) {
        post(ApiRoutes.Auth.VALIDATE_RESET_TOKEN) {
            val request = call.receive<ValidateResetTokenRequest>()
            val userId = passwordResetService.validateToken(request.token)
            if (userId != null) {
                call.respond(HttpStatusCode.OK, mapOf("valid" to true))
            } else {
                call.respond(
                    HttpStatusCode.NotFound,
                    ErrorResponse(error = "Invalid or expired token", code = "INVALID_TOKEN"),
                )
            }
        }

        post(ApiRoutes.Auth.RESET_PASSWORD) {
            val request = call.receive<ResetPasswordRequest>()

            // Validate the new password
            val passwordErrors = passwordValidator.validate(request.newPassword)
            if (passwordErrors.isNotEmpty()) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse(error = passwordErrors.joinToString("; "), code = "VALIDATION_ERROR"),
                )
                return@post
            }

            // Hash the new password before consuming the token
            val newPasswordHash = authService.hashPassword(request.newPassword)

            val result = passwordResetService.consumeToken(request.token, newPasswordHash)
            result.fold(
                onSuccess = { userId ->
                    log.info("Password reset completed for user '{}'", userId.value)
                    call.respond(HttpStatusCode.OK)
                },
                onFailure = { error ->
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse(
                            error = error.message ?: "Password reset failed",
                            code = "INVALID_TOKEN",
                        ),
                    )
                },
            )
        }
    }
}

/**
 * AUTH-H2: Checks that the session has ADMIN role. Returns the session on success, or null
 * after responding 403. Replaces the old requireAdminSession that had a dead null-check.
 */
private suspend fun requireAdmin(call: ApplicationCall, session: UserSession): UserSession? {
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

