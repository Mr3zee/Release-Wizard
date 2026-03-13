package com.github.mr3zee.auth

import io.ktor.server.application.ApplicationCall
import io.ktor.server.sessions.get
import io.ktor.server.sessions.sessions

/**
 * Extracts the UserSession from an authenticated route.
 * Should only be called within authenticate("session-auth") blocks.
 */
fun ApplicationCall.userSession(): UserSession = sessions.get<UserSession>()!!
