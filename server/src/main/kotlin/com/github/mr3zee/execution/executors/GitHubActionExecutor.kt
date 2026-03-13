package com.github.mr3zee.execution.executors

import com.github.mr3zee.execution.BlockExecutor
import com.github.mr3zee.execution.ExecutionContext
import com.github.mr3zee.model.Block
import com.github.mr3zee.model.ConnectionConfig
import com.github.mr3zee.model.Parameter
import com.github.mr3zee.webhooks.*
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

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

    override suspend fun execute(
        block: Block.ActionBlock,
        parameters: List<Parameter>,
        context: ExecutionContext,
    ): Map<String, String> {
        val connectionId = block.connectionId
            ?: throw IllegalStateException("GitHub Action block requires a connection")
        val config = context.connections[connectionId] as? ConnectionConfig.GitHubConfig
            ?: throw IllegalStateException("GitHub connection config not found for $connectionId")

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
            throw RuntimeException("GitHub workflow dispatch failed: ${dispatchResponse.status} - ${dispatchResponse.bodyAsText()}")
        }

        // Discover the run ID by polling the runs list
        val runId = discoverRunId(baseUrl, workflowFile, config.token)
            ?: throw RuntimeException("Could not discover GitHub Actions run ID after dispatch")

        // Register pending webhook
        val webhook = webhookRepository.create(
            externalId = runId,
            blockId = block.id,
            releaseId = context.releaseId,
            connectionId = connectionId,
            type = WebhookType.GITHUB,
        )

        // Wait for webhook completion
        val completion = withTimeout(WEBHOOK_TIMEOUT_MS) {
            webhookService.completions.first { it.webhookId == webhook.id }
        }

        // Parse payload
        return try {
            val payload = kotlinx.serialization.json.Json.decodeFromString<JsonObject>(completion.payload)
            val workflowRun = payload["workflow_run"]?.jsonObject
            mapOf(
                "runId" to (workflowRun?.get("id")?.jsonPrimitive?.content ?: runId),
                "runUrl" to (workflowRun?.get("html_url")?.jsonPrimitive?.content
                    ?: "https://github.com/${config.owner}/${config.repo}/actions/runs/$runId"),
                "runStatus" to (workflowRun?.get("conclusion")?.jsonPrimitive?.content ?: "success"),
            )
        } catch (_: Exception) {
            mapOf(
                "runId" to runId,
                "runUrl" to "https://github.com/${config.owner}/${config.repo}/actions/runs/$runId",
                "runStatus" to "success",
            )
        }
    }

    /**
     * Poll the workflow runs list to discover the run ID triggered by our dispatch.
     * GitHub's dispatch API returns 204 with no body, so we need to find the run.
     */
    private suspend fun discoverRunId(baseUrl: String, workflowFile: String, token: String): String? {
        repeat(RUN_DISCOVERY_RETRIES) { attempt ->
            if (attempt > 0) delay(RUN_DISCOVERY_DELAY_MS)

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
            } catch (_: Exception) {
                // Retry
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
