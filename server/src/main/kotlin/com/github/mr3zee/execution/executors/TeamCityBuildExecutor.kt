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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

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

        // Trigger build
        val xmlBody = buildString {
            append("""<build>""")
            append("""<buildType id="$buildTypeId"/>""")
            if (branch != null) {
                append("""<branchName>$branch</branchName>""")
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

        // Register pending webhook
        val webhook = webhookRepository.create(
            externalId = buildId,
            blockId = block.id,
            releaseId = context.releaseId,
            connectionId = connectionId,
            type = WebhookType.TEAMCITY,
        )

        // Wait for webhook completion (or timeout)
        val completion = withTimeout(WEBHOOK_TIMEOUT_MS) {
            webhookService.completions.first { it.webhookId == webhook.id }
        }

        // Parse payload for build results
        return try {
            val payload = kotlinx.serialization.json.Json.decodeFromString<JsonObject>(completion.payload)
            mapOf(
                "buildNumber" to (payload["buildNumber"]?.jsonPrimitive?.content ?: buildId),
                "buildUrl" to "${config.serverUrl}/viewLog.html?buildId=$buildId",
                "buildStatus" to (payload["buildStatus"]?.jsonPrimitive?.content ?: "SUCCESS"),
            )
        } catch (_: Exception) {
            mapOf(
                "buildNumber" to buildId,
                "buildUrl" to "${config.serverUrl}/viewLog.html?buildId=$buildId",
                "buildStatus" to "SUCCESS",
            )
        }
    }

    companion object {
        const val WEBHOOK_TIMEOUT_MS = 30 * 60 * 1000L // 30 minutes
    }
}
