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
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.slf4j.LoggerFactory

/**
 * Creates a GitHub release via the GitHub API.
 *
 * Params: tagName (required), releaseName (optional), body (optional),
 *         draft (optional, default false), prerelease (optional, default false)
 * Outputs: releaseUrl, tagName
 */
class GitHubPublicationExecutor(
    private val httpClient: HttpClient,
) : BlockExecutor {

    private val log = LoggerFactory.getLogger(GitHubPublicationExecutor::class.java)

    private fun resolveGitHubConfig(
        block: Block.ActionBlock,
        context: ExecutionContext,
    ): ConnectionConfig.GitHubConfig {
        val connectionId = block.connectionId
            ?: throw IllegalStateException("GitHub Publication block requires a connection")
        return context.connections[connectionId] as? ConnectionConfig.GitHubConfig
            ?: throw IllegalStateException("GitHub connection config not found for $connectionId")
    }

    override suspend fun resume(
        block: Block.ActionBlock,
        parameters: List<Parameter>,
        context: ExecutionContext,
    ): Map<String, String> {
        val config = resolveGitHubConfig(block, context)

        val tagName = parameters.find { it.key == "tagName" }?.value
            ?: throw IllegalArgumentException("GitHub Publication requires 'tagName' parameter")

        // Check if release already exists by tag
        val checkResponse = httpClient.get("https://api.github.com/repos/${config.owner}/${config.repo}/releases/tags/$tagName") {
            header("Authorization", "Bearer ${config.token}")
            header("Accept", "application/vnd.github+json")
        }

        return if (checkResponse.status.isSuccess()) {
            // Release already exists — extract outputs
            parseReleaseResponse(checkResponse.body(), tagName)
        } else {
            // Release doesn't exist — create it
            execute(block, parameters, context)
        }
    }

    override suspend fun execute(
        block: Block.ActionBlock,
        parameters: List<Parameter>,
        context: ExecutionContext,
    ): Map<String, String> {
        val config = resolveGitHubConfig(block, context)

        val tagName = parameters.find { it.key == "tagName" }?.value
            ?: throw IllegalArgumentException("GitHub Publication requires 'tagName' parameter")
        val releaseName = parameters.find { it.key == "releaseName" }?.value ?: tagName
        val body = parameters.find { it.key == "body" }?.value ?: ""
        val draft = parameters.find { it.key == "draft" }?.value?.toBoolean() ?: false
        val prerelease = parameters.find { it.key == "prerelease" }?.value?.toBoolean() ?: false

        val requestBody = CreateReleaseBody(
            tag_name = tagName,
            name = releaseName,
            body = body,
            draft = draft,
            prerelease = prerelease,
        )

        val response = httpClient.post("https://api.github.com/repos/${config.owner}/${config.repo}/releases") {
            header("Authorization", "Bearer ${config.token}")
            header("Accept", "application/vnd.github+json")
            contentType(ContentType.Application.Json)
            setBody(requestBody)
        }

        if (!response.status.isSuccess()) {
            val errorBody = response.bodyAsText()
            log.warn("GitHub release creation failed: {} - {}", response.status, errorBody)
            throw RuntimeException("GitHub release creation failed (HTTP ${response.status.value})")
        }

        return parseReleaseResponse(response.body(), tagName)
    }

    private fun parseReleaseResponse(responseBody: JsonObject, fallbackTagName: String): Map<String, String> {
        val releaseUrl = responseBody["html_url"]?.jsonPrimitive?.content ?: ""
        val actualTag = responseBody["tag_name"]?.jsonPrimitive?.content ?: fallbackTagName
        val releaseId = responseBody["id"]?.jsonPrimitive?.content ?: ""
        return mapOf("releaseUrl" to releaseUrl, "tagName" to actualTag, "releaseId" to releaseId)
    }

    @Serializable
    private data class CreateReleaseBody(
        @Suppress("PropertyName")
        val tag_name: String,
        val name: String,
        val body: String,
        val draft: Boolean,
        val prerelease: Boolean,
    )
}
