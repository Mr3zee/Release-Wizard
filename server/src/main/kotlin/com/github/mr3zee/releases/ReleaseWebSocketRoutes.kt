package com.github.mr3zee.releases

import com.github.mr3zee.AppJson
import com.github.mr3zee.ForbiddenException
import com.github.mr3zee.api.ApiRoutes
import com.github.mr3zee.api.ReleaseEvent
import com.github.mr3zee.auth.UserSession
import com.github.mr3zee.execution.ExecutionEngine
import com.github.mr3zee.model.ReleaseId
import com.github.mr3zee.model.isTerminal
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import org.koin.ktor.ext.inject
import java.util.UUID

fun Route.releaseWebSocketRoutes() {
    val service by inject<ReleasesService>()
    val engine by inject<ExecutionEngine>()

    route("${ApiRoutes.Releases.BASE}/{id}") {
        webSocket("/ws") {
            val idParam = call.parameters["id"]
            if (idParam == null) {
                close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Missing release id"))
                return@webSocket
            }

            try {
                UUID.fromString(idParam)
            } catch (_: IllegalArgumentException) {
                close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Invalid release ID format"))
                return@webSocket
            }

            val releaseId = ReleaseId(idParam)
            val release = service.getRelease(releaseId)
            if (release == null) {
                close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Release not found"))
                return@webSocket
            }

            // Check ownership — session is available since WS routes are inside authenticate("session-auth")
            val session = call.sessions.get<UserSession>()!!
            try {
                service.checkAccess(releaseId, session)
            } catch (_: ForbiddenException) {
                close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Access denied"))
                return@webSocket
            }

            // Subscribe to events BEFORE querying snapshot to avoid race condition.
            // Use UNDISPATCHED start to ensure the collector begins immediately,
            // preventing events from being lost between subscription and snapshot.
            val eventBuffer = Channel<ReleaseEvent>(Channel.UNLIMITED)
            val collectJob = launch(start = CoroutineStart.UNDISPATCHED) {
                engine.events
                    .filter { it.releaseId == releaseId }
                    .collect { eventBuffer.send(it) }
            }

            // Send initial snapshot
            val executions = service.getBlockExecutions(releaseId)
            val currentRelease = service.getRelease(releaseId) ?: release
            val snapshot = ReleaseEvent.Snapshot(
                releaseId = releaseId,
                release = currentRelease,
                blockExecutions = executions,
            )
            send(Frame.Text(AppJson.encodeToString(ReleaseEvent.serializer(), snapshot)))

            // If the release is already in a terminal state, the ReleaseCompleted event
            // may have been emitted before our subscription started. Close immediately.
            if (currentRelease.status.isTerminal) {
                close(CloseReason(CloseReason.Codes.NORMAL, "Release completed"))
                collectJob.cancel()
                eventBuffer.close()
                return@webSocket
            }

            // Forward buffered and subsequent events
            try {
                for (event in eventBuffer) {
                    send(Frame.Text(AppJson.encodeToString(ReleaseEvent.serializer(), event)))

                    if (event is ReleaseEvent.ReleaseCompleted) {
                        close(CloseReason(CloseReason.Codes.NORMAL, "Release completed"))
                        break
                    }
                }
            } finally {
                collectJob.cancel()
                eventBuffer.close()
            }
        }
    }
}
