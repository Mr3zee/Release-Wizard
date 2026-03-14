@file:Suppress("FunctionName")

package com.github.mr3zee.github

import com.github.mr3zee.execution.ExecutionContext
import com.github.mr3zee.execution.FakeConnectionsRepository
import com.github.mr3zee.execution.InMemoryPendingWebhookRepository
import com.github.mr3zee.execution.executors.GitHubActionExecutor
import com.github.mr3zee.model.*
import com.github.mr3zee.webhooks.WebhookCompletion
import com.github.mr3zee.webhooks.WebhookService
import com.github.mr3zee.webhooks.WebhookType
import com.github.mr3zee.waitUntil
import io.ktor.client.*
import kotlinx.coroutines.async
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
    )

    private fun context(): ExecutionContext {
        val cfg = config!!
        return ExecutionContext(
            releaseId = ReleaseId("integ-release-1"),
            parameters = emptyList(),
            blockOutputs = emptyMap(),
            connections = mapOf(
                ConnectionId("conn-1") to ConnectionConfig.GitHubConfig(
                    token = cfg.token,
                    owner = cfg.owner,
                    repo = cfg.repo,
                    webhookSecret = cfg.webhookSecret,
                )
            ),
        )
    }

    @Test
    fun `execute triggers dispatch and discovers run ID`() = runBlocking {
        val cfg = config!!
        val webhookRepo = InMemoryPendingWebhookRepository()
        val connectionsRepo = FakeConnectionsRepository()
        val webhookService = WebhookService(webhookRepo, connectionsRepo)
        val executor = GitHubActionExecutor(client!!, webhookRepo, webhookService)

        val outputsDeferred = async {
            executor.execute(
                block = block(),
                parameters = listOf(
                    Parameter(key = "workflowFile", value = cfg.workflowFile),
                    Parameter(key = "ref", value = cfg.ref),
                ),
                context = context(),
            )
        }

        // Wait for the webhook to be registered (real HTTP calls need time)
        waitUntil(maxAttempts = 60, delayMillis = 500) {
            webhookRepo.findByConnectionIdAndType(ConnectionId("conn-1"), WebhookType.GITHUB).isNotEmpty()
        }

        val pendingWebhooks = webhookRepo.findByConnectionIdAndType(ConnectionId("conn-1"), WebhookType.GITHUB)
        assertTrue(pendingWebhooks.isNotEmpty(), "Should have registered a pending webhook")
        assertTrue(pendingWebhooks[0].externalId.isNotEmpty(), "Should have discovered a run ID as externalId")

        // Emit completion to unblock the executor
        webhookService.emitCompletion(
            WebhookCompletion(
                webhookId = pendingWebhooks[0].id,
                blockId = block().id,
                releaseId = ReleaseId("integ-release-1"),
                type = WebhookType.GITHUB,
                payload = """{"workflow_run":{"id":${pendingWebhooks[0].externalId},"html_url":"https://github.com/${cfg.owner}/${cfg.repo}/actions/runs/${pendingWebhooks[0].externalId}","conclusion":"success"}}""",
            )
        )

        val outputs = outputsDeferred.await()
        assertTrue(outputs["runId"]!!.isNotEmpty(), "runId should not be empty")
        assertTrue(outputs["runUrl"]!!.isNotEmpty(), "runUrl should not be empty")
    }

    @Test
    fun `dispatch with invalid workflow file throws`() = runBlocking {
        val cfg = config!!
        val webhookRepo = InMemoryPendingWebhookRepository()
        val connectionsRepo = FakeConnectionsRepository()
        val webhookService = WebhookService(webhookRepo, connectionsRepo)
        val executor = GitHubActionExecutor(client!!, webhookRepo, webhookService)

        try {
            executor.execute(
                block = block(),
                parameters = listOf(
                    Parameter(key = "workflowFile", value = "nonexistent-workflow-${System.currentTimeMillis()}.yml"),
                    Parameter(key = "ref", value = cfg.ref),
                ),
                context = context(),
            )
            fail("Should have thrown RuntimeException")
        } catch (e: RuntimeException) {
            assertTrue(
                e.message!!.contains("GitHub workflow dispatch failed"),
                "Message should indicate dispatch failure: ${e.message}"
            )
        }
    }
}
