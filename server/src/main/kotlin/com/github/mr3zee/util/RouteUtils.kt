package com.github.mr3zee.util

import com.github.mr3zee.model.ProjectId
import io.ktor.server.application.ApplicationCall
import java.util.UUID

internal fun ApplicationCall.requireUuidParam(paramName: String): String {
    val raw = parameters[paramName]
        ?: throw IllegalArgumentException("Missing $paramName")
    try {
        UUID.fromString(raw)
    } catch (_: IllegalArgumentException) {
        throw IllegalArgumentException("Invalid $paramName format")
    }
    return raw
}

internal fun ApplicationCall.requireProjectId(): ProjectId {
    return ProjectId(requireUuidParam("projectId"))
}
