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
import org.slf4j.LoggerFactory
import java.util.UUID

private val log = LoggerFactory.getLogger("com.github.mr3zee.releases.ReleaseWebSocketRoutes")

fun Route.releaseWebSocketRoutes() {
    val service by inject<ReleasesService>()
    val engine by inject<ExecutionEngine>()

    route("${ApiRoutes.Releases.BASE}/{id}") {
        webSocket("/ws") {
            // Validate Origin header to prevent cross-origin WebSocket hijacking
            val origin = call.request.headers["Origin"]
            val host = call.request.headers["Host"]
            if (origin != null && host != null) {
                val originHost = try { java.net.URI(origin).host } catch (_: Exception) { null }
                if (originHost != null && originHost != host.substringBefore(":")) {
                    log.warn("Cross-origin WebSocket rejected: origin={}, host={}", origin, host)
                    close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Cross-origin WebSocket not allowed"))
                    return@webSocket
                }
            }

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
                log.warn("WebSocket connection rejected: release {} not found", idParam)
                close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Release not found"))
                return@webSocket
            }

            // Check ownership — session is available since WS routes are inside authenticate("session-auth")
            val session = call.sessions.get<UserSession>() ?: run {
                close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Not authenticated"))
                return@webSocket
            }
            try {
                service.checkAccess(releaseId, session)
            } catch (_: ForbiddenException) {
                log.warn("WebSocket access denied for release {} by user {}", idParam, session.userId)
                close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Access denied"))
                return@webSocket
            }

            log.debug("WebSocket connected for release {}", idParam)

            // Check for lastSeq query parameter for incremental replay
            val lastSeqParam = call.request.queryParameters["lastSeq"]?.toLongOrNull()

            // Subscribe to events BEFORE querying snapshot to avoid race condition.
            // Use UNDISPATCHED start to ensure the collector begins immediately,
            // preventing events from being lost between subscription and snapshot.
            val eventBuffer = Channel<ReleaseEvent>(Channel.UNLIMITED)
            val collectJob = launch(start = CoroutineStart.UNDISPATCHED) {
                engine.events
                    .filter { it.releaseId == releaseId }
                    .collect { eventBuffer.send(it) }
            }

            // Determine whether we can do incremental replay or need a full snapshot
            var sentSnapshot = false
            var lastReplayedSeq = 0L
            // Track the latest release state for the terminal check below
            var latestRelease = release
            if (lastSeqParam != null && lastSeqParam > 0) {
                val replayEvents = engine.getReplayEvents(releaseId, lastSeqParam)
                if (replayEvents != null) {
                    // Incremental replay: send only missed events
                    for (replayEvent in replayEvents) {
                        send(Frame.Text(AppJson.encodeToString(ReleaseEvent.serializer(), replayEvent)))
                        if (replayEvent.sequenceNumber > lastReplayedSeq) {
                            lastReplayedSeq = replayEvent.sequenceNumber
                        }
                    }
                    sentSnapshot = true
                }
            }

            if (!sentSnapshot) {
                // Full snapshot fallback (original behavior)
                val executions = service.getBlockExecutions(releaseId)
                val currentRelease = service.getRelease(releaseId) ?: release
                latestRelease = currentRelease
                val snapshot = ReleaseEvent.Snapshot(
                    releaseId = releaseId,
                    release = currentRelease,
                    blockExecutions = executions,
                )
                send(Frame.Text(AppJson.encodeToString(ReleaseEvent.serializer(), snapshot)))
            }

            // If the release is already in a terminal state, the ReleaseCompleted event
            // may have been emitted before our subscription started. Close immediately.
            if (latestRelease.status.isTerminal) {
                close(CloseReason(CloseReason.Codes.NORMAL, "Release completed"))
                collectJob.cancel()
                eventBuffer.close()
                return@webSocket
            }

            // Forward buffered and subsequent events, skipping any already sent during replay
            try {
                for (event in eventBuffer) {
                    // Skip events that were already sent during incremental replay
                    if (lastReplayedSeq > 0 && event.sequenceNumber > 0 && event.sequenceNumber <= lastReplayedSeq) {
                        continue
                    }
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
