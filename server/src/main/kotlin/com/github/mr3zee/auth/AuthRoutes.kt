package com.github.mr3zee.auth

import com.github.mr3zee.api.ApiRoutes
import com.github.mr3zee.api.ErrorResponse
import com.github.mr3zee.api.LoginRequest
import com.github.mr3zee.api.UserInfo
import com.github.mr3zee.plugins.CorrelationIdKey
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import org.koin.ktor.ext.inject

fun Route.authRoutes() {
    val authService by inject<AuthService>()

    post(ApiRoutes.Auth.LOGIN) {
        val request = call.receive<LoginRequest>()
        if (authService.validate(request.username, request.password)) {
            call.sessions.set(UserSession(username = request.username))
            call.respond(UserInfo(username = request.username))
        } else {
            val correlationId = call.attributes.getOrNull(CorrelationIdKey)
            call.respond(
                HttpStatusCode.Unauthorized,
                ErrorResponse(
                    error = "Invalid credentials",
                    code = "INVALID_CREDENTIALS",
                    correlationId = correlationId,
                ),
            )
        }
    }

    post(ApiRoutes.Auth.LOGOUT) {
        call.sessions.clear<UserSession>()
        call.respond(HttpStatusCode.OK)
    }

    get(ApiRoutes.Auth.ME) {
        val session = call.sessions.get<UserSession>()
        if (session != null) {
            call.respond(UserInfo(username = session.username))
        } else {
            val correlationId = call.attributes.getOrNull(CorrelationIdKey)
            call.respond(
                HttpStatusCode.Unauthorized,
                ErrorResponse(
                    error = "Not authenticated",
                    code = "UNAUTHORIZED",
                    correlationId = correlationId,
                ),
            )
        }
    }
}
