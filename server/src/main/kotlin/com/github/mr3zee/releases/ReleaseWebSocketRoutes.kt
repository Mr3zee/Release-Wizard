package com.github.mr3zee.releases

import com.github.mr3zee.AppJson
import com.github.mr3zee.api.ReleaseEvent
import com.github.mr3zee.execution.ExecutionEngine
import com.github.mr3zee.model.ReleaseId
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.koin.ktor.ext.inject
import java.util.UUID

fun Route.releaseWebSocketRoutes() {
    val service by inject<ReleasesService>()
    val engine by inject<ExecutionEngine>()

    route("/api/v1/releases/{id}") {
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

            // Subscribe to events BEFORE querying snapshot to avoid race condition.
            // Buffer events that arrive between subscription and snapshot send.
            val eventBuffer = Channel<ReleaseEvent>(Channel.UNLIMITED)
            val collectJob = engine.events
                .filter { it.releaseId == releaseId }
                .onEach { eventBuffer.send(it) }
                .launchIn(this)

            // Send initial snapshot
            val executions = service.getBlockExecutions(releaseId)
            val snapshot = ReleaseEvent.Snapshot(
                releaseId = releaseId,
                release = release,
                blockExecutions = executions,
            )
            send(Frame.Text(AppJson.encodeToString(ReleaseEvent.serializer(), snapshot)))

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
