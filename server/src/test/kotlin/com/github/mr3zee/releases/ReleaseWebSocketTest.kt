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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ReleaseWebSocketTest {

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
    fun `connect and receive snapshot`() = testApplication {
        application { testModule() }
        val httpClient = jsonClient()
        httpClient.login()

        val project = httpClient.createTestProject()
        val created = httpClient.post(ApiRoutes.Releases.BASE) {
            contentType(ContentType.Application.Json)
            setBody(CreateReleaseRequest(projectTemplateId = project.id))
        }.body<ReleaseResponse>()

        // Wait for execution to complete
        httpClient.post(ApiRoutes.Releases.await(created.release.id.value))

        val wsClient = wsClient()
        wsClient.login()

        wsClient.webSocket(ApiRoutes.Releases.ws(created.release.id.value)) {
            val frame = incoming.receive()
            assertIs<Frame.Text>(frame)
            val event = receiveEvent(frame.readText())
            assertIs<ReleaseEvent.Snapshot>(event)
            assertEquals(created.release.id, event.releaseId)
            assertEquals(1, event.blockExecutions.size)
        }
    }

    @Test
    fun `receive block execution updates during execution`() = testApplication {
        application { testModule() }
        val httpClient = jsonClient()
        httpClient.login()

        val project = httpClient.createTestProject(
            blocks = listOf(
                Block.ActionBlock(id = BlockId("a"), name = "A", type = BlockType.TEAMCITY_BUILD),
                Block.ActionBlock(id = BlockId("b"), name = "B", type = BlockType.GITHUB_ACTION),
            ),
            edges = listOf(Edge(fromBlockId = BlockId("a"), toBlockId = BlockId("b"))),
        )

        val wsClient = wsClient()
        wsClient.login()

        // Start the release — don't await, connect via WS to capture events
        val created = httpClient.post(ApiRoutes.Releases.BASE) {
            contentType(ContentType.Application.Json)
            setBody(CreateReleaseRequest(projectTemplateId = project.id))
        }.body<ReleaseResponse>()

        wsClient.webSocket(ApiRoutes.Releases.ws(created.release.id.value)) {
            val events = mutableListOf<ReleaseEvent>()

            // Collect events until we see ReleaseCompleted or connection closes
            for (frame in incoming) {
                if (frame is Frame.Text) {
                    val event = receiveEvent(frame.readText())
                    events.add(event)
                    if (event is ReleaseEvent.ReleaseCompleted) break
                }
            }

            // Should have snapshot + block updates + release status changes
            assertTrue(events.isNotEmpty())
            assertIs<ReleaseEvent.Snapshot>(events.first())

            val blockUpdates = events.filterIsInstance<ReleaseEvent.BlockExecutionUpdated>()
            assertTrue(blockUpdates.isNotEmpty(), "Should have block execution updates")

            val completed = events.filterIsInstance<ReleaseEvent.ReleaseCompleted>()
            assertEquals(1, completed.size)
            assertEquals(ReleaseStatus.SUCCEEDED, completed.first().status)
        }
    }

    @Test
    fun `receive release completed event`() = testApplication {
        application { testModule() }
        val httpClient = jsonClient()
        httpClient.login()

        val project = httpClient.createTestProject()

        val wsClient = wsClient()
        wsClient.login()

        val created = httpClient.post(ApiRoutes.Releases.BASE) {
            contentType(ContentType.Application.Json)
            setBody(CreateReleaseRequest(projectTemplateId = project.id))
        }.body<ReleaseResponse>()

        wsClient.webSocket(ApiRoutes.Releases.ws(created.release.id.value)) {
            val events = mutableListOf<ReleaseEvent>()
            for (frame in incoming) {
                if (frame is Frame.Text) {
                    val event = receiveEvent(frame.readText())
                    events.add(event)
                    if (event is ReleaseEvent.ReleaseCompleted) break
                }
            }

            val completed = events.filterIsInstance<ReleaseEvent.ReleaseCompleted>()
            assertEquals(1, completed.size)
            assertEquals(ReleaseStatus.SUCCEEDED, completed.first().status)
        }
    }

    @Test
    fun `nonexistent release closes connection`() = testApplication {
        application { testModule() }
        val wsClient = wsClient()
        wsClient.login()

        wsClient.webSocket(ApiRoutes.Releases.ws("00000000-0000-0000-0000-000000000000")) {
            val reason = closeReason.await()
            assertEquals(CloseReason.Codes.VIOLATED_POLICY, reason?.knownReason)
            assertEquals(reason?.message?.contains("not found"), true)
        }
    }

    @Test
    fun `graceful disconnect`() = testApplication {
        application { testModule() }
        val httpClient = jsonClient()
        httpClient.login()

        val project = httpClient.createTestProject()
        val created = httpClient.post(ApiRoutes.Releases.BASE) {
            contentType(ContentType.Application.Json)
            setBody(CreateReleaseRequest(projectTemplateId = project.id))
        }.body<ReleaseResponse>()

        httpClient.post(ApiRoutes.Releases.await(created.release.id.value))

        val wsClient = wsClient()
        wsClient.login()

        wsClient.webSocket(ApiRoutes.Releases.ws(created.release.id.value)) {
            // Receive snapshot
            val frame = incoming.receive()
            assertIs<Frame.Text>(frame)

            // Client-initiated close
            close(CloseReason(CloseReason.Codes.NORMAL, "Client done"))
        }
        // No crash — test passes if we get here
    }
}
