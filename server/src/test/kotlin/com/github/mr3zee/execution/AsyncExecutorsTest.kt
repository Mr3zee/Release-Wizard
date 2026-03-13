package com.github.mr3zee.execution

import com.github.mr3zee.AppJson
import com.github.mr3zee.execution.executors.GitHubActionExecutor
import com.github.mr3zee.execution.executors.TeamCityBuildExecutor
import com.github.mr3zee.model.*
import com.github.mr3zee.webhooks.*
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock

class AsyncExecutorsTest {

    private fun mockClient(handler: MockRequestHandler): HttpClient {
        return HttpClient(MockEngine(handler)) {
            install(ContentNegotiation) {
                json(AppJson)
            }
        }
    }

    private fun context(connectionId: String = "conn-1", config: ConnectionConfig) = ExecutionContext(
        releaseId = ReleaseId("release-1"),
        parameters = emptyList(),
        blockOutputs = emptyMap(),
        connections = mapOf(ConnectionId(connectionId) to config),
    )

    /**
     * Poll until condition is true, avoiding flaky delay()-based waits.
     */
    private suspend fun waitUntil(
        timeoutMs: Long = 5000,
        intervalMs: Long = 10,
        condition: suspend () -> Boolean,
    ) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return
            delay(intervalMs)
        }
        throw AssertionError("waitUntil timed out after ${timeoutMs}ms")
    }

    // --- TeamCity Build Executor ---

    @Test
    fun `teamcity executor triggers build and returns outputs on webhook completion`() = runBlocking {
        var capturedUrl: String? = null

        val client = mockClient { request ->
            capturedUrl = request.url.toString()
            respond(
                """{"id":"42","buildTypeId":"bt1","state":"queued"}""",
                HttpStatusCode.OK,
                headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }

        val webhookRepo = InMemoryPendingWebhookRepository()
        val connectionsRepo = FakeConnectionsRepository()
        val webhookService = WebhookService(webhookRepo, connectionsRepo)

        val executor = TeamCityBuildExecutor(client, webhookRepo, webhookService)

        val block = Block.ActionBlock(
            id = BlockId("tc-1"),
            name = "Build",
            type = BlockType.TEAMCITY_BUILD,
            connectionId = ConnectionId("conn-1"),
        )

        // Launch execution in background (it will wait for webhook)
        val outputsDeferred = async {
            executor.execute(
                block = block,
                parameters = listOf(Parameter(key = "buildTypeId", value = "bt1")),
                context = context(
                    config = ConnectionConfig.TeamCityConfig(
                        serverUrl = "https://tc.example.com",
                        token = "tc-token",
                    ),
                ),
            )
        }

        // Wait for the webhook to be registered
        waitUntil {
            webhookRepo.findByConnectionIdAndType(ConnectionId("conn-1"), WebhookType.TEAMCITY).isNotEmpty()
        }

        val pendingWebhooks = webhookRepo.findByConnectionIdAndType(ConnectionId("conn-1"), WebhookType.TEAMCITY)
        assertEquals(1, pendingWebhooks.size)
        assertEquals("42", pendingWebhooks[0].externalId)

        // Simulate webhook arrival via the public emitCompletion method
        webhookService.emitCompletion(
            WebhookCompletion(
                webhookId = pendingWebhooks[0].id,
                blockId = block.id,
                releaseId = ReleaseId("release-1"),
                type = WebhookType.TEAMCITY,
                payload = """{"buildNumber":"42","buildStatus":"SUCCESS"}""",
            )
        )

        val outputs = outputsDeferred.await()
        assertEquals("42", outputs["buildNumber"])
        assertTrue(outputs["buildUrl"]!!.contains("tc.example.com"))
        assertEquals("SUCCESS", outputs["buildStatus"])
        assertTrue(capturedUrl!!.contains("/app/rest/buildQueue"))
    }

    @Test
    fun `teamcity executor throws on missing buildTypeId`() = runBlocking {
        val client = mockClient { respond("", HttpStatusCode.OK) }
        val webhookRepo = InMemoryPendingWebhookRepository()
        val connectionsRepo = FakeConnectionsRepository()
        val webhookService = WebhookService(webhookRepo, connectionsRepo)
        val executor = TeamCityBuildExecutor(client, webhookRepo, webhookService)

        try {
            executor.execute(
                block = Block.ActionBlock(
                    id = BlockId("tc-1"), name = "Build",
                    type = BlockType.TEAMCITY_BUILD, connectionId = ConnectionId("conn-1"),
                ),
                parameters = emptyList(),
                context = context(
                    config = ConnectionConfig.TeamCityConfig(serverUrl = "https://tc.example.com", token = "t"),
                ),
            )
            assertTrue(false, "Should have thrown")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("buildTypeId"))
        }
    }

    // --- GitHub Action Executor ---

    @Test
    fun `github action executor triggers dispatch and returns outputs on webhook completion`() = runBlocking {
        val client = mockClient { request ->
            val url = request.url.toString()
            when {
                url.contains("/dispatches") ->
                    respond("", HttpStatusCode.NoContent)
                url.contains("/runs") ->
                    respond(
                        """{"workflow_runs":[{"id":789,"html_url":"https://github.com/o/r/actions/runs/789"}]}""",
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                    )
                else ->
                    respond("ok", HttpStatusCode.OK)
            }
        }

        val webhookRepo = InMemoryPendingWebhookRepository()
        val connectionsRepo = FakeConnectionsRepository()
        val webhookService = WebhookService(webhookRepo, connectionsRepo)

        val executor = GitHubActionExecutor(client, webhookRepo, webhookService)

        val block = Block.ActionBlock(
            id = BlockId("gh-action-1"),
            name = "Run CI",
            type = BlockType.GITHUB_ACTION,
            connectionId = ConnectionId("conn-1"),
        )

        val outputsDeferred = async {
            executor.execute(
                block = block,
                parameters = listOf(
                    Parameter(key = "workflowFile", value = "ci.yml"),
                    Parameter(key = "ref", value = "main"),
                ),
                context = context(
                    config = ConnectionConfig.GitHubConfig(
                        token = "ghp_test",
                        owner = "o",
                        repo = "r",
                    ),
                ),
            )
        }

        // Wait for the webhook to be registered (poll instead of delay)
        waitUntil {
            webhookRepo.findByConnectionIdAndType(ConnectionId("conn-1"), WebhookType.GITHUB).isNotEmpty()
        }

        val pendingWebhooks = webhookRepo.findByConnectionIdAndType(ConnectionId("conn-1"), WebhookType.GITHUB)
        assertEquals(1, pendingWebhooks.size)
        assertEquals("789", pendingWebhooks[0].externalId)

        // Simulate webhook arrival
        val payload = """{"workflow_run":{"id":789,"html_url":"https://github.com/o/r/actions/runs/789","conclusion":"success"}}"""
        webhookRepo.updateStatus(pendingWebhooks[0].id, WebhookStatus.COMPLETED, payload)
        webhookService.emitCompletion(
            WebhookCompletion(
                webhookId = pendingWebhooks[0].id,
                blockId = block.id,
                releaseId = ReleaseId("release-1"),
                type = WebhookType.GITHUB,
                payload = payload,
            )
        )

        val outputs = outputsDeferred.await()
        assertEquals("789", outputs["runId"])
        assertTrue(outputs["runUrl"]!!.contains("actions/runs/789"))
        assertEquals("success", outputs["runStatus"])
    }

    @Test
    fun `github action executor throws on missing workflowFile`() = runBlocking {
        val client = mockClient { respond("", HttpStatusCode.OK) }
        val webhookRepo = InMemoryPendingWebhookRepository()
        val connectionsRepo = FakeConnectionsRepository()
        val webhookService = WebhookService(webhookRepo, connectionsRepo)
        val executor = GitHubActionExecutor(client, webhookRepo, webhookService)

        try {
            executor.execute(
                block = Block.ActionBlock(
                    id = BlockId("gh-1"), name = "Action",
                    type = BlockType.GITHUB_ACTION, connectionId = ConnectionId("conn-1"),
                ),
                parameters = emptyList(),
                context = context(
                    config = ConnectionConfig.GitHubConfig(token = "t", owner = "o", repo = "r"),
                ),
            )
            assertTrue(false, "Should have thrown")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("workflowFile"))
        }
    }
}

