@file:Suppress("FunctionName")

package com.github.mr3zee.teamcity

import com.github.mr3zee.execution.ExecutionContext
import com.github.mr3zee.execution.FakeConnectionsRepository
import com.github.mr3zee.execution.InMemoryPendingWebhookRepository
import com.github.mr3zee.execution.executors.TeamCityBuildExecutor
import com.github.mr3zee.model.*
import com.github.mr3zee.webhooks.WebhookCompletion
import com.github.mr3zee.webhooks.WebhookService
import com.github.mr3zee.webhooks.WebhookType
import com.github.mr3zee.waitUntil
import io.ktor.client.*
import kotlinx.coroutines.async
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.fail
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TeamCityBuildExecutorIntegrationTest {

    companion object {
        private var config: TeamCityTestConfig? = null
        private var client: HttpClient? = null

        @BeforeClass
        @JvmStatic
        fun setUp() {
            config = TeamCityTestConfig.loadOrNull()
            Assume.assumeNotNull(config)
            client = createTeamCityTestHttpClient()
        }

        @AfterClass
        @JvmStatic
        fun tearDown() {
            client?.close()
        }
    }

    /** Tracks build IDs triggered during a test for cleanup. */
    private var triggeredBuildId: String? = null

    @After
    fun cleanupTriggeredBuild() {
        val buildId = triggeredBuildId ?: return
        triggeredBuildId = null
        runBlocking {
            client?.cancelBuild(config ?: error("TeamCityTestConfig not loaded"), buildId)
        }
    }

    private fun block() = Block.ActionBlock(
        id = BlockId("tc-integ"),
        name = "Integration TC Build",
        type = BlockType.TEAMCITY_BUILD,
        connectionId = ConnectionId("conn-tc"),
    )

    private fun context(): ExecutionContext {
        val cfg = config ?: error("TeamCityTestConfig not loaded — setUp should have skipped this test")
        return ExecutionContext(
            releaseId = ReleaseId("integ-release-tc"),
            parameters = emptyList(),
            blockOutputs = emptyMap(),
            connections = mapOf(
                ConnectionId("conn-tc") to ConnectionConfig.TeamCityConfig(
                    serverUrl = cfg.serverUrl,
                    token = cfg.token,
                )
            ),
        )
    }

    @Test
    fun `execute triggers build and returns outputs on webhook completion`() = runBlocking {
        val cfg = config ?: error("TeamCityTestConfig not loaded — setUp should have skipped this test")
        val webhookRepo = InMemoryPendingWebhookRepository()
        val connectionsRepo = FakeConnectionsRepository()
        val webhookService = WebhookService(webhookRepo, connectionsRepo)
        val executor = TeamCityBuildExecutor(client ?: error("HttpClient not initialized"), webhookRepo, webhookService)

        val outputsDeferred = async {
            executor.execute(
                block = block(),
                parameters = listOf(Parameter(key = "buildTypeId", value = cfg.buildTypeId)),
                context = context(),
            )
        }

        // Wait for the webhook to be registered (real HTTP call needs time)
        waitUntil(maxAttempts = 60, delayMillis = 500) {
            webhookRepo.findByConnectionIdAndType(ConnectionId("conn-tc"), WebhookType.TEAMCITY).isNotEmpty()
        }

        val pendingWebhooks = webhookRepo.findByConnectionIdAndType(ConnectionId("conn-tc"), WebhookType.TEAMCITY)
        assertTrue(pendingWebhooks.isNotEmpty(), "Should have registered a pending webhook")
        assertTrue(pendingWebhooks[0].externalId.isNotEmpty(), "Should have a build ID as externalId")

        val buildId = pendingWebhooks[0].externalId
        triggeredBuildId = buildId

        // Wait for the real build to finish so we can get actual outputs
        val buildResult = (client ?: error("HttpClient not initialized")).waitForBuildCompletion(cfg, buildId)
        val buildNumber = buildResult["number"]?.jsonPrimitive?.content ?: buildId
        val buildStatus = buildResult["status"]?.jsonPrimitive?.content ?: "UNKNOWN"

        // Simulate webhook arrival with real build data
        webhookService.emitCompletion(
            WebhookCompletion(
                webhookId = pendingWebhooks[0].id,
                blockId = block().id,
                releaseId = ReleaseId("integ-release-tc"),
                type = WebhookType.TEAMCITY,
                payload = """{"buildNumber":"$buildNumber","buildStatus":"$buildStatus"}""",
            )
        )

        val outputs = outputsDeferred.await()
        assertEquals(buildNumber, outputs["buildNumber"])
        val buildUrl = outputs["buildUrl"] ?: error("Expected 'buildUrl' in executor outputs")
        assertTrue(buildUrl.contains(cfg.serverUrl), "buildUrl should contain server URL")
        assertEquals(buildStatus, outputs["buildStatus"])
    }

    @Test
    fun `execute with invalid build type throws`() = runBlocking {
        val webhookRepo = InMemoryPendingWebhookRepository()
        val connectionsRepo = FakeConnectionsRepository()
        val webhookService = WebhookService(webhookRepo, connectionsRepo)
        val executor = TeamCityBuildExecutor(client ?: error("HttpClient not initialized"), webhookRepo, webhookService)

        try {
            executor.execute(
                block = block(),
                parameters = listOf(
                    Parameter(key = "buildTypeId", value = "NonExistent_BuildType_${System.currentTimeMillis()}")
                ),
                context = context(),
            )
            fail("Should have thrown RuntimeException")
        } catch (e: RuntimeException) {
            val msg = e.message ?: error("RuntimeException should have a message")
            assertTrue(
                msg.contains("TeamCity build trigger failed"),
                "Message should indicate trigger failure: $msg"
            )
        }
    }
}
