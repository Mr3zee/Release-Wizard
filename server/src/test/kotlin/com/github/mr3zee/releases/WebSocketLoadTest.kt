package com.github.mr3zee.releases

import com.github.mr3zee.AppJson
import com.github.mr3zee.api.*
import com.github.mr3zee.jsonClient
import com.github.mr3zee.login
import com.github.mr3zee.model.*
import com.github.mr3zee.testModule
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.cookies.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.testing.*
import io.ktor.websocket.*
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation as ClientContentNegotiation
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class WebSocketLoadTest {

    private suspend fun HttpClient.createTestProject(
        blocks: List<Block> = listOf(
            Block.ActionBlock(id = BlockId("block-a"), name = "Build", type = BlockType.TEAMCITY_BUILD),
        ),
        edges: List<Edge> = emptyList(),
    ): ProjectTemplate {
        val response = post(ApiRoutes.Projects.BASE) {
            contentType(ContentType.Application.Json)
            setBody(
                CreateProjectRequest(
                    name = "Test Project",
                    teamId = TeamId("00000000-0000-0000-0000-000000000000"),
                    dagGraph = DagGraph(blocks = blocks, edges = edges),
                )
            )
        }
        return response.body<ProjectResponse>().project
    }

    private fun ApplicationTestBuilder.wsClient() = createClient {
        install(WebSockets)
        install(ClientContentNegotiation) { json(AppJson) }
        install(HttpCookies)
    }

    private fun receiveEvent(text: String): ReleaseEvent {
        return AppJson.decodeFromString(ReleaseEvent.serializer(), text)
    }

    @Test
    fun `multiple WebSocket clients receive same events for a running release`() = testApplication {
        application { testModule() }
        val httpClient = jsonClient()
        httpClient.login()

        val m = 3

        // Create a project with a USER_ACTION block so the release stays running
        // while we connect WebSocket clients, followed by a regular block
        val project = httpClient.createTestProject(
            blocks = listOf(
                Block.ActionBlock(id = BlockId("approval"), name = "Approve", type = BlockType.USER_ACTION),
                Block.ActionBlock(id = BlockId("build"), name = "Build", type = BlockType.TEAMCITY_BUILD),
            ),
            edges = listOf(Edge(fromBlockId = BlockId("approval"), toBlockId = BlockId("build"))),
        )

        // Start the release
        val created = httpClient.post(ApiRoutes.Releases.BASE) {
            contentType(ContentType.Application.Json)
            setBody(CreateReleaseRequest(projectTemplateId = project.id))
        }.body<ReleaseResponse>()
        val releaseId = created.release.id

        // Create M WebSocket clients and log them in
        val wsClients = (1..m).map {
            val ws = wsClient()
            ws.login()
            ws
        }

        // Connect all M clients and collect events concurrently
        coroutineScope {
            val eventCollectors = wsClients.map { wsClient ->
                async {
                    val events = mutableListOf<ReleaseEvent>()
                    wsClient.webSocket(ApiRoutes.Releases.ws(releaseId.value)) {
                        // Receive snapshot first
                        val snapshotFrame = incoming.receive()
                        assertIs<Frame.Text>(snapshotFrame)
                        val snapshot = receiveEvent(snapshotFrame.readText())
                        assertIs<ReleaseEvent.Snapshot>(snapshot)
                        events.add(snapshot)

                        // Now approve the user action (only one client needs to do this,
                        // but we do it after all WS clients have connected)
                        // Signal readiness by yielding
                        yield()

                        // Collect remaining events until ReleaseCompleted
                        for (frame in incoming) {
                            if (frame is Frame.Text) {
                                val event = receiveEvent(frame.readText())
                                events.add(event)
                                if (event is ReleaseEvent.ReleaseCompleted) break
                            }
                        }
                    }
                    events
                }
            }

            // Wait a bit for all WS connections to be established, then approve
            // Retry approval until the engine reaches WAITING_FOR_INPUT
            yield()
            while (true) {
                val resp = httpClient.post(ApiRoutes.Releases.approveBlock(releaseId.value, "approval")) {
                    contentType(ContentType.Application.Json)
                    setBody(ApproveBlockRequest(input = mapOf("approved" to "true")))
                }
                if (resp.status == HttpStatusCode.OK) break
                yield()
            }

            val allEvents = eventCollectors.awaitAll()

            // All M clients should have received events
            assertEquals(m, allEvents.size, "All $m clients should have collected events")

            for ((clientIdx, events) in allEvents.withIndex()) {
                // Each client should have received a Snapshot
                val snapshots = events.filterIsInstance<ReleaseEvent.Snapshot>()
                assertEquals(1, snapshots.size, "Client $clientIdx should receive exactly 1 Snapshot")

                // Each client should have received a ReleaseCompleted
                val completed = events.filterIsInstance<ReleaseEvent.ReleaseCompleted>()
                assertEquals(
                    1,
                    completed.size,
                    "Client $clientIdx should receive exactly 1 ReleaseCompleted",
                )
                assertEquals(
                    ReleaseStatus.SUCCEEDED,
                    completed.first().status,
                    "Client $clientIdx should see SUCCEEDED status",
                )

                // Each client should have received BlockExecutionUpdated events
                val blockUpdates = events.filterIsInstance<ReleaseEvent.BlockExecutionUpdated>()
                assertTrue(
                    blockUpdates.isNotEmpty(),
                    "Client $clientIdx should receive block execution updates",
                )
            }

            // All clients should have received the same ReleaseCompleted event
            val completedStatuses = allEvents.map { events ->
                events.filterIsInstance<ReleaseEvent.ReleaseCompleted>().first().status
            }.toSet()
            assertEquals(
                setOf(ReleaseStatus.SUCCEEDED),
                completedStatuses,
                "All clients should see SUCCEEDED",
            )
        }
    }

    @Test
    fun `multiple WebSocket clients receive updates for simple release`() = testApplication {
        application { testModule() }
        val httpClient = jsonClient()
        httpClient.login()

        val m = 3

        // Use a simple 2-block chain: A -> B (no user action, just watch execution)
        val project = httpClient.createTestProject(
            blocks = listOf(
                Block.ActionBlock(id = BlockId("a"), name = "A", type = BlockType.TEAMCITY_BUILD),
                Block.ActionBlock(id = BlockId("b"), name = "B", type = BlockType.GITHUB_ACTION),
            ),
            edges = listOf(Edge(fromBlockId = BlockId("a"), toBlockId = BlockId("b"))),
        )

        // Start the release
        val created = httpClient.post(ApiRoutes.Releases.BASE) {
            contentType(ContentType.Application.Json)
            setBody(CreateReleaseRequest(projectTemplateId = project.id))
        }.body<ReleaseResponse>()
        val releaseId = created.release.id

        // Create and connect M WebSocket clients concurrently
        val wsClients = (1..m).map {
            val ws = wsClient()
            ws.login()
            ws
        }

        val allEvents = coroutineScope {
            wsClients.map { wsClient ->
                async {
                    val events = mutableListOf<ReleaseEvent>()
                    wsClient.webSocket(ApiRoutes.Releases.ws(releaseId.value)) {
                        for (frame in incoming) {
                            if (frame is Frame.Text) {
                                val event = receiveEvent(frame.readText())
                                events.add(event)
                                if (event is ReleaseEvent.ReleaseCompleted) break
                            }
                        }
                    }
                    events
                }
            }.awaitAll()
        }

        // All clients should have received events
        for ((clientIdx, events) in allEvents.withIndex()) {
            assertTrue(
                events.isNotEmpty(),
                "Client $clientIdx should have received events",
            )

            // Should start with a Snapshot
            val snapshot = events.first()
            assertIs<ReleaseEvent.Snapshot>(
                snapshot,
                "Client $clientIdx should receive Snapshot first",
            )

            // Client should see SUCCEEDED either via ReleaseCompleted event
            // or via the snapshot (if the release completed before WS connected)
            val completed = events.filterIsInstance<ReleaseEvent.ReleaseCompleted>()
            val succeededViaEvent = completed.any { it.status == ReleaseStatus.SUCCEEDED }
            val succeededViaSnapshot = snapshot.release.status == ReleaseStatus.SUCCEEDED
            assertTrue(
                succeededViaEvent || succeededViaSnapshot,
                "Client $clientIdx should see SUCCEEDED via event or snapshot",
            )
        }
    }

    @Test
    fun `WebSocket connections close cleanly on release completion`() = testApplication {
        application { testModule() }
        val httpClient = jsonClient()
        httpClient.login()

        // Use USER_ACTION so release stays running until we approve
        val project = httpClient.createTestProject(
            blocks = listOf(
                Block.ActionBlock(id = BlockId("approval"), name = "Approve", type = BlockType.USER_ACTION),
            ),
        )

        val created = httpClient.post(ApiRoutes.Releases.BASE) {
            contentType(ContentType.Application.Json)
            setBody(CreateReleaseRequest(projectTemplateId = project.id))
        }.body<ReleaseResponse>()
        val releaseId = created.release.id

        val m = 3
        val wsClients = (1..m).map {
            val ws = wsClient()
            ws.login()
            ws
        }

        // Connect all clients and verify they all disconnect cleanly
        coroutineScope {
            val closeCollectors = wsClients.map { wsClient ->
                async {
                    var reason: CloseReason? = null
                    wsClient.webSocket(ApiRoutes.Releases.ws(releaseId.value)) {
                        // Receive snapshot
                        val snapshotFrame = incoming.receive()
                        assertIs<Frame.Text>(snapshotFrame)
                        yield()

                        // Consume remaining events
                        for (frame in incoming) {
                            if (frame is Frame.Text) {
                                val event = receiveEvent(frame.readText())
                                if (event is ReleaseEvent.ReleaseCompleted) break
                            }
                        }
                        reason = closeReason.await()
                    }
                    reason
                }
            }

            // Approve after WS clients are connected
            yield()
            while (true) {
                val resp = httpClient.post(ApiRoutes.Releases.approveBlock(releaseId.value, "approval")) {
                    contentType(ContentType.Application.Json)
                    setBody(ApproveBlockRequest(input = emptyMap()))
                }
                if (resp.status == HttpStatusCode.OK) break
                yield()
            }

            val closeReasons = closeCollectors.awaitAll()

            for ((idx, reason) in closeReasons.withIndex()) {
                if (reason != null) {
                    assertEquals(
                        CloseReason.Codes.NORMAL,
                        reason.knownReason,
                        "Client $idx should receive NORMAL close code",
                    )
                }
            }
        }
    }

    @Test
    fun `WebSocket clients see consistent block execution updates across connections`() = testApplication {
        application { testModule() }
        val httpClient = jsonClient()
        httpClient.login()

        // Use USER_ACTION first so release stays running while WS clients connect,
        // then A -> B -> C chain for block execution events
        val project = httpClient.createTestProject(
            blocks = listOf(
                Block.ActionBlock(id = BlockId("gate"), name = "Gate", type = BlockType.USER_ACTION),
                Block.ActionBlock(id = BlockId("a"), name = "A", type = BlockType.TEAMCITY_BUILD),
                Block.ActionBlock(id = BlockId("b"), name = "B", type = BlockType.GITHUB_ACTION),
                Block.ActionBlock(id = BlockId("c"), name = "C", type = BlockType.SLACK_MESSAGE),
            ),
            edges = listOf(
                Edge(fromBlockId = BlockId("gate"), toBlockId = BlockId("a")),
                Edge(fromBlockId = BlockId("a"), toBlockId = BlockId("b")),
                Edge(fromBlockId = BlockId("b"), toBlockId = BlockId("c")),
            ),
        )

        val created = httpClient.post(ApiRoutes.Releases.BASE) {
            contentType(ContentType.Application.Json)
            setBody(CreateReleaseRequest(projectTemplateId = project.id))
        }.body<ReleaseResponse>()
        val releaseId = created.release.id

        val m = 3
        val wsClients = (1..m).map {
            val ws = wsClient()
            ws.login()
            ws
        }

        coroutineScope {
            val eventCollectors = wsClients.map { wsClient ->
                async {
                    val events = mutableListOf<ReleaseEvent>()
                    wsClient.webSocket(ApiRoutes.Releases.ws(releaseId.value)) {
                        for (frame in incoming) {
                            if (frame is Frame.Text) {
                                val event = receiveEvent(frame.readText())
                                events.add(event)
                                if (event is ReleaseEvent.ReleaseCompleted) break
                            }
                        }
                    }
                    events
                }
            }

            // Approve the gate after WS clients connect
            yield()
            while (true) {
                val resp = httpClient.post(ApiRoutes.Releases.approveBlock(releaseId.value, "gate")) {
                    contentType(ContentType.Application.Json)
                    setBody(ApproveBlockRequest(input = emptyMap()))
                }
                if (resp.status == HttpStatusCode.OK) break
                yield()
            }

            val allEvents = eventCollectors.awaitAll()

            // Extract block IDs that reached SUCCEEDED across all clients.
            // Include blocks already SUCCEEDED in the snapshot (connected after gate approved).
            val succeededBlocksByClient = allEvents.map { events ->
                val fromEvents = events.filterIsInstance<ReleaseEvent.BlockExecutionUpdated>()
                    .filter { it.blockExecution.status == BlockStatus.SUCCEEDED }
                    .map { it.blockExecution.blockId }
                    .toSet()
                val fromSnapshot = events.asSequence().filterIsInstance<ReleaseEvent.Snapshot>()
                    .flatMap { it.blockExecutions }
                    .filter { it.status == BlockStatus.SUCCEEDED }
                    .map { it.blockId }
                    .toSet()
                fromEvents + fromSnapshot
            }

            // All clients should see all 4 blocks reach SUCCEEDED (gate + a + b + c)
            val expectedBlocks = setOf(BlockId("gate"), BlockId("a"), BlockId("b"), BlockId("c"))
            for ((clientIdx, succeededBlocks) in succeededBlocksByClient.withIndex()) {
                assertEquals(
                    expectedBlocks,
                    succeededBlocks,
                    "Client $clientIdx should see all blocks succeed",
                )
            }

            // All clients should agree on the final release status (via event or snapshot)
            for ((clientIdx, events) in allEvents.withIndex()) {
                val completedEvent = events.filterIsInstance<ReleaseEvent.ReleaseCompleted>().firstOrNull()
                val snapshotStatus = events.filterIsInstance<ReleaseEvent.Snapshot>().firstOrNull()?.release?.status
                val succeeded = completedEvent?.status == ReleaseStatus.SUCCEEDED ||
                    snapshotStatus == ReleaseStatus.SUCCEEDED
                assertTrue(
                    succeeded,
                    "Client $clientIdx should see SUCCEEDED via event or snapshot",
                )
            }
        }
    }
}
