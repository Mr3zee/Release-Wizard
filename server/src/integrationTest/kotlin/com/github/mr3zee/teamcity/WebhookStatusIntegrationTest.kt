@file:Suppress("FunctionName")

package com.github.mr3zee.teamcity

import com.github.mr3zee.*
import com.github.mr3zee.api.ApiRoutes
import com.github.mr3zee.api.StatusUpdatePayload
import com.github.mr3zee.execution.ExecutionEngine
import com.github.mr3zee.execution.StubBlockExecutor
import com.github.mr3zee.execution.FakeConnectionsRepository
import com.github.mr3zee.execution.executors.TeamCityBuildExecutor
import com.github.mr3zee.model.*
import com.github.mr3zee.persistence.ProjectTemplateTable
import com.github.mr3zee.persistence.TeamTable
import com.github.mr3zee.persistence.dataSource
import com.github.mr3zee.persistence.initDatabase
import com.github.mr3zee.releases.ExposedReleasesRepository
import com.github.mr3zee.webhooks.*
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.request.*
import io.ktor.server.plugins.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.*
import java.net.ServerSocket
import java.util.UUID
import kotlin.test.assertTrue
import kotlin.time.Clock

/**
 * Integration test that verifies the full webhook status flow with a real TeamCity instance.
 *
 * Prerequisites:
 * - TeamCity credentials in local.properties (teamcity.test.*)
 * - teamcity.test.webhookBuildTypeId pointing to the "Webhook Status Test" build config
 * - ngrok installed and available on PATH
 *
 * The test:
 * 1. Starts a local HTTP server
 * 2. Opens an ngrok tunnel to expose it
 * 3. Triggers a TC build that POSTs 3 status updates via the tunnel
 * 4. Polls the local DB for status changes
 * 5. Verifies all 3 statuses were observed
 */
class WebhookStatusIntegrationTest {

    companion object {
        private var config: TeamCityTestConfig? = null
        private var tcClient: HttpClient? = null

        @BeforeClass
        @JvmStatic
        fun setUp() {
            config = TeamCityTestConfig.loadOrNull()
            Assume.assumeNotNull(config)
            Assume.assumeNotNull(config?.webhookBuildTypeId)
            tcClient = createTeamCityTestHttpClient()
        }

        @AfterClass
        @JvmStatic
        fun tearDown() {
            tcClient?.close()
        }
    }

    private var triggeredBuildId: String? = null
    private var ngrokProcess: Process? = null
    private var server: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>? = null
    private var testDataSource: javax.sql.DataSource? = null
    private var testScope: CoroutineScope? = null

    @After
    fun cleanup() {
        // Cancel TC build
        val buildId = triggeredBuildId
        if (buildId != null) {
            triggeredBuildId = null
            runBlocking {
                tcClient?.cancelBuild(config ?: return@runBlocking, buildId)
            }
        }
        // Stop ngrok first (closes the tunnel so no more inbound requests)
        ngrokProcess?.destroyForcibly()
        ngrokProcess?.waitFor()
        ngrokProcess = null
        // Stop server with adequate timeouts for NIO selector cleanup
        server?.stop(1000, 2000)
        server = null
        // Cancel coroutine scope and wait for children to finish
        val scope = testScope
        if (scope != null) {
            scope.cancel()
            runBlocking { scope.coroutineContext[kotlinx.coroutines.Job]?.join() }
        }
        testScope = null
        // Close HikariCP connection pool
        (testDataSource as? com.zaxxer.hikari.HikariDataSource)?.close()
        testDataSource = null
    }

