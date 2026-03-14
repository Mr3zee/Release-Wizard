package com.github.mr3zee.slack

import com.github.mr3zee.AppJson
import com.github.mr3zee.waitUntil
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.*

fun createSlackTestHttpClient(): HttpClient {
    return HttpClient(CIO) {
        install(ContentNegotiation) {
            json(AppJson)
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 30_000
            connectTimeoutMillis = 10_000
            socketTimeoutMillis = 30_000
        }
    }
}

data class SlackMessage(
    val text: String,
    val ts: String,
)

suspend fun HttpClient.fetchSlackMessages(
    botToken: String,
    channelId: String,
    limit: Int = 20,
): List<SlackMessage> {
    val response = get("https://slack.com/api/conversations.history") {
        header("Authorization", "Bearer $botToken")
        parameter("channel", channelId)
        parameter("limit", limit)
    }
    val body = AppJson.decodeFromString<JsonObject>(response.bodyAsText())
    val ok = body["ok"]?.jsonPrimitive?.boolean ?: false
    if (!ok) {
        val error = body["error"]?.jsonPrimitive?.content ?: "unknown"
        throw RuntimeException("Slack API error: $error")
    }
    val messages = body["messages"]?.jsonArray ?: return emptyList()
    return messages.mapNotNull { msg ->
        val obj = msg.jsonObject
        val text = obj["text"]?.jsonPrimitive?.content ?: return@mapNotNull null
        val ts = obj["ts"]?.jsonPrimitive?.content ?: return@mapNotNull null
        SlackMessage(text = text, ts = ts)
    }
}

suspend fun HttpClient.findSlackMessageByText(
    botToken: String,
    channelId: String,
    textSubstring: String,
    maxAttempts: Int = 30,
    delayMillis: Long = 500,
): SlackMessage {
    var found: SlackMessage? = null
    waitUntil(maxAttempts = maxAttempts, delayMillis = delayMillis) {
        val messages = fetchSlackMessages(botToken, channelId)
        found = messages.find { textSubstring in it.text }
        found != null
    }
    return found!!
}
