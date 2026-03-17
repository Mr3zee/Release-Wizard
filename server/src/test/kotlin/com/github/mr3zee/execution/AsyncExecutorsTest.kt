package com.github.mr3zee.execution

import com.github.mr3zee.AppJson
import com.github.mr3zee.connections.ConnectionsRepository
import com.github.mr3zee.execution.executors.GitHubActionExecutor
import com.github.mr3zee.execution.executors.TeamCityArtifactService
import com.github.mr3zee.execution.executors.TeamCityBuildExecutor
import com.github.mr3zee.model.*
import com.github.mr3zee.WebhookConfig
import com.github.mr3zee.webhooks.*
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import com.github.mr3zee.waitUntil
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
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
        val buildUrl = outputs["buildUrl"] ?: error("outputs should contain 'buildUrl'")
        assertTrue(buildUrl.contains("tc.example.com"))
        assertEquals("SUCCESS", outputs["buildStatus"])
        val requestUrl = capturedUrl ?: error("capturedUrl should have been set by the mock client")
        assertTrue(requestUrl.contains("/app/rest/buildQueue"))
    }

    @Test
    fun `teamcity executor forwards custom parameters in XML properties`() = runBlocking {
        var capturedBody: String? = null

        val client = mockClient { request ->
            capturedBody = request.body.toByteArray().decodeToString()
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
            id = BlockId("tc-1"), name = "Build",
            type = BlockType.TEAMCITY_BUILD, connectionId = ConnectionId("conn-1"),
        )

        val outputsDeferred = async {
            executor.execute(
                block = block,
                parameters = listOf(
                    Parameter(key = "buildTypeId", value = "bt1"),
                    Parameter(key = "branch", value = "main"),
                    Parameter(key = "env.VERSION", value = "2.0.0"),
                    Parameter(key = "env.TARGET", value = "production"),
                    Parameter(key = "artifactsGlob", value = "**/*.jar"),
                ),
                context = context(
                    config = ConnectionConfig.TeamCityConfig(serverUrl = "https://tc.example.com", token = "t"),
                ),
            )
        }

        waitUntil {
            webhookRepo.findByConnectionIdAndType(ConnectionId("conn-1"), WebhookType.TEAMCITY).isNotEmpty()
        }

        val body = capturedBody ?: error("Request body should have been captured")
        // System keys should NOT be in <properties>
        assertFalse(body.contains("""name="buildTypeId""""))
        assertFalse(body.contains("""name="branch""""))
        assertFalse(body.contains("""name="artifactsGlob""""))
        // Custom params SHOULD be in <properties>
        assertTrue(body.contains("<properties>"))
        assertTrue(body.contains("""name="env.VERSION" value="2.0.0""""))
        assertTrue(body.contains("""name="env.TARGET" value="production""""))
        // buildTypeId and branch should be in their own XML elements
        assertTrue(body.contains("""<buildType id="bt1"/>"""))
        assertTrue(body.contains("<branchName>main</branchName>"))

        // Complete the webhook to finish the test
        val pendingWebhooks = webhookRepo.findByConnectionIdAndType(ConnectionId("conn-1"), WebhookType.TEAMCITY)
        webhookService.emitCompletion(
            WebhookCompletion(
                webhookId = pendingWebhooks[0].id,
                blockId = block.id,
                releaseId = ReleaseId("release-1"),
                type = WebhookType.TEAMCITY,
                payload = """{"buildNumber":"42","buildStatus":"SUCCESS"}""",
            )
        )
        outputsDeferred.await()
        Unit
    }

    @Test
    fun `teamcity executor omits properties element when no custom params`() = runBlocking {
        var capturedBody: String? = null

        val client = mockClient { request ->
            capturedBody = request.body.toByteArray().decodeToString()
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
            id = BlockId("tc-1"), name = "Build",
            type = BlockType.TEAMCITY_BUILD, connectionId = ConnectionId("conn-1"),
        )

        val outputsDeferred = async {
            executor.execute(
                block = block,
                parameters = listOf(
                    Parameter(key = "buildTypeId", value = "bt1"),
                    Parameter(key = "branch", value = "main"),
                ),
                context = context(
                    config = ConnectionConfig.TeamCityConfig(serverUrl = "https://tc.example.com", token = "t"),
                ),
            )
        }

        waitUntil {
            webhookRepo.findByConnectionIdAndType(ConnectionId("conn-1"), WebhookType.TEAMCITY).isNotEmpty()
        }

        val body = capturedBody ?: error("Request body should have been captured")
        assertFalse(body.contains("<properties>"), "XML should not contain <properties> when there are no custom params")

        val pendingWebhooks = webhookRepo.findByConnectionIdAndType(ConnectionId("conn-1"), WebhookType.TEAMCITY)
        webhookService.emitCompletion(
            WebhookCompletion(
                webhookId = pendingWebhooks[0].id,
                blockId = block.id,
                releaseId = ReleaseId("release-1"),
                type = WebhookType.TEAMCITY,
                payload = """{"buildNumber":"42","buildStatus":"SUCCESS"}""",
            )
        )
        outputsDeferred.await()
        Unit
    }

    @Test
    fun `teamcity escapeXml handles all special characters`() {
        assertEquals("a &amp; b", TeamCityBuildExecutor.escapeXml("a & b"))
        assertEquals("&lt;script&gt;", TeamCityBuildExecutor.escapeXml("<script>"))
        assertEquals("value=&quot;x&quot;", TeamCityBuildExecutor.escapeXml("value=\"x\""))
        assertEquals("it&apos;s", TeamCityBuildExecutor.escapeXml("it's"))
        assertEquals("a &amp; b &lt; c &gt; d", TeamCityBuildExecutor.escapeXml("a & b < c > d"))
        assertEquals("plain text", TeamCityBuildExecutor.escapeXml("plain text"))
    }

    @Test
    fun `teamcity executor throws on missing buildTypeId`() = runBlocking {
        val client = mockClient { respond("", HttpStatusCode.OK) }
        val webhookRepo = InMemoryPendingWebhookRepository()
        val connectionsRepo = FakeConnectionsRepository()
        val webhookService = WebhookService(webhookRepo, connectionsRepo)
        val executor = TeamCityBuildExecutor(client, webhookRepo, webhookService)

        val e = assertFailsWith<IllegalArgumentException> {
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
        }
        val message = e.message ?: error("IllegalArgumentException should have a message")
        assertTrue(message.contains("buildTypeId"))
    }

    @Test
    fun `teamcity executor includes artifacts in outputs when artifactsGlob parameter present`() = runBlocking {
        val client = mockClient { request ->
            val url = request.url.toString()
            when {
                url.contains("/app/rest/buildQueue") -> respond(
                    """{"id":"42","buildTypeId":"bt1","state":"queued"}""",
                    HttpStatusCode.OK,
                    headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                )
                url.contains("/artifacts/children") -> respond(
                    """{"file":[{"name":"app.jar","size":1024},{"name":"readme.txt","size":256}]}""",
                    HttpStatusCode.OK,
                    headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                )
                else -> respond("{}", HttpStatusCode.OK)
            }
        }

        val webhookRepo = InMemoryPendingWebhookRepository()
        val connectionsRepo = FakeConnectionsRepository()
        val webhookService = WebhookService(webhookRepo, connectionsRepo)
        val artifactService = TeamCityArtifactService(client)

        val executor = TeamCityBuildExecutor(client, webhookRepo, webhookService, artifactService)

        val block = Block.ActionBlock(
            id = BlockId("tc-art"),
            name = "Build",
            type = BlockType.TEAMCITY_BUILD,
            connectionId = ConnectionId("conn-1"),
        )

        val outputsDeferred = async {
            executor.execute(
                block = block,
                parameters = listOf(
                    Parameter(key = "buildTypeId", value = "bt1"),
                    Parameter(key = "artifactsGlob", value = "**/*.jar"),
                ),
                context = context(
                    config = ConnectionConfig.TeamCityConfig(
                        serverUrl = "https://tc.example.com",
                        token = "tc-token",
                    ),
                ),
            )
        }

        waitUntil {
            webhookRepo.findByConnectionIdAndType(ConnectionId("conn-1"), WebhookType.TEAMCITY).isNotEmpty()
        }

        val pendingWebhooks = webhookRepo.findByConnectionIdAndType(ConnectionId("conn-1"), WebhookType.TEAMCITY)
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
        assertEquals("SUCCESS", outputs["buildStatus"])
        assertTrue(outputs.containsKey("artifacts"), "Should have artifacts key")
        val artifacts = outputs["artifacts"] ?: error("outputs should contain 'artifacts'")
        assertTrue(artifacts.contains("app.jar"), "Should contain app.jar")
        assertTrue(!artifacts.contains("readme.txt"), "Should not contain readme.txt (filtered by glob)")
    }

    @Test
    fun `teamcity executor skips artifacts when no artifactsGlob parameter`() = runBlocking {
        val client = mockClient { respond(
            """{"id":"42","buildTypeId":"bt1","state":"queued"}""",
            HttpStatusCode.OK,
            headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
        )}

        val webhookRepo = InMemoryPendingWebhookRepository()
        val connectionsRepo = FakeConnectionsRepository()
        val webhookService = WebhookService(webhookRepo, connectionsRepo)
        val artifactService = TeamCityArtifactService(client)

        val executor = TeamCityBuildExecutor(client, webhookRepo, webhookService, artifactService)

        val block = Block.ActionBlock(
            id = BlockId("tc-no-art"),
            name = "Build",
            type = BlockType.TEAMCITY_BUILD,
            connectionId = ConnectionId("conn-1"),
        )

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

        waitUntil {
            webhookRepo.findByConnectionIdAndType(ConnectionId("conn-1"), WebhookType.TEAMCITY).isNotEmpty()
        }

        val pendingWebhooks = webhookRepo.findByConnectionIdAndType(ConnectionId("conn-1"), WebhookType.TEAMCITY)
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
        assertTrue(!outputs.containsKey("artifacts"), "Should not have artifacts key without glob parameter")
    }

    @Test
    fun `teamcity executor respects custom maxDepth and maxFiles parameters`() = runBlocking {
        val client = mockClient { request ->
            val url = request.url.toString()
            when {
                url.contains("/app/rest/buildQueue") -> respond(
                    """{"id":"42","buildTypeId":"bt1","state":"queued"}""",
                    HttpStatusCode.OK,
                    headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                )
                url.contains("/artifacts/children") -> respond(
                    """{"file":[{"name":"a.jar","size":100},{"name":"b.jar","size":200},{"name":"c.jar","size":300}]}""",
                    HttpStatusCode.OK,
                    headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                )
                else -> respond("{}", HttpStatusCode.OK)
            }
        }

        val webhookRepo = InMemoryPendingWebhookRepository()
        val connectionsRepo = FakeConnectionsRepository()
        val webhookService = WebhookService(webhookRepo, connectionsRepo)
        val artifactService = TeamCityArtifactService(client)

        val executor = TeamCityBuildExecutor(client, webhookRepo, webhookService, artifactService)

        val block = Block.ActionBlock(
            id = BlockId("tc-custom"),
            name = "Build",
            type = BlockType.TEAMCITY_BUILD,
            connectionId = ConnectionId("conn-1"),
        )

        val outputsDeferred = async {
            executor.execute(
                block = block,
                parameters = listOf(
                    Parameter(key = "buildTypeId", value = "bt1"),
                    Parameter(key = "artifactsGlob", value = "**/*.jar"),
                    Parameter(key = "artifactsMaxDepth", value = "5"),
                    Parameter(key = "artifactsMaxFiles", value = "2"),
                ),
                context = context(
                    config = ConnectionConfig.TeamCityConfig(
                        serverUrl = "https://tc.example.com",
                        token = "tc-token",
                    ),
                ),
            )
        }

        waitUntil {
            webhookRepo.findByConnectionIdAndType(ConnectionId("conn-1"), WebhookType.TEAMCITY).isNotEmpty()
        }

        val pendingWebhooks = webhookRepo.findByConnectionIdAndType(ConnectionId("conn-1"), WebhookType.TEAMCITY)
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
        assertTrue(outputs.containsKey("artifacts"))
        val artifactsJson = outputs["artifacts"] ?: error("outputs should contain 'artifacts'")
        val artifacts = Json.decodeFromString<List<String>>(artifactsJson)
        assertEquals(2, artifacts.size, "maxFiles=2 should limit to 2 artifacts")
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
        val runUrl = outputs["runUrl"] ?: error("outputs should contain 'runUrl'")
        assertTrue(runUrl.contains("actions/runs/789"))
        assertEquals("success", outputs["runStatus"])
    }

    @Test
    fun `github action executor throws on missing workflowFile`() = runBlocking {
        val client = mockClient { respond("", HttpStatusCode.OK) }
        val webhookRepo = InMemoryPendingWebhookRepository()
        val connectionsRepo = FakeConnectionsRepository()
        val webhookService = WebhookService(webhookRepo, connectionsRepo)
        val executor = GitHubActionExecutor(client, webhookRepo, webhookService)

        val e = assertFailsWith<IllegalArgumentException> {
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
        }
        val message = e.message ?: error("IllegalArgumentException should have a message")
        assertTrue(message.contains("workflowFile"))
    }

    // --- TeamCity Webhook URL Injection ---

    @Test
    fun `teamcity executor includes webhook params when injectWebhookUrl is true`() = runBlocking {
        var capturedBody: String? = null

        val client = mockClient { request ->
            capturedBody = request.body.toByteArray().decodeToString()
            respond(
                """{"id":"42","buildTypeId":"bt1","state":"queued"}""",
                HttpStatusCode.OK,
                headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }

        val webhookRepo = InMemoryPendingWebhookRepository()
        val connectionsRepo = FakeConnectionsRepository()
        val webhookService = WebhookService(webhookRepo, connectionsRepo)

        val releasesRepo = InMemoryReleasesRepository()
        val stubExecutor = StubBlockExecutor()
        val engineScope = CoroutineScope(SupervisorJob())
        val engine = ExecutionEngine(releasesRepo, stubExecutor, connectionsRepo, engineScope)
        val statusWebhookService = StatusWebhookService(
            InMemoryStatusWebhookTokenRepository(),
            releasesRepo,
            engine,
            WebhookConfig(baseUrl = "http://localhost:8080"),
        )

        val executor = TeamCityBuildExecutor(client, webhookRepo, webhookService, statusWebhookService = statusWebhookService)

        val block = Block.ActionBlock(
            id = BlockId("tc-webhook"),
            name = "Build",
            type = BlockType.TEAMCITY_BUILD,
            connectionId = ConnectionId("conn-1"),
            injectWebhookUrl = true,
        )

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

        waitUntil {
            webhookRepo.findByConnectionIdAndType(ConnectionId("conn-1"), WebhookType.TEAMCITY).isNotEmpty()
        }

        val body = capturedBody ?: error("Request body should have been captured")
        assertTrue(body.contains(TeamCityBuildExecutor.WEBHOOK_URL_PARAM), "XML should contain webhook URL param")
        assertTrue(body.contains(TeamCityBuildExecutor.WEBHOOK_TOKEN_PARAM), "XML should contain webhook token param")
        assertTrue(body.contains("http://localhost:8080"), "XML should contain the webhook base URL")

        // Complete the webhook to let the executor finish
        val pendingWebhooks = webhookRepo.findByConnectionIdAndType(ConnectionId("conn-1"), WebhookType.TEAMCITY)
        webhookService.emitCompletion(
            WebhookCompletion(
                webhookId = pendingWebhooks[0].id,
                blockId = block.id,
                releaseId = ReleaseId("release-1"),
                type = WebhookType.TEAMCITY,
                payload = """{"buildNumber":"42","buildStatus":"SUCCESS"}""",
            )
        )
        outputsDeferred.await()
        engineScope.cancel()
    }

    @Test
    fun `teamcity executor does NOT include webhook params when injectWebhookUrl is false`() = runBlocking {
        var capturedBody: String? = null

        val client = mockClient { request ->
            capturedBody = request.body.toByteArray().decodeToString()
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
            id = BlockId("tc-no-webhook"),
            name = "Build",
            type = BlockType.TEAMCITY_BUILD,
            connectionId = ConnectionId("conn-1"),
            injectWebhookUrl = false,
        )

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

        waitUntil {
            webhookRepo.findByConnectionIdAndType(ConnectionId("conn-1"), WebhookType.TEAMCITY).isNotEmpty()
        }

        val body = capturedBody ?: error("Request body should have been captured")
        assertFalse(body.contains(TeamCityBuildExecutor.WEBHOOK_URL_PARAM), "XML should NOT contain webhook URL param")
        assertFalse(body.contains(TeamCityBuildExecutor.WEBHOOK_TOKEN_PARAM), "XML should NOT contain webhook token param")

        // Complete the webhook to let the executor finish
        val pendingWebhooks = webhookRepo.findByConnectionIdAndType(ConnectionId("conn-1"), WebhookType.TEAMCITY)
        webhookService.emitCompletion(
            WebhookCompletion(
                webhookId = pendingWebhooks[0].id,
                blockId = block.id,
                releaseId = ReleaseId("release-1"),
                type = WebhookType.TEAMCITY,
                payload = """{"buildNumber":"42","buildStatus":"SUCCESS"}""",
            )
        )
        outputsDeferred.await()
        Unit
    }

    // --- Resume: webhook token deactivation ---

    @Test
    fun `teamcity executor deactivates webhook token after resume with completed webhook`() = runBlocking<Unit> {
        val client = mockClient { respond(
            """{"id":"42","buildTypeId":"bt1","state":"queued"}""",
            HttpStatusCode.OK,
            headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
        )}

        val webhookRepo = InMemoryPendingWebhookRepository()
        val connectionsRepo = FakeConnectionsRepository()
        val webhookService = WebhookService(webhookRepo, connectionsRepo)

        val releasesRepo = InMemoryReleasesRepository()
        val stubExecutor = StubBlockExecutor()
        val engineScope = CoroutineScope(SupervisorJob())
        val engine = ExecutionEngine(releasesRepo, stubExecutor, connectionsRepo, engineScope)
        val tokenRepo = InMemoryStatusWebhookTokenRepository()
        val statusWebhookService = StatusWebhookService(
            tokenRepo,
            releasesRepo,
            engine,
            WebhookConfig(baseUrl = "http://localhost:8080"),
        )

        val block = Block.ActionBlock(
            id = BlockId("tc-resume"),
            name = "Build",
            type = BlockType.TEAMCITY_BUILD,
            connectionId = ConnectionId("conn-1"),
            injectWebhookUrl = true,
        )

        val releaseId = ReleaseId("release-1")
        val ctx = context(config = ConnectionConfig.TeamCityConfig(
            serverUrl = "https://tc.example.com",
            token = "tc-token",
        ))

        // Pre-create a token (as if execute() created one before crash)
        val token = statusWebhookService.createToken(releaseId, block.id)

        // Pre-insert a COMPLETED webhook (as if the TC webhook arrived while server was down)
        val pendingWebhook = webhookRepo.create(
            externalId = "42",
            blockId = block.id,
            releaseId = releaseId,
            connectionId = ConnectionId("conn-1"),
            type = WebhookType.TEAMCITY,
        )
        webhookRepo.updateStatus(pendingWebhook.id, WebhookStatus.COMPLETED, """{"buildNumber":"42","buildStatus":"SUCCESS"}""")

        val executor = TeamCityBuildExecutor(client, webhookRepo, webhookService, statusWebhookService = statusWebhookService)

        // Call resume — should detect COMPLETED webhook and return outputs
        val outputs = executor.resume(block, listOf(Parameter(key = "buildTypeId", value = "bt1")), ctx)

        assertEquals("42", outputs["buildNumber"])
        assertEquals("SUCCESS", outputs["buildStatus"])

        // Verify the token was deactivated
        val tokenRecord = tokenRepo.findByToken(token)
        assertNotNull(tokenRecord, "Token record should still exist")
        assertFalse(tokenRecord.active, "Token should have been deactivated after resume")

        engineScope.cancel()
    }

    // --- BlockExecution Serialization ---

    @Test
    fun `BlockExecution serialization backward compatibility — without webhookStatus`() {
        val json = """
            {
                "blockId": "b-1",
                "releaseId": "r-1",
                "status": "RUNNING",
                "outputs": {},
                "approvals": []
            }
        """.trimIndent()

        val execution = AppJson.decodeFromString<BlockExecution>(json)
        assertEquals(BlockId("b-1"), execution.blockId)
        assertEquals(ReleaseId("r-1"), execution.releaseId)
        assertEquals(BlockStatus.RUNNING, execution.status)
        assertNull(execution.webhookStatus, "webhookStatus should be null when absent from JSON")
    }

    @Test
    fun `BlockExecution serialization round-trip — with webhookStatus`() {
        val now = Clock.System.now()
        val original = BlockExecution(
            blockId = BlockId("b-2"),
            releaseId = ReleaseId("r-2"),
            status = BlockStatus.RUNNING,
            webhookStatus = WebhookStatusUpdate(
                status = "COMPILING",
                description = "Step 3 of 5",
                receivedAt = now,
            ),
        )

        val encoded = AppJson.encodeToString(original)
        val decoded = AppJson.decodeFromString<BlockExecution>(encoded)

        assertEquals(original, decoded)
        val webhookStatus = decoded.webhookStatus ?: error("webhookStatus should not be null after round-trip")
        assertEquals("COMPILING", webhookStatus.status)
        assertEquals("Step 3 of 5", webhookStatus.description)
        assertEquals(now, webhookStatus.receivedAt)
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

    override suspend fun findPendingByExternalIdAndType(externalId: String, type: WebhookType): PendingWebhook? {
        return webhooks.find { it.externalId == externalId && it.type == type && it.status == WebhookStatus.PENDING }
    }

    override suspend fun findByConnectionIdAndType(connectionId: ConnectionId, type: WebhookType): List<PendingWebhook> {
        return webhooks.filter { it.connectionId == connectionId && it.type == type && it.status == WebhookStatus.PENDING }
    }

    override suspend fun findPendingByReleaseId(releaseId: ReleaseId): List<PendingWebhook> {
        return webhooks.filter { it.releaseId == releaseId && it.status == WebhookStatus.PENDING }
    }

    override suspend fun findByReleaseIdAndBlockId(releaseId: ReleaseId, blockId: BlockId): PendingWebhook? {
        return webhooks.find { it.releaseId == releaseId && it.blockId == blockId }
    }

    override suspend fun updateStatus(id: String, status: WebhookStatus, payload: String?): Boolean {
        val idx = webhooks.indexOfFirst { it.id == id }
        if (idx < 0) return false
        webhooks[idx] = webhooks[idx].copy(status = status, payload = payload)
        return true
    }

    override suspend fun updateExternalId(id: String, externalId: String): Boolean {
        val idx = webhooks.indexOfFirst { it.id == id }
        if (idx < 0) return false
        webhooks[idx] = webhooks[idx].copy(externalId = externalId)
        return true
    }

    override suspend fun deleteCompletedOlderThan(cutoff: kotlin.time.Instant): Int {
        val before = webhooks.size
        webhooks.removeAll { it.status == WebhookStatus.COMPLETED && it.updatedAt < cutoff }
        return before - webhooks.size
    }
}

/**
 * Fake ConnectionsRepository for unit tests (not used by executors directly).
 */
class FakeConnectionsRepository : ConnectionsRepository {
    override suspend fun findAll(teamId: String?, teamIds: List<String>?, offset: Int, limit: Int, search: String?, type: ConnectionType?) = emptyList<Connection>()
    override suspend fun countAll(teamId: String?, teamIds: List<String>?, search: String?, type: ConnectionType?) = 0L
    override suspend fun findAllWithCount(teamId: String?, teamIds: List<String>?, offset: Int, limit: Int, search: String?, type: ConnectionType?) = emptyList<Connection>() to 0L
    override suspend fun findById(id: ConnectionId) = null
    override suspend fun findTeamId(id: ConnectionId): String? = null
    override suspend fun create(name: String, type: ConnectionType, config: ConnectionConfig, teamId: String) =
        throw UnsupportedOperationException()
    override suspend fun update(id: ConnectionId, name: String?, config: ConnectionConfig?) = null
    override suspend fun delete(id: ConnectionId) = false
}