    @Test
    fun `TC build posts 3 status updates and all are observed`() {
        val cfg = config ?: error("Config should be loaded")
        val webhookBuildTypeId = cfg.webhookBuildTypeId ?: error("webhookBuildTypeId should be set")
        val client = tcClient ?: error("HttpClient should be initialized")

        // 1. Find a free port
        val port = ServerSocket(0).use { it.localPort }

        // 2. Set up in-memory H2 database (no DB_CLOSE_DELAY to allow clean JVM exit)
        val ds = dataSource(DatabaseConfig(
            url = "jdbc:h2:mem:test_${System.nanoTime()};MODE=PostgreSQL",
            user = "sa", password = "", driver = "org.h2.Driver",
        ))
        testDataSource = ds
        val db = initDatabase(ds)

        // 3. Set up services
        val releasesRepo = ExposedReleasesRepository(db)
        val tokenRepo = ExposedStatusWebhookTokenRepository(db)
        val connectionsRepo = FakeConnectionsRepository()
        val executor = StubBlockExecutor()
        val scope = CoroutineScope(SupervisorJob())
        testScope = scope
        val engine = ExecutionEngine(releasesRepo, executor, connectionsRepo, scope)
        val webhookConfig = WebhookConfig(baseUrl = "http://localhost:$port") // placeholder, will be replaced with ngrok URL
        val statusService = StatusWebhookService(tokenRepo, releasesRepo, engine, webhookConfig)

        // 4. Create a release + RUNNING block execution
        val blockId = BlockId("webhook-test-block")
        // Insert team + project template (FK targets for releases)
        val teamUuid = UUID.fromString("00000000-0000-0000-0000-000000000001")
        val projectId = ProjectId(UUID.randomUUID().toString())
        val now = Clock.System.now()
        transaction(db) {
            TeamTable.insert {
                it[TeamTable.id] = teamUuid
                it[TeamTable.name] = "test-team"
                it[TeamTable.createdAt] = now
            }
            ProjectTemplateTable.insert {
                it[ProjectTemplateTable.id] = UUID.fromString(projectId.value)
                it[ProjectTemplateTable.name] = "Webhook Integration Test"
                it[ProjectTemplateTable.description] = ""
                it[ProjectTemplateTable.dagGraph] = DagGraph()
                it[ProjectTemplateTable.parameters] = emptyList()
                it[ProjectTemplateTable.defaultTags] = emptyList()
                it[ProjectTemplateTable.teamId] = teamUuid
                it[ProjectTemplateTable.createdAt] = now
                it[ProjectTemplateTable.updatedAt] = now
            }
        }

        val release = runBlocking { releasesRepo.create(projectId, DagGraph(), emptyList(), "00000000-0000-0000-0000-000000000001") }
        runBlocking {
            releasesRepo.upsertBlockExecution(
                BlockExecution(blockId = blockId, releaseId = release.id, status = BlockStatus.RUNNING, startedAt = Clock.System.now())
            )
        }

        // 5. Create a webhook token
        val token = runBlocking { statusService.createToken(release.id, blockId) }

        // 6. Start ngrok tunnel
        val ngrokUrl = runBlocking { startNgrok(port) }
        println("Ngrok tunnel: $ngrokUrl -> localhost:$port")

        // 7. Start the minimal HTTP server — NOT inside runBlocking to avoid scope inheritance
        // CIO engine used instead of Netty — daemon threads ensure clean shutdown in tests
        server = embeddedServer(CIO, port = port) {
            install(ContentNegotiation) { json(AppJson) }
            routing {
                post(ApiRoutes.Webhooks.STATUS) {
                    val authHeader = call.request.header("Authorization")
                    if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                        call.respond(HttpStatusCode.NotFound, "Not found")
                        return@post
                    }
                    val tokenStr = authHeader.removePrefix("Bearer ").trim()
                    val parsedToken = try {
                        UUID.fromString(tokenStr)
                    } catch (_: IllegalArgumentException) {
                        call.respond(HttpStatusCode.NotFound, "Not found")
                        return@post
                    }
                    val payload = try {
                        call.receive<StatusUpdatePayload>()
                    } catch (_: Exception) {
                        call.respond(HttpStatusCode.BadRequest, "Invalid body")
                        return@post
                    }
                    when (statusService.processStatusUpdate(parsedToken, payload)) {
                        StatusWebhookResult.Accepted -> call.respond(HttpStatusCode.OK, "OK")
                        StatusWebhookResult.NotFound -> call.respond(HttpStatusCode.NotFound, "Not found")
                        is StatusWebhookResult.BadRequest -> call.respond(HttpStatusCode.BadRequest, "Bad request")
                    }
                }
            }
        }.start(wait = false)

        // 8. Trigger TC build with webhook URL + token as parameters
        val xmlBody = buildString {
            append("<build>")
            append("""<buildType id="${TeamCityBuildExecutor.escapeXml(webhookBuildTypeId)}"/>""")
            append("<properties>")
            append("""<property name="env.RELEASE_WIZARD_WEBHOOK_URL" value="${TeamCityBuildExecutor.escapeXml("$ngrokUrl${ApiRoutes.Webhooks.STATUS}")}"/>""")
            append("""<property name="env.RELEASE_WIZARD_WEBHOOK_TOKEN" value="${TeamCityBuildExecutor.escapeXml(token.toString())}"/>""")
            append("</properties>")
            append("</build>")
        }

