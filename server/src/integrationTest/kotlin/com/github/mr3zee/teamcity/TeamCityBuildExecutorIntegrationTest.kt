@file:Suppress("FunctionName")

package com.github.mr3zee.teamcity

import com.github.mr3zee.execution.ExecutionContext
import com.github.mr3zee.execution.executors.BuildPollingService
import com.github.mr3zee.execution.executors.TeamCityBuildExecutor
import com.github.mr3zee.model.*
import io.ktor.client.*
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
        timeoutSeconds = 300, // 5 min timeout for integration tests
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
                    pollingIntervalSeconds = 5,
                )
            ),
        )
    }

    @Test
    fun `execute triggers build and polls to completion`() = runBlocking {
        val cfg = config ?: error("TeamCityTestConfig not loaded — setUp should have skipped this test")
        val httpClient = client ?: error("HttpClient not initialized")
        val pollingService = BuildPollingService(httpClient)
        val executor = TeamCityBuildExecutor(httpClient, pollingService)

        val outputs = executor.execute(
            block = block(),
            parameters = listOf(Parameter(key = "buildTypeId", value = cfg.buildTypeId)),
            context = context(),
        )

        val buildNumber = outputs["buildNumber"] ?: error("Expected 'buildNumber' in executor outputs")
        assertTrue(buildNumber.isNotEmpty(), "buildNumber should not be empty")
        val buildUrl = outputs["buildUrl"] ?: error("Expected 'buildUrl' in executor outputs")
        assertTrue(buildUrl.contains(cfg.serverUrl), "buildUrl should contain server URL")
        val buildStatus = outputs["buildStatus"] ?: error("Expected 'buildStatus' in executor outputs")
        assertTrue(buildStatus.isNotEmpty(), "buildStatus should not be empty")
    }

    @Test
    fun `execute with invalid build type throws`() = runBlocking {
        val httpClient = client ?: error("HttpClient not initialized")
        val pollingService = BuildPollingService(httpClient)
        val executor = TeamCityBuildExecutor(httpClient, pollingService)

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
