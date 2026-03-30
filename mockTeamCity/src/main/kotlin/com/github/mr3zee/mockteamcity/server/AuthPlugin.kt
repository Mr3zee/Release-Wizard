package com.github.mr3zee.mockteamcity.server

import com.github.mr3zee.mockteamcity.model.MockTeamCityState
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*

fun Application.configureAuth(state: MockTeamCityState) {
    intercept(ApplicationCallPipeline.Plugins) {
        val path = call.request.local.uri
        if (!path.startsWith("/app/rest/")) return@intercept

        val authHeader = call.request.headers[HttpHeaders.Authorization]
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            call.respondText("Unauthorized: missing Bearer token", status = HttpStatusCode.Unauthorized)
            finish()
            return@intercept
        }

        val token = authHeader.removePrefix("Bearer ")
        val accepted = state.currentState().serverConfig.acceptedToken
        if (accepted.isNotEmpty() && token != accepted) {
            call.respondText("Unauthorized: invalid token", status = HttpStatusCode.Unauthorized)
            finish()
            return@intercept
        }
    }
}
