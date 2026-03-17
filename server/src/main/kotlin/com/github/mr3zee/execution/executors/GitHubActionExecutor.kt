package com.github.mr3zee.execution.executors

import com.github.mr3zee.execution.BlockExecutor
import com.github.mr3zee.execution.ExecutionContext
import com.github.mr3zee.execution.ExecutionScope
import com.github.mr3zee.model.Block
import com.github.mr3zee.model.ConnectionConfig
import com.github.mr3zee.model.ConnectionId
import com.github.mr3zee.model.Parameter
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.slf4j.LoggerFactory
import kotlin.time.Duration.Companion.milliseconds

/**
 * Triggers a GitHub Actions workflow and polls for completion.
 *
 * Params: workflowFile (required), ref (required)
 * Outputs: runId, runUrl, runStatus
 */
class GitHubActionExecutor(
    private val httpClient: HttpClient,
    private val buildPollingService: BuildPollingService,
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

    override suspend fun resume(
        block: Block.ActionBlock,
        parameters: List<Parameter>,
        context: ExecutionContext,
        scope: ExecutionScope?,
    ): Map<String, String> {
        val (_, config) = resolveGitHubConfig(block, context)

        // Check for persisted run ID from before crash
        val persistedRunId = context.blockOutputs[block.id]?.get(INTERNAL_RUN_ID_KEY)

        return if (persistedRunId != null) {
            // Resume polling from persisted run ID
            buildPollingService.pollGitHubRun(
                owner = config.owner,
                repo = config.repo,
                token = config.token,
                runId = persistedRunId,
                intervalSeconds = config.pollingIntervalSeconds,
            )
        } else {
            // No persisted run ID — re-trigger
            execute(block, parameters, context, scope)
        }
    }

    override suspend fun execute(
        block: Block.ActionBlock,
        parameters: List<Parameter>,
        context: ExecutionContext,
        scope: ExecutionScope?,
    ): Map<String, String> {
        val (_, config) = resolveGitHubConfig(block, context)

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

        // Persist run ID before polling so recovery can resume
        scope?.persistOutputs(mapOf(INTERNAL_RUN_ID_KEY to runId))

        return buildPollingService.pollGitHubRun(
            owner = config.owner,
            repo = config.repo,
            token = config.token,
            runId = runId,
            intervalSeconds = config.pollingIntervalSeconds,
        )
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
        const val INTERNAL_RUN_ID_KEY = "_ghRunId"
        const val RUN_DISCOVERY_RETRIES = 3
        const val RUN_DISCOVERY_DELAY_MS = 1000L
    }
}
