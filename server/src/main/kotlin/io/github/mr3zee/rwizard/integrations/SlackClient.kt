package io.github.mr3zee.rwizard.integrations

import io.github.mr3zee.rwizard.api.SlackChannel
import io.github.mr3zee.rwizard.domain.model.Credentials
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

/**
 * Slack API integration client for Release Wizard
 * Provides functionality for sending messages and fetching channel information
 */
class SlackClient(
    private val credentials: Credentials.SlackCredentials
) {
    private val logger = LoggerFactory.getLogger(SlackClient::class.java)
    
    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                coerceInputValues = true
            })
        }
    }

    companion object {
        const val BASE_URL = "https://slack.com/api"
    }

    /**
     * Test the Slack connection by calling auth.test
     */
    suspend fun testConnection(): Result<SlackAuthTest> = try {
        val response = client.get("$BASE_URL/auth.test") {
            headers {
                append("Authorization", "Bearer ${credentials.botToken}")
                append("Content-Type", "application/json")
            }
        }
        
        val result = response.body<SlackAuthTest>()
        if (result.ok) {
            logger.info("Slack connection test successful for team: ${result.team}")
            Result.success(result)
        } else {
            logger.warn("Slack connection test failed: ${result.error}")
            Result.failure(Exception("Slack API error: ${result.error}"))
        }
    } catch (e: Exception) {
        logger.error("Failed to test Slack connection", e)
        Result.failure(e)
    }

    /**
     * Fetch list of channels accessible to the bot
     */
    suspend fun getChannels(excludeArchived: Boolean = true): Result<List<SlackChannel>> = try {
        val response = client.get("$BASE_URL/conversations.list") {
            headers {
                append("Authorization", "Bearer ${credentials.botToken}")
            }
            url {
                parameters.append("exclude_archived", excludeArchived.toString())
                parameters.append("types", "public_channel,private_channel")
                parameters.append("limit", "200")
            }
        }
        
        val result = response.body<SlackChannelListResult>()
        if (result.ok) {
            val channels = result.channels.map { channel ->
                SlackChannel(
                    id = channel.id,
                    name = channel.name,
                    isPrivate = channel.is_private
                )
            }
            logger.info("Retrieved ${channels.size} Slack channels")
            Result.success(channels)
        } else {
            logger.warn("Failed to fetch Slack channels: ${result.error}")
            Result.failure(Exception("Slack API error: ${result.error}"))
        }
    } catch (e: Exception) {
        logger.error("Failed to fetch Slack channels", e)
        Result.failure(e)
    }

    /**
     * Send a message to a Slack channel
     */
    suspend fun sendMessage(
        channelId: String,
        message: String,
        threadTs: String? = null
    ): Result<SlackMessageResponse> = try {
        val response = client.post("$BASE_URL/chat.postMessage") {
            headers {
                append("Authorization", "Bearer ${credentials.botToken}")
                append("Content-Type", "application/json")
            }
            setBody(SlackMessageRequest(
                channel = channelId,
                text = message,
                thread_ts = threadTs
            ))
        }
        
        val result = response.body<SlackMessageResponse>()
        if (result.ok) {
            logger.info("Message sent to Slack channel $channelId: ${result.ts}")
            Result.success(result)
        } else {
            logger.warn("Failed to send Slack message: ${result.error}")
            Result.failure(Exception("Slack API error: ${result.error}"))
        }
    } catch (e: Exception) {
        logger.error("Failed to send Slack message", e)
        Result.failure(e)
    }

    /**
     * Update an existing message
     */
    suspend fun updateMessage(
        channelId: String,
        messageTs: String,
        newMessage: String
    ): Result<SlackMessageResponse> = try {
        val response = client.post("$BASE_URL/chat.update") {
            headers {
                append("Authorization", "Bearer ${credentials.botToken}")
                append("Content-Type", "application/json")
            }
            setBody(SlackUpdateMessageRequest(
                channel = channelId,
                ts = messageTs,
                text = newMessage
            ))
        }
        
        val result = response.body<SlackMessageResponse>()
        if (result.ok) {
            logger.info("Message updated in Slack channel $channelId: $messageTs")
            Result.success(result)
        } else {
            logger.warn("Failed to update Slack message: ${result.error}")
            Result.failure(Exception("Slack API error: ${result.error}"))
        }
    } catch (e: Exception) {
        logger.error("Failed to update Slack message", e)
        Result.failure(e)
    }

    fun close() {
        client.close()
    }
}

// Slack API Data Models
@Serializable
data class SlackAuthTest(
    val ok: Boolean,
    val url: String? = null,
    val team: String? = null,
    val user: String? = null,
    val team_id: String? = null,
    val user_id: String? = null,
    val bot_id: String? = null,
    val error: String? = null
)

@Serializable
data class SlackChannelListResult(
    val ok: Boolean,
    val channels: List<SlackChannelInfo> = emptyList(),
    val error: String? = null
)

@Serializable
data class SlackChannelInfo(
    val id: String,
    val name: String,
    val is_private: Boolean,
    val is_archived: Boolean = false,
    val is_general: Boolean = false,
    val is_member: Boolean = false,
    val topic: SlackChannelTopic? = null,
    val purpose: SlackChannelPurpose? = null
)

@Serializable
data class SlackChannelTopic(
    val value: String,
    val creator: String,
    val last_set: Long
)

@Serializable
data class SlackChannelPurpose(
    val value: String,
    val creator: String,
    val last_set: Long
)

@Serializable
data class SlackMessageRequest(
    val channel: String,
    val text: String,
    val thread_ts: String? = null,
    val unfurl_links: Boolean = true,
    val unfurl_media: Boolean = true
)

@Serializable
data class SlackUpdateMessageRequest(
    val channel: String,
    val ts: String,
    val text: String
)

@Serializable
data class SlackMessageResponse(
    val ok: Boolean,
    val channel: String? = null,
    val ts: String? = null,
    val message: SlackMessage? = null,
    val error: String? = null
)

@Serializable
data class SlackMessage(
    val type: String,
    val user: String? = null,
    val ts: String,
    val text: String,
    val thread_ts: String? = null
)
