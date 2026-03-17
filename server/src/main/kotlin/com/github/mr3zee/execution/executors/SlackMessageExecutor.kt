package com.github.mr3zee.execution.executors

import com.github.mr3zee.AppJson
import com.github.mr3zee.execution.BlockExecutor
import com.github.mr3zee.execution.ExecutionContext
import com.github.mr3zee.execution.ExecutionScope
import com.github.mr3zee.model.Block
import com.github.mr3zee.model.ConnectionConfig
import com.github.mr3zee.model.Parameter
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import org.slf4j.LoggerFactory

/**
 * Sends a message via Slack incoming webhook.
 *
 * Params: text (required), channel (optional)
 * Outputs: messageTs = "sent", channel
 */
class SlackMessageExecutor(
    private val httpClient: HttpClient,
) : BlockExecutor {

    private val log = LoggerFactory.getLogger(SlackMessageExecutor::class.java)

    override suspend fun resume(
        block: Block.ActionBlock,
        parameters: List<Parameter>,
        context: ExecutionContext,
        scope: ExecutionScope?,
    ): Map<String, String> {
        // Slack webhook is once-only and we can't verify if it was sent.
        // Mark FAILED so user can inspect and re-trigger manually.
        throw RuntimeException("Server restarted — Slack message may have been sent. Please verify and re-trigger if needed.")
    }

    override suspend fun execute(
        block: Block.ActionBlock,
        parameters: List<Parameter>,
        context: ExecutionContext,
        scope: ExecutionScope?,
    ): Map<String, String> {
        val connectionId = block.connectionId
            ?: throw IllegalStateException("Slack Message block requires a connection")
        val config = context.connections[connectionId] as? ConnectionConfig.SlackConfig
            ?: throw IllegalStateException("Slack connection config not found for $connectionId")

        val text = parameters.find { it.key == "text" }?.value
            ?: throw IllegalArgumentException("Slack Message requires 'text' parameter")
        val channel = parameters.find { it.key == "channel" }?.value

        val payload = SlackWebhookPayload(text = text, channel = channel)

        val response = httpClient.post(config.webhookUrl) {
            contentType(ContentType.Application.Json)
            setBody(AppJson.encodeToString(payload))
        }

        if (!response.status.isSuccess()) {
            val errorBody = response.bodyAsText()
            log.warn("Slack webhook failed: {} - {}", response.status, errorBody)
            throw RuntimeException("Slack webhook failed (HTTP ${response.status.value})")
        }

        return buildMap {
            put("messageTs", "sent")
            put("channel", channel ?: "default")
        }
    }
}

@Serializable
private data class SlackWebhookPayload(
    val text: String,
    val channel: String? = null,
)
