package com.github.mr3zee.execution.executors

import com.github.mr3zee.execution.BlockExecutor
import com.github.mr3zee.execution.ExecutionContext
import com.github.mr3zee.model.Block
import com.github.mr3zee.model.ConnectionConfig
import com.github.mr3zee.model.ConnectionId
import com.github.mr3zee.model.Parameter
import com.github.mr3zee.webhooks.*
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.slf4j.LoggerFactory
import kotlin.time.Duration.Companion.milliseconds

/**
 * Triggers a GitHub Actions workflow and waits for webhook completion.
 *
 * Params: workflowFile (required), ref (required)
 * Outputs: runId, runUrl, runStatus
 */
class GitHubActionExecutor(
    private val httpClient: HttpClient,
    private val webhookRepository: PendingWebhookRepository,
    private val webhookService: WebhookService,
) : BlockExecutor {

    private val log = LoggerFactory.getLogger(GitHubActionExecutor::class.java)

    private fun resolveGitHubConfig(
        block: Block.ActionBlock,
        context: ExecutionContext,
    ): Pair<ConnectionId, ConnectionConfig.GitHubConfig> {
        val connectionId = block.connectionId
            ?: throw IllegalStateException("GitHub Action block requires a connection")
        val config = context.connections[connectionId] as? ConnectionConfig.GitHubConfig
            ?: throw IllegalStateException("GitHub connection config not found for $connectionId")
        return connectionId to config
    }

    private suspend fun CoroutineScope.awaitWebhookCompletion(
        block: Block.ActionBlock,
        context: ExecutionContext,
    ): WebhookCompletion {
        val completionDeferred = async(start = CoroutineStart.UNDISPATCHED) {
            withTimeoutOrNull(WEBHOOK_TIMEOUT_MS.milliseconds) {
                webhookService.completions.first { it.blockId == block.id && it.releaseId == context.releaseId }
            }
        }
        return completionDeferred.await()
            ?: throw RuntimeException("GitHub Actions webhook timed out after ${WEBHOOK_TIMEOUT_MS / 60_000}min")
    }

    override suspend fun resume(
        block: Block.ActionBlock,
        parameters: List<Parameter>,
        context: ExecutionContext,
    ): Map<String, String> {
        val (_, config) = resolveGitHubConfig(block, context)

        // Check PendingWebhook state
        val webhook = webhookRepository.findByReleaseIdAndBlockId(context.releaseId, block.id)

        return when {
            webhook != null && webhook.status == WebhookStatus.COMPLETED && webhook.payload != null -> {
                // Webhook already arrived — parse and return outputs
                parseCompletionPayload(webhook.payload, webhook.externalId, config.owner, config.repo)
            }
            webhook != null && webhook.status == WebhookStatus.PENDING && webhook.externalId.isNotEmpty() -> {
                // Webhook registered with run ID — subscribe and wait
                coroutineScope {
                    val completion = awaitWebhookCompletion(block, context)
                    parseCompletionPayload(completion.payload, webhook.externalId, config.owner, config.repo)
                }
            }
            webhook != null && webhook.status == WebhookStatus.PENDING -> {
                // PENDING without externalId — re-poll for run ID, then wait
                val baseUrl = "https://api.github.com/repos/${config.owner}/${config.repo}"
                val workflowFile = parameters.find { it.key == "workflowFile" }?.value ?: ""
                val runId = discoverRunId(baseUrl, workflowFile, config.token)
                if (runId != null) {
                    webhookRepository.updateExternalId(webhook.id, runId)
                }
                coroutineScope {
                    val completion = awaitWebhookCompletion(block, context)
                    parseCompletionPayload(completion.payload, runId ?: "", config.owner, config.repo)
                }
            }
            else -> {
                // No webhook record — trigger never happened, re-execute
                execute(block, parameters, context)
            }
        }
    }

    override suspend fun execute(
        block: Block.ActionBlock,
        parameters: List<Parameter>,
        context: ExecutionContext,
    ): Map<String, String> {
        val (connectionId, config) = resolveGitHubConfig(block, context)

        val workflowFile = parameters.find { it.key == "workflowFile" }?.value
            ?: throw IllegalArgumentException("GitHub Action requires 'workflowFile' parameter")
        val ref = parameters.find { it.key == "ref" }?.value
            ?: throw IllegalArgumentException("GitHub Action requires 'ref' parameter")

        val baseUrl = "https://api.github.com/repos/${config.owner}/${config.repo}"

        // Trigger workflow dispatch (returns 204 No Content)
        val dispatchResponse = httpClient.post("$baseUrl/actions/workflows/$workflowFile/dispatches") {
            header("Authorization", "Bearer ${config.token}")
            header("Accept", "application/vnd.github+json")
            contentType(ContentType.Application.Json)
            setBody(DispatchRequest(ref = ref))
        }

        if (dispatchResponse.status != HttpStatusCode.NoContent && !dispatchResponse.status.isSuccess()) {
            val errorBody = dispatchResponse.bodyAsText()
            log.warn("GitHub workflow dispatch failed: {} - {}", dispatchResponse.status, errorBody)
            throw RuntimeException("GitHub workflow dispatch failed (HTTP ${dispatchResponse.status.value})")
        }

        // Discover the run ID by polling the runs list
        val runId = discoverRunId(baseUrl, workflowFile, config.token)
            ?: throw RuntimeException("Could not discover GitHub Actions run ID after dispatch")

        // Subscribe to completions BEFORE registering webhook to prevent race condition.
        // Use UNDISPATCHED start to ensure the collector begins immediately.
        return coroutineScope {
            val completionDeferred = async(start = CoroutineStart.UNDISPATCHED) {
                withTimeoutOrNull(WEBHOOK_TIMEOUT_MS.milliseconds) {
                    webhookService.completions.first { it.blockId == block.id && it.releaseId == context.releaseId }
                }
            }

            webhookRepository.create(
                externalId = runId,
                blockId = block.id,
                releaseId = context.releaseId,
                connectionId = connectionId,
                type = WebhookType.GITHUB,
            )

            val completion = completionDeferred.await()
                ?: throw RuntimeException("GitHub Actions webhook timed out after ${WEBHOOK_TIMEOUT_MS / 60_000}min")
            parseCompletionPayload(completion.payload, runId, config.owner, config.repo)
        }
    }

    private fun parseCompletionPayload(
        payload: String,
        runId: String,
        owner: String,
        repo: String,
    ): Map<String, String> {
        return try {
            val json = Json.decodeFromString<JsonObject>(payload)
            val workflowRun = json["workflow_run"]?.jsonObject
            mapOf(
                "runId" to (workflowRun?.get("id")?.jsonPrimitive?.content ?: runId),
                "runUrl" to (workflowRun?.get("html_url")?.jsonPrimitive?.content
                    ?: "https://github.com/$owner/$repo/actions/runs/$runId"),
                "runStatus" to (workflowRun?.get("conclusion")?.jsonPrimitive?.content ?: "unknown"),
            )
        } catch (e: Exception) {
            log.warn("Failed to parse GitHub Actions webhook payload for run $runId", e)
            mapOf(
                "runId" to runId,
                "runUrl" to "https://github.com/$owner/$repo/actions/runs/$runId",
                "runStatus" to "unknown",
            )
        }
    }

    /**
     * Poll the workflow runs list to discover the run ID triggered by our dispatch.
     * GitHub's dispatch API returns 204 with no body, so we need to find the run.
     */
    private suspend fun discoverRunId(baseUrl: String, workflowFile: String, token: String): String? {
        repeat(RUN_DISCOVERY_RETRIES) { attempt ->
            if (attempt > 0) {
                delay(RUN_DISCOVERY_DELAY_MS.milliseconds)
            }

            try {
                val response = httpClient.get("$baseUrl/actions/workflows/$workflowFile/runs?per_page=1") {
                    header("Authorization", "Bearer $token")
                    header("Accept", "application/vnd.github+json")
                }

                if (response.status.isSuccess()) {
                    val body = response.body<JsonObject>()
                    val runs = body["workflow_runs"] as? JsonArray
                    if (!runs.isNullOrEmpty()) {
                        return runs[0].jsonObject["id"]?.jsonPrimitive?.content
                    }
                }
            } catch (e: Exception) {
                log.debug("Run discovery attempt $attempt failed", e)
            }
        }
        return null
    }

    @Serializable
    private data class DispatchRequest(
        val ref: String,
    )

    companion object {
        const val WEBHOOK_TIMEOUT_MS = 60 * 60 * 1000L // 60 minutes
        const val RUN_DISCOVERY_RETRIES = 3
        const val RUN_DISCOVERY_DELAY_MS = 1000L
    }
}