// --- Test helpers ---

/**
 * In-memory PendingWebhookRepository for unit tests.
 */
class InMemoryPendingWebhookRepository : PendingWebhookRepository {
    private val webhooks = mutableListOf<PendingWebhook>()
    private var idCounter = 0

    override suspend fun create(
        externalId: String,
        blockId: BlockId,
        releaseId: ReleaseId,
        connectionId: ConnectionId,
        type: WebhookType,
    ): PendingWebhook {
        val now = Clock.System.now()
        val webhook = PendingWebhook(
            id = "wh-${++idCounter}",
            externalId = externalId,
            blockId = blockId,
            releaseId = releaseId,
            connectionId = connectionId,
            type = type,
            status = WebhookStatus.PENDING,
            createdAt = now,
            updatedAt = now,
        )
        webhooks.add(webhook)
        return webhook
    }

    override suspend fun findByExternalIdAndType(externalId: String, type: WebhookType): PendingWebhook? {
        return webhooks.find { it.externalId == externalId && it.type == type }
    }

    override suspend fun findByConnectionIdAndType(connectionId: ConnectionId, type: WebhookType): List<PendingWebhook> {
        return webhooks.filter { it.connectionId == connectionId && it.type == type && it.status == WebhookStatus.PENDING }
    }

    override suspend fun findPendingByReleaseId(releaseId: ReleaseId): List<PendingWebhook> {
        return webhooks.filter { it.releaseId == releaseId && it.status == WebhookStatus.PENDING }
    }

    override suspend fun updateStatus(id: String, status: WebhookStatus, payload: String?): Boolean {
        val idx = webhooks.indexOfFirst { it.id == id }
        if (idx < 0) return false
        webhooks[idx] = webhooks[idx].copy(status = status, payload = payload)
        return true
    }
}

/**
 * Fake ConnectionsRepository for unit tests (not used by executors directly).
 */
class FakeConnectionsRepository : com.github.mr3zee.connections.ConnectionsRepository {
    override suspend fun findAll() = emptyList<Connection>()
    override suspend fun findById(id: ConnectionId) = null
    override suspend fun create(name: String, type: ConnectionType, config: ConnectionConfig) =
        throw UnsupportedOperationException()
    override suspend fun update(id: ConnectionId, name: String?, config: ConnectionConfig?) = null
    override suspend fun delete(id: ConnectionId) = false
}
