package com.github.mr3zee.execution.executors

import com.github.mr3zee.execution.BlockExecutor
import com.github.mr3zee.execution.ExecutionContext
import com.github.mr3zee.model.Block
import com.github.mr3zee.model.ConnectionConfig
import com.github.mr3zee.model.Parameter
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*

/**
 * Sends a message via Slack incoming webhook.
 *
 * Params: text (required), channel (optional)
 * Outputs: messageTs = "sent", channel
 */
class SlackMessageExecutor(
    private val httpClient: HttpClient,
) : BlockExecutor {

    override suspend fun resume(
        block: Block.ActionBlock,
        parameters: List<Parameter>,
        context: ExecutionContext,
    ): Map<String, String> {
        // Slack webhook is once-only and we can't verify if it was sent.
        // Mark FAILED so user can inspect and re-trigger manually.
        throw RuntimeException("Server restarted — Slack message may have been sent. Please verify and re-trigger if needed.")
    }

    override suspend fun execute(
        block: Block.ActionBlock,
        parameters: List<Parameter>,
        context: ExecutionContext,
    ): Map<String, String> {
        val connectionId = block.connectionId
            ?: throw IllegalStateException("Slack Message block requires a connection")
        val config = context.connections[connectionId] as? ConnectionConfig.SlackConfig
            ?: throw IllegalStateException("Slack connection config not found for $connectionId")

        val text = parameters.find { it.key == "text" }?.value
            ?: throw IllegalArgumentException("Slack Message requires 'text' parameter")
        val channel = parameters.find { it.key == "channel" }?.value

        val body = buildMap<String, String> {
            put("text", text)
            if (channel != null) put("channel", channel)
        }

        val response = httpClient.post(config.webhookUrl) {
            contentType(ContentType.Application.Json)
            setBody(body)
        }

        if (!response.status.isSuccess()) {
            throw RuntimeException("Slack webhook failed: ${response.status} - ${response.bodyAsText()}")
        }

        return buildMap {
            put("messageTs", "sent")
            put("channel", channel ?: "default")
        }
    }
}
