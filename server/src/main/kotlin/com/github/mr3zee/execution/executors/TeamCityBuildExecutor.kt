package com.github.mr3zee.execution.executors

import com.github.mr3zee.execution.BlockExecutor
import com.github.mr3zee.execution.ExecutionContext
import com.github.mr3zee.model.Block
import com.github.mr3zee.model.BlockExecution
import com.github.mr3zee.model.ConnectionConfig
import com.github.mr3zee.model.Parameter
import com.github.mr3zee.webhooks.*
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.slf4j.LoggerFactory
import kotlin.time.Duration.Companion.milliseconds

/**
 * Triggers a TeamCity build and waits for webhook completion.
 *
 * Params: buildTypeId (required), branch (optional)
 * Outputs: buildNumber, buildUrl, buildStatus
 */
class TeamCityBuildExecutor(
    private val httpClient: HttpClient,
    private val webhookRepository: PendingWebhookRepository,
    private val webhookService: WebhookService,
    private val artifactService: TeamCityArtifactService? = null,
) : BlockExecutor {

    private val log = LoggerFactory.getLogger(TeamCityBuildExecutor::class.java)

    override suspend fun resume(
        block: Block.ActionBlock,
        parameters: List<Parameter>,
        context: ExecutionContext,
    ): Map<String, String> {
        val connectionId = block.connectionId
            ?: throw IllegalStateException("TeamCity Build block requires a connection")
        val config = context.connections[connectionId] as? ConnectionConfig.TeamCityConfig
            ?: throw IllegalStateException("TeamCity connection config not found for $connectionId")

        // Check PendingWebhook state
        val webhook = webhookRepository.findByReleaseIdAndBlockId(context.releaseId, block.id)

        return when {
            webhook != null && webhook.status == WebhookStatus.COMPLETED && webhook.payload != null -> {
                // Webhook already arrived — parse and return outputs
                val baseOutputs = parseCompletionPayload(webhook.payload, webhook.externalId, config.serverUrl)
                baseOutputs + fetchArtifactOutputs(parameters, config, webhook.externalId)
            }
            webhook != null && webhook.status == WebhookStatus.PENDING -> {
                // Webhook registered but not yet received — subscribe and wait
                coroutineScope {
                    val completionDeferred = async(start = CoroutineStart.UNDISPATCHED) {
                        withTimeoutOrNull(WEBHOOK_TIMEOUT_MS.milliseconds) {
                            webhookService.completions.first { it.blockId == block.id && it.releaseId == context.releaseId }
                        }
                    }
                    val completion = completionDeferred.await()
                        ?: throw RuntimeException("TeamCity webhook timed out after ${WEBHOOK_TIMEOUT_MS / 60_000}min")
                    val baseOutputs = parseCompletionPayload(completion.payload, webhook.externalId, config.serverUrl)
                    baseOutputs + fetchArtifactOutputs(parameters, config, webhook.externalId)
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
        val connectionId = block.connectionId
            ?: throw IllegalStateException("TeamCity Build block requires a connection")
        val config = context.connections[connectionId] as? ConnectionConfig.TeamCityConfig
            ?: throw IllegalStateException("TeamCity connection config not found for $connectionId")

        val buildTypeId = parameters.find { it.key == "buildTypeId" }?.value
            ?: throw IllegalArgumentException("TeamCity Build requires 'buildTypeId' parameter")
        val branch = parameters.find { it.key == "branch" }?.value

        // Trigger build with XML-escaped parameters
        val xmlBody = buildString {
            append("""<build>""")
            append("""<buildType id="${escapeXml(buildTypeId)}"/>""")
            if (branch != null) {
                append("""<branchName>${escapeXml(branch)}</branchName>""")
            }
            append("""</build>""")
        }

        val triggerResponse = httpClient.post("${config.serverUrl}/app/rest/buildQueue") {
            header("Authorization", "Bearer ${config.token}")
            header("Accept", "application/json")
            contentType(ContentType.Application.Xml)
            setBody(xmlBody)
        }

        if (!triggerResponse.status.isSuccess()) {
            val errorBody = triggerResponse.bodyAsText()
            log.warn("TeamCity build trigger failed: {} - {}", triggerResponse.status, errorBody)
            throw RuntimeException("TeamCity build trigger failed (HTTP ${triggerResponse.status.value})")
        }

        val responseBody = triggerResponse.body<JsonObject>()
        val buildId = responseBody["id"]?.jsonPrimitive?.content
            ?: throw RuntimeException("TeamCity response missing build id")

        // Subscribe to completions BEFORE registering webhook to prevent race condition.
        // Use UNDISPATCHED start to ensure the collector begins immediately on the current
        // thread, guaranteeing subscription is active before we register the webhook.
        return coroutineScope {
            val completionDeferred = async(start = CoroutineStart.UNDISPATCHED) {
                withTimeoutOrNull(WEBHOOK_TIMEOUT_MS.milliseconds) {
                    webhookService.completions.first { it.blockId == block.id && it.releaseId == context.releaseId }
                }
            }

            webhookRepository.create(
                externalId = buildId,
                blockId = block.id,
                releaseId = context.releaseId,
                connectionId = connectionId,
                type = WebhookType.TEAMCITY,
            )

            val completion = completionDeferred.await()
                ?: throw RuntimeException("TeamCity webhook timed out after ${WEBHOOK_TIMEOUT_MS / 60_000}min")
            val baseOutputs = parseCompletionPayload(completion.payload, buildId, config.serverUrl)
            baseOutputs + fetchArtifactOutputs(parameters, config, buildId)
        }
    }

    private suspend fun fetchArtifactOutputs(
        parameters: List<Parameter>,
        config: ConnectionConfig.TeamCityConfig,
        buildId: String,
    ): Map<String, String> {
        if (artifactService == null) return emptyMap()
        val artifactsGlob = parameters.find { it.key == "artifactsGlob" }?.value
        if (artifactsGlob.isNullOrBlank()) return emptyMap()

        val maxDepth = (parameters.find { it.key == "artifactsMaxDepth" }?.value?.toIntOrNull() ?: 10).coerceIn(1, 20)
        val maxFiles = (parameters.find { it.key == "artifactsMaxFiles" }?.value?.toIntOrNull() ?: 1000).coerceIn(1, 5000)

        return try {
            val artifacts = artifactService.fetchMatchingArtifacts(
                config.serverUrl, config.token, buildId, artifactsGlob, maxDepth, maxFiles,
            )
            if (artifacts.isNotEmpty()) {
                mapOf(BlockExecution.ARTIFACTS_OUTPUT_KEY to Json.encodeToString(artifacts))
            } else {
                emptyMap()
            }
        } catch (e: Exception) {
            log.warn("Failed to fetch artifacts for build $buildId", e)
            emptyMap()
        }
    }

    private fun parseCompletionPayload(payload: String, buildId: String, serverUrl: String): Map<String, String> {
        return try {
            val json = Json.decodeFromString<JsonObject>(payload)
            mapOf(
                "buildNumber" to (json["buildNumber"]?.jsonPrimitive?.content ?: buildId),
                "buildUrl" to "$serverUrl/viewLog.html?buildId=$buildId",
                "buildStatus" to (json["buildStatus"]?.jsonPrimitive?.content ?: "UNKNOWN"),
            )
        } catch (e: Exception) {
            log.warn("Failed to parse TeamCity webhook payload for build $buildId", e)
            mapOf(
                "buildNumber" to buildId,
                "buildUrl" to "$serverUrl/viewLog.html?buildId=$buildId",
                "buildStatus" to "UNKNOWN",
            )
        }
    }

    companion object {
        const val WEBHOOK_TIMEOUT_MS = 30 * 60 * 1000L // 30 minutes

        fun escapeXml(value: String): String = value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }
}
