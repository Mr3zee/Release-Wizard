package com.github.mr3zee.execution.executors

import com.github.mr3zee.AppJson
import com.github.mr3zee.execution.BlockExecutor
import com.github.mr3zee.execution.ExecutionContext
import com.github.mr3zee.execution.ExecutionScope
import com.github.mr3zee.model.Block
import com.github.mr3zee.model.BlockExecution
import com.github.mr3zee.model.ConnectionConfig
import com.github.mr3zee.model.Parameter
import com.github.mr3zee.webhooks.StatusWebhookService
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.slf4j.LoggerFactory

/**
 * Triggers a TeamCity build and polls for completion.
 *
 * Params: buildTypeId (required), branch (optional)
 * Outputs: buildNumber, buildUrl, buildStatus
 */
class TeamCityBuildExecutor(
    private val httpClient: HttpClient,
    private val buildPollingService: BuildPollingService,
    private val artifactService: TeamCityArtifactService? = null,
    private val statusWebhookService: StatusWebhookService? = null,
) : BlockExecutor {

    private val log = LoggerFactory.getLogger(TeamCityBuildExecutor::class.java)

    override suspend fun resume(
        block: Block.ActionBlock,
        parameters: List<Parameter>,
        context: ExecutionContext,
        scope: ExecutionScope?,
    ): Map<String, String> {
        val connectionId = block.connectionId
            ?: throw IllegalStateException("TeamCity Build block requires a connection")
        val config = context.connections[connectionId] as? ConnectionConfig.TeamCityConfig
            ?: throw IllegalStateException("TeamCity connection config not found for $connectionId")

        // Check for persisted build ID from before crash
        val persistedBuildId = context.blockOutputs[block.id]?.get(INTERNAL_BUILD_ID_KEY)

        return if (persistedBuildId != null) {
            // Resume polling from persisted build ID
            try {
                pollBuildToCompletion(config, persistedBuildId, parameters, block, scope)
            } finally {
                deactivateTokenIfNeeded(block, context)
            }
        } else {
            // No persisted build ID — re-trigger (acknowledged recovery gap)
            execute(block, parameters, context, scope)
        }
    }

    override suspend fun execute(
        block: Block.ActionBlock,
        parameters: List<Parameter>,
        context: ExecutionContext,
        scope: ExecutionScope?,
    ): Map<String, String> {
        val connectionId = block.connectionId
            ?: throw IllegalStateException("TeamCity Build block requires a connection")
        val config = context.connections[connectionId] as? ConnectionConfig.TeamCityConfig
            ?: throw IllegalStateException("TeamCity connection config not found for $connectionId")

        val buildTypeId = parameters.find { it.key == "buildTypeId" }?.value
            ?: throw IllegalArgumentException("TeamCity Build requires 'buildTypeId' parameter")
        val branch = parameters.find { it.key == "branch" }?.value

        val customParams = parameters.filter {
            it.key !in SYSTEM_PARAMETER_KEYS && it.key.isNotBlank() && it.value.isNotBlank()
        }

        // If webhook URL injection is enabled, create a token and add webhook params
        val webhookParams = if (block.injectWebhookUrl && statusWebhookService != null) {
            val token = statusWebhookService.createToken(context.releaseId, block.id)
            val url = statusWebhookService.webhookUrl()
            listOf(
                Parameter(key = WEBHOOK_URL_PARAM, value = url),
                Parameter(key = WEBHOOK_TOKEN_PARAM, value = token.toString()),
            )
        } else {
            emptyList()
        }

        val allCustomParams = customParams + webhookParams

        // Trigger build with XML-escaped parameters
        val xmlBody = buildString {
            append("""<build>""")
            append("""<buildType id="${escapeXml(buildTypeId)}"/>""")
            if (branch != null) {
                append("""<branchName>${escapeXml(branch)}</branchName>""")
            }
            if (allCustomParams.isNotEmpty()) {
                append("<properties>")
                for (param in allCustomParams) {
                    append("""<property name="${escapeXml(param.key)}" value="${escapeXml(param.value)}"/>""")
                }
                append("</properties>")
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

        // Persist build ID before polling so recovery can resume
        scope?.persistOutputs(mapOf(INTERNAL_BUILD_ID_KEY to buildId))

        return try {
            pollBuildToCompletion(config, buildId, parameters, block, scope)
        } finally {
            deactivateTokenIfNeeded(block, context)
        }
    }

    private suspend fun pollBuildToCompletion(
        config: ConnectionConfig.TeamCityConfig,
        buildId: String,
        parameters: List<Parameter>,
        // todo claude: unused
        block: Block.ActionBlock,
        scope: ExecutionScope?,
    ): Map<String, String> {
        // Discover sub-builds once (non-fatal on failure)
        var subBuilds = buildPollingService.discoverTeamCitySubBuilds(
            config.serverUrl, config.token, buildId,
        )
        if (subBuilds.isNotEmpty()) {
            scope?.updateSubBuilds(subBuilds)
        }

        // Poll main build + sub-build statuses
        val baseOutputs = buildPollingService.pollTeamCityBuild(
            serverUrl = config.serverUrl,
            token = config.token,
            buildId = buildId,
            intervalSeconds = config.pollingIntervalSeconds,
        ) { _, _ ->
            // On each tick, also update sub-build statuses if we have any
            if (subBuilds.isNotEmpty()) {
                subBuilds = buildPollingService.pollTeamCitySubBuildStatuses(
                    config.serverUrl, config.token, subBuilds,
                )
                scope?.updateSubBuilds(subBuilds)
            }
        }

        return baseOutputs + fetchArtifactOutputs(parameters, config, buildId)
    }

    private suspend fun deactivateTokenIfNeeded(block: Block.ActionBlock, context: ExecutionContext) {
        if (block.injectWebhookUrl) {
            statusWebhookService?.deactivateToken(context.releaseId, block.id)
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
                mapOf(BlockExecution.ARTIFACTS_OUTPUT_KEY to AppJson.encodeToString(artifacts))
            } else {
                emptyMap()
            }
        } catch (e: Exception) {
            log.warn("Failed to fetch artifacts for build $buildId", e)
            emptyMap()
        }
    }

    override suspend fun cancel(block: Block.ActionBlock, context: ExecutionContext) {
        val connectionId = block.connectionId ?: return
        val config = context.connections[connectionId] as? ConnectionConfig.TeamCityConfig ?: return
        val buildId = context.blockOutputs[block.id]?.get(INTERNAL_BUILD_ID_KEY) ?: return

        try {
            val response = httpClient.post("${config.serverUrl}/app/rest/builds/id:$buildId") {
                header("Authorization", "Bearer ${config.token}")
                header("Accept", "application/json")
                contentType(ContentType.Application.Xml)
                setBody("""<buildCancelRequest comment="Stopped by Release Wizard" readdIntoQueue="false"/>""")
            }
            log.info("TeamCity build {} cancel response: {}", buildId, response.status)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            log.warn("Failed to cancel TeamCity build {}: {}", buildId, e.message)
        }

        deactivateTokenIfNeeded(block, context)
    }

    companion object {
        const val INTERNAL_BUILD_ID_KEY = "_tcBuildId"

        const val WEBHOOK_URL_PARAM = "env.RELEASE_WIZARD_WEBHOOK_URL"
        const val WEBHOOK_TOKEN_PARAM = "env.RELEASE_WIZARD_WEBHOOK_TOKEN"

        private val SYSTEM_PARAMETER_KEYS = setOf(
            "buildTypeId", "branch", "artifactsGlob", "artifactsMaxDepth", "artifactsMaxFiles",
        )

        fun escapeXml(value: String): String = value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }
}
