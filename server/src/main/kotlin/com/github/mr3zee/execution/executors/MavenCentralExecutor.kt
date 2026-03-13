package com.github.mr3zee.execution.executors

import com.github.mr3zee.execution.BlockExecutor
import com.github.mr3zee.execution.ExecutionContext
import com.github.mr3zee.model.Block
import com.github.mr3zee.model.ConnectionConfig
import com.github.mr3zee.model.Parameter
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Checks Maven Central publication status via the Central Portal API.
 *
 * Two modes:
 * - By deploymentId: GET /api/v1/publisher/deployment/{deploymentId}
 * - By coordinates: GET /api/v1/publisher/published?namespace={groupId}&name={artifactId}&version={version}
 *
 * Params: Either `deploymentId`, OR `groupId` + `artifactId` + `version`
 * Outputs: repositoryId, status
 */
class MavenCentralExecutor(
    private val httpClient: HttpClient,
) : BlockExecutor {

    override suspend fun execute(
        block: Block.ActionBlock,
        parameters: List<Parameter>,
        context: ExecutionContext,
    ): Map<String, String> {
        val connectionId = block.connectionId
            ?: throw IllegalStateException("Maven Central block requires a connection")
        val config = context.connections[connectionId] as? ConnectionConfig.MavenCentralConfig
            ?: throw IllegalStateException("Maven Central connection config not found for $connectionId")

        val deploymentId = parameters.find { it.key == "deploymentId" }?.value
        val groupId = parameters.find { it.key == "groupId" }?.value
        val artifactId = parameters.find { it.key == "artifactId" }?.value
        val version = parameters.find { it.key == "version" }?.value

        return if (deploymentId != null) {
            checkDeployment(config, deploymentId)
        } else if (groupId != null && artifactId != null && version != null) {
            checkPublished(config, groupId, artifactId, version)
        } else {
            throw IllegalArgumentException(
                "Maven Central requires either 'deploymentId' or 'groupId' + 'artifactId' + 'version' parameters"
            )
        }
    }

    private suspend fun checkDeployment(
        config: ConnectionConfig.MavenCentralConfig,
        deploymentId: String,
    ): Map<String, String> {
        val response = httpClient.get("${config.baseUrl}/api/v1/publisher/deployment/$deploymentId") {
            basicAuth(config.username, config.password)
            header("Accept", "application/json")
        }

        if (!response.status.isSuccess()) {
            throw RuntimeException("Maven Central deployment check failed: ${response.status} - ${response.bodyAsText()}")
        }

        val body = response.body<JsonObject>()
        val status = body["deploymentState"]?.jsonPrimitive?.content ?: "UNKNOWN"

        return mapOf(
            "repositoryId" to deploymentId,
            "status" to status,
        )
    }

    private suspend fun checkPublished(
        config: ConnectionConfig.MavenCentralConfig,
        groupId: String,
        artifactId: String,
        version: String,
    ): Map<String, String> {
        val response = httpClient.get("${config.baseUrl}/api/v1/publisher/published") {
            basicAuth(config.username, config.password)
            header("Accept", "application/json")
            parameter("namespace", groupId)
            parameter("name", artifactId)
            parameter("version", version)
        }

        if (!response.status.isSuccess()) {
            throw RuntimeException("Maven Central published check failed: ${response.status} - ${response.bodyAsText()}")
        }

        val body = response.body<JsonObject>()
        val published = body["published"]?.jsonPrimitive?.content ?: "false"

        return mapOf(
            "repositoryId" to "$groupId:$artifactId:$version",
            "status" to if (published == "true") "PUBLISHED" else "NOT_PUBLISHED",
        )
    }
}
