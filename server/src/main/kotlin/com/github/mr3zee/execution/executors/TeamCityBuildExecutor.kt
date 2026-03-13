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
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.slf4j.LoggerFactory

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
) : BlockExecutor {

    private val log = LoggerFactory.getLogger(TeamCityBuildExecutor::class.java)

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
            throw RuntimeException("TeamCity build trigger failed: ${triggerResponse.status} - ${triggerResponse.bodyAsText()}")
        }

        val responseBody = triggerResponse.body<JsonObject>()
        val buildId = responseBody["id"]?.jsonPrimitive?.content
            ?: throw RuntimeException("TeamCity response missing build id")

        // Subscribe to completions BEFORE registering webhook to prevent race condition.
        // Use UNDISPATCHED start to ensure the collector begins immediately on the current
        // thread, guaranteeing subscription is active before we register the webhook.
        return coroutineScope {
            val completionDeferred = async(start = CoroutineStart.UNDISPATCHED) {
                withTimeout(WEBHOOK_TIMEOUT_MS) {
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
            parseCompletionPayload(completion.payload, buildId, config.serverUrl)
        }
    }

    private fun parseCompletionPayload(payload: String, buildId: String, serverUrl: String): Map<String, String> {
        return try {
            val json = kotlinx.serialization.json.Json.decodeFromString<JsonObject>(payload)
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
