@file:Suppress("FunctionName")

package com.github.mr3zee.github

import com.github.mr3zee.execution.ExecutionContext
import com.github.mr3zee.execution.executors.BuildPollingService
import com.github.mr3zee.execution.executors.GitHubActionExecutor
import com.github.mr3zee.model.*
import io.ktor.client.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.fail
import kotlin.test.assertTrue

class GitHubActionIntegrationTest {

    companion object {
        private var config: GitHubTestConfig? = null
        private var client: HttpClient? = null

        @BeforeClass
        @JvmStatic
        fun setUp() {
            config = GitHubTestConfig.loadOrNull()
            Assume.assumeNotNull(config)
            client = createGitHubTestHttpClient()
        }

        @AfterClass
        @JvmStatic
        fun tearDown() {
            client?.close()
        }
    }

    private fun block() = Block.ActionBlock(
        id = BlockId("gh-action-integ"),
        name = "Integration Action",
        type = BlockType.GITHUB_ACTION,
        connectionId = ConnectionId("conn-1"),
        timeoutSeconds = 300, // 5 min timeout for integration tests
    )

    private fun context(): ExecutionContext {
        val cfg = config ?: error("GitHubTestConfig not loaded — setUp should have skipped this test")
        return ExecutionContext(
            releaseId = ReleaseId("integ-release-1"),
            parameters = emptyList(),
            blockOutputs = emptyMap(),
            connections = mapOf(
                ConnectionId("conn-1") to ConnectionConfig.GitHubConfig(
                    token = cfg.token,
                    owner = cfg.owner,
                    repo = cfg.repo,
                    pollingIntervalSeconds = 5,
                )
            ),
        )
    }

    @Test
    fun `execute triggers dispatch and polls to completion`() = runBlocking {
        val cfg = config ?: error("GitHubTestConfig not loaded — setUp should have skipped this test")
        val httpClient = client ?: error("HttpClient not initialized")
        val pollingService = BuildPollingService(httpClient)
        val executor = GitHubActionExecutor(httpClient, pollingService)

        val outputs = executor.execute(
            block = block(),
            parameters = listOf(
                Parameter(key = "workflowFile", value = cfg.workflowFile),
                Parameter(key = "ref", value = cfg.ref),
            ),
            context = context(),
        )

        val runId = outputs["runId"] ?: error("Expected 'runId' in executor outputs")
        assertTrue(runId.isNotEmpty(), "runId should not be empty")
        val runUrl = outputs["runUrl"] ?: error("Expected 'runUrl' in executor outputs")
        assertTrue(runUrl.isNotEmpty(), "runUrl should not be empty")
        val runStatus = outputs["runStatus"] ?: error("Expected 'runStatus' in executor outputs")
        assertTrue(runStatus.isNotEmpty(), "runStatus should not be empty")
    }

    @Test
    fun `dispatch with invalid workflow file throws`() = runBlocking {
        val httpClient = client ?: error("HttpClient not initialized")
        val pollingService = BuildPollingService(httpClient)
        val executor = GitHubActionExecutor(httpClient, pollingService)

        try {
            executor.execute(
                block = block(),
                parameters = listOf(
                    Parameter(key = "workflowFile", value = "nonexistent-workflow-${System.currentTimeMillis()}.yml"),
                    Parameter(key = "ref", value = config?.ref ?: "main"),
                ),
                context = context(),
            )
            fail("Should have thrown RuntimeException")
        } catch (e: RuntimeException) {
            val msg = e.message ?: error("RuntimeException should have a message")
            assertTrue(
                msg.contains("GitHub workflow dispatch failed"),
                "Message should indicate dispatch failure: $msg"
            )
        }
    }
}
