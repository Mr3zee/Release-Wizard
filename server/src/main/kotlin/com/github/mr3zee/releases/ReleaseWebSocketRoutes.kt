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
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

private val log = LoggerFactory.getLogger("com.github.mr3zee.releases.ReleaseWebSocketRoutes")

// REL-H3: Per-user WebSocket connection counter to prevent memory exhaustion via WS spam
private val wsConnectionsPerUser = ConcurrentHashMap<String, AtomicInteger>()
private const val MAX_WS_CONNECTIONS_PER_USER = 50

// REL-H4: Bounded channel capacity to prevent unbounded memory growth from slow consumers
private const val WS_CHANNEL_CAPACITY = 1024

fun Route.releaseWebSocketRoutes() {
    val service by inject<ReleasesService>()
    val engine by inject<ExecutionEngine>()

    route("${ApiRoutes.Releases.BASE}/{id}") {
        webSocket("/ws") {
            // BUG-7 + REL-M6: Validate Origin header to prevent cross-origin WebSocket hijacking.
            // Fail closed: if Origin is present, it MUST match the Host header.
            // REL-M6: Also fail closed on parse errors (null originHost from URI).
            val origin = call.request.headers["Origin"]
            if (origin != null) {
                val host = call.request.headers["Host"]
                if (host == null) {
                    log.warn("Cross-origin WebSocket rejected: Origin present but Host header missing")
                    close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Missing Host header"))
                    return@webSocket
                }
                val originHost = try { java.net.URI(origin).host } catch (_: Exception) { null }
                // REL-M6: Fail closed if origin cannot be parsed
                if (originHost == null) {
                    log.warn("Cross-origin WebSocket rejected: cannot parse Origin header '{}'", origin)
                    close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Invalid Origin header"))
                    return@webSocket
                }
                // REL-M6: Compare with port to prevent port-based attacks
                val hostWithoutPort = host.substringBefore(":")
                if (originHost != hostWithoutPort) {
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

            // Check ownership — session is available since WS routes are inside authenticate("session-auth")
            val session = call.sessions.get<UserSession>() ?: run {
                close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Not authenticated"))
                return@webSocket
            }

            // REL-H3: Enforce per-user WebSocket connection limit
            val userCounter = wsConnectionsPerUser.getOrPut(session.userId) { AtomicInteger(0) }
            val currentCount = userCounter.incrementAndGet()
            if (currentCount > MAX_WS_CONNECTIONS_PER_USER) {
                userCounter.decrementAndGet()
                log.warn("WebSocket connection limit exceeded for user {} ({} connections)", session.userId, currentCount)
                close(CloseReason(CloseReason.Codes.TRY_AGAIN_LATER, "Too many WebSocket connections"))
                return@webSocket
            }

            // REL-H3: All paths after incrementing the counter must decrement it.
            // Wrap everything in try/finally to guarantee cleanup on any exit path.
            try {
            // REL-H2: Access-controlled release retrieval
            val release = try {
                service.getRelease(releaseId, session)
            } catch (_: ForbiddenException) {
                log.warn("WebSocket access denied for release {} by user {}", idParam, session.userId)
                close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Access denied"))
                return@webSocket
            }
            if (release == null) {
                log.warn("WebSocket connection rejected: release {} not found", idParam)
                close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Release not found"))
                return@webSocket
            }

            log.debug("WebSocket connected for release {}", idParam)

            // Check for lastSeq query parameter for incremental replay
            val lastSeqParam = call.request.queryParameters["lastSeq"]?.toLongOrNull()

            // Subscribe to events BEFORE querying snapshot to avoid race condition.
            // Use UNDISPATCHED start to ensure the collector begins immediately,
            // preventing events from being lost between subscription and snapshot.
            // REL-H4: Bounded channel prevents unbounded memory growth from slow consumers.
            // trySend drops newest events if the buffer is full (backpressure for slow clients).
            val eventBuffer = Channel<ReleaseEvent>(WS_CHANNEL_CAPACITY)
            val collectJob = launch(start = CoroutineStart.UNDISPATCHED) {
                engine.events
                    .filter { it.releaseId == releaseId }
                    .collect { event ->
                        if (!eventBuffer.trySend(event).isSuccess) {
                            log.debug("WebSocket event buffer full for release {}, dropping event", releaseId.value)
                        }
                    }
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
                val executions = service.getBlockExecutions(releaseId, session)
                val currentRelease = service.getRelease(releaseId, session) ?: release
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

            } finally {
                // REL-H3: Always decrement connection count and clean up empty entries
                val remaining = userCounter.decrementAndGet()
                if (remaining <= 0) {
                    wsConnectionsPerUser.remove(session.userId)
                }
            }
        }
    }
}
