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
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonArray
import org.slf4j.LoggerFactory

/**
 * Sends a message via Slack Bot API (chat.postMessage).
 *
 * Params: text (required), channel (required)
 * Outputs: messageTs, channel
 */
class SlackMessageExecutor(
    private val httpClient: HttpClient,
    private val slackApiBaseUrl: String = "https://slack.com/api",
) : BlockExecutor {

    private val log = LoggerFactory.getLogger(SlackMessageExecutor::class.java)

    private data class SlackParams(
        val config: ConnectionConfig.SlackConfig,
        val text: String,
        val channel: String,
    )

    private fun resolveParams(
        block: Block.ActionBlock,
        parameters: List<Parameter>,
        context: ExecutionContext,
    ): SlackParams {
        val connectionId = block.connectionId
            ?: throw IllegalStateException("Slack Message block requires a connection")
        val config = context.connections[connectionId] as? ConnectionConfig.SlackConfig
            ?: throw IllegalStateException("Slack connection config not found for $connectionId")
        val text = parameters.find { it.key == "text" }?.value
            ?: throw IllegalArgumentException("Slack Message requires 'text' parameter")
        val channel = parameters.find { it.key == "channel" }?.value
            ?: throw IllegalArgumentException("Slack Message requires 'channel' parameter")
        return SlackParams(config, text, channel)
    }

    override suspend fun resume(
        block: Block.ActionBlock,
        parameters: List<Parameter>,
        context: ExecutionContext,
        scope: ExecutionScope?,
    ): Map<String, String> {
        val (config, text, channel) = resolveParams(block, parameters, context)

        // Try to verify the message was sent by checking conversations.history
        log.info("Resuming Slack message — checking if message was already sent to channel {}", channel)
        val existingMessage = findMessageByText(config.botToken, channel, text)
        if (existingMessage != null) {
            log.info("Found existing Slack message (ts={}), resuming with outputs", existingMessage.first)
            return mapOf("messageTs" to existingMessage.first, "channel" to existingMessage.second)
        }

        // Message not found — re-send
        log.info("Message not found in channel history, re-sending")
        return execute(block, parameters, context, scope)
    }

    override suspend fun execute(
        block: Block.ActionBlock,
        parameters: List<Parameter>,
        context: ExecutionContext,
        scope: ExecutionScope?,
    ): Map<String, String> {
        val (config, text, channel) = resolveParams(block, parameters, context)

        val payload = SlackChatPostMessagePayload(channel = channel, text = text)

        val response = httpClient.post("$slackApiBaseUrl/chat.postMessage") {
            header("Authorization", "Bearer ${config.botToken}")
            contentType(ContentType.Application.Json)
            setBody(AppJson.encodeToString(payload))
        }

        val responseBody = response.bodyAsText()
        if (!response.status.isSuccess()) {
            log.warn("Slack API HTTP error: {} - {}", response.status, responseBody)
            throw RuntimeException("Slack API failed (HTTP ${response.status.value})")
        }

        val json = AppJson.parseToJsonElement(responseBody).jsonObject
        val ok = json["ok"]?.jsonPrimitive?.booleanOrNull ?: false
        if (!ok) {
            val error = json["error"]?.jsonPrimitive?.content ?: "unknown"
            log.warn("Slack API error: {}", error)
            throw RuntimeException("Slack API error: $error")
        }

        val messageTs = json["ts"]?.jsonPrimitive?.content
            ?: error("Slack API returned ok=true but no ts field")
        val responseChannel = json["channel"]?.jsonPrimitive?.content ?: channel

        return mapOf("messageTs" to messageTs, "channel" to responseChannel)
    }

    /**
     * Searches recent channel history for a message with exactly the given text.
     * Returns (ts, channel) if found, null otherwise.
     */
    private suspend fun findMessageByText(
        botToken: String,
        channel: String,
        text: String,
    ): Pair<String, String>? {
        return try {
            val response = httpClient.get("$slackApiBaseUrl/conversations.history") {
                header("Authorization", "Bearer $botToken")
                parameter("channel", channel)
                parameter("limit", 50)
            }
            val body = AppJson.parseToJsonElement(response.bodyAsText()).jsonObject
            val ok = body["ok"]?.jsonPrimitive?.booleanOrNull ?: false
            if (!ok) return null

            val messages = body["messages"]?.jsonArray ?: return null
            for (msg in messages) {
                val obj = msg.jsonObject
                val msgText = obj["text"]?.jsonPrimitive?.content ?: continue
                if (msgText == text) {
                    val ts = obj["ts"]?.jsonPrimitive?.content ?: continue
                    return ts to channel
                }
            }
            null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.warn("Failed to check Slack message history: {}", e.message)
            null
        }
    }
}

@Serializable
private data class SlackChatPostMessagePayload(
    val channel: String,
    val text: String,
)