        val triggerResponse = runBlocking {
            client.post("${cfg.serverUrl}/app/rest/buildQueue") {
                header("Authorization", "Bearer ${cfg.token}")
                header("Accept", "application/json")
                contentType(ContentType.Application.Xml)
                setBody(xmlBody)
            }
        }
        assertTrue(triggerResponse.status.isSuccess(), "Build trigger should succeed: ${triggerResponse.status}")

        val responseBody = runBlocking { AppJson.decodeFromString<JsonObject>(triggerResponse.bodyAsText()) }
        val buildId = responseBody["id"]?.jsonPrimitive?.content ?: error("Missing build id in response")
        triggeredBuildId = buildId
        println("Triggered TC build: $buildId")

        // 9. Poll for status updates (collect distinct values over ~60 seconds)
        val observedStatuses = mutableSetOf<String>()
        val expectedStatuses = setOf("Step 1: Compiling", "Step 2: Testing", "Step 3: Deploying")

        runBlocking {
            waitUntil(maxAttempts = 120, delayMillis = 1000) {
                val execution = releasesRepo.findBlockExecution(release.id, blockId)
                val currentStatus = execution?.webhookStatus?.status
                if (currentStatus != null) {
                    if (observedStatuses.add(currentStatus)) {
                        println("Observed status: $currentStatus (${observedStatuses.size}/${expectedStatuses.size})")
                    }
                }
                observedStatuses.containsAll(expectedStatuses)
            }
        }

        println("All statuses observed: $observedStatuses")

        assertTrue(
            observedStatuses.containsAll(expectedStatuses),
            "Should have observed all 3 statuses. Observed: $observedStatuses, Expected: $expectedStatuses"
        )
    }

    /**
     * Starts ngrok, waits for it to be healthy, then returns the public HTTPS URL.
     * Uses a health-check loop against the ngrok local API instead of sleep-based waits.
     * The ngrok binary path is resolved from the `ngrok.path` system property (set by Gradle),
     * falling back to `ngrok` on PATH.
     */
    private suspend fun startNgrok(port: Int): String {
        // Kill any leftover ngrok processes from previous runs
        try {
            val killProcess = withContext(Dispatchers.IO) {
                ProcessBuilder("pkill", "-f", "ngrok http").start()
            }
            withContext(Dispatchers.IO) {
                killProcess.waitFor()
            }
        } catch (_: Exception) {}
        // Wait for the old ngrok process to fully exit by verifying port 4040 is free (ngrok API port)
        waitUntil(maxAttempts = 10, delayMillis = 200) {
            try {
                java.net.Socket("127.0.0.1", 4040).use { false }
            } catch (_: Exception) {
                true // Port is free, ngrok is gone
            }
        }

        val ngrokBinary = System.getProperty("ngrok.path") ?: "ngrok"
        val process = withContext(Dispatchers.IO) {
            ProcessBuilder(ngrokBinary, "http", port.toString(), "--log", "stdout", "--log-format", "json")
                .redirectErrorStream(true)
                .start()
        }
        ngrokProcess = process

        // Poll ngrok's local API until it reports a healthy tunnel with a public URL
        val ngrokApiClient = HttpClient(io.ktor.client.engine.cio.CIO)
        var publicUrl: String? = null
        ngrokApiClient.use { ngrokApiClient ->
            waitUntil(maxAttempts = 30, delayMillis = 1000) {
                try {
                    val response = ngrokApiClient.get("http://127.0.0.1:4040/api/tunnels")
                    if (!response.status.isSuccess()) return@waitUntil false
                    val body = AppJson.decodeFromString<JsonObject>(response.bodyAsText())
                    val tunnelsStr = body["tunnels"]?.toString() ?: return@waitUntil false
                    val httpsRegex = """"public_url"\s*:\s*"(https://[^"]+)"""".toRegex()
                    val match = httpsRegex.find(tunnelsStr)
                    if (match != null) {
                        publicUrl = match.groupValues[1]
                        true
                    } else false
                } catch (_: Exception) {
                    false // ngrok API not ready yet
                }
            }
        }

        return publicUrl ?: error("Failed to get ngrok public URL after 30 health-check attempts")
    }
}
