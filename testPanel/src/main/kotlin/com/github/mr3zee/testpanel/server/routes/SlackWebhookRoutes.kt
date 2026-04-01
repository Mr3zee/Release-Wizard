package com.github.mr3zee.testpanel.server.routes

import com.github.mr3zee.testpanel.model.TestPanelState
import io.ktor.http.*
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Mock Slack Bot API endpoints for the test panel.
 * Handles chat.postMessage, auth.test, and conversations.history.
 */
fun Routing.slackApiRoutes(state: TestPanelState) {
    route("/api") {
        // POST /api/chat.postMessage — mock message posting
        post("/chat.postMessage") {
            val body = call.receiveText()

            val json = try {
                Json.parseToJsonElement(body).jsonObject
            } catch (_: Exception) {
                call.respondText(
                    buildJsonObject { put("ok", false); put("error", "invalid_json") }.toString(),
                    ContentType.Application.Json,
                    HttpStatusCode.OK,
                )
                return@post
            }

            val text = json["text"]?.jsonPrimitive?.content ?: ""
            val channel = json["channel"]?.jsonPrimitive?.content ?: "C0000000000"

            val msg = state.addSlackMessage(text, channel)

            val now = System.currentTimeMillis()
            val ts = "${now / 1000}.${"%06d".format(now % 1000 * 1000)}"
            val responseJson = buildJsonObject {
                put("ok", true)
                put("ts", ts)
                put("channel", channel)
            }
            call.respondText(responseJson.toString(), ContentType.Application.Json, HttpStatusCode.OK)
        }

        // GET/POST /api/auth.test — mock bot authentication test
        get("/auth.test") { handleAuthTest(call) }
        post("/auth.test") { handleAuthTest(call) }

        // GET /api/conversations.history — mock channel history
        get("/conversations.history") {
            val messages = state.getSlackMessages()
            val responseJson = buildJsonObject {
                put("ok", true)
                put("messages", buildJsonArray {
                    for (msg in messages) {
                        add(buildJsonObject {
                            put("text", msg.text)
                            val now = System.currentTimeMillis()
                            put("ts", "${now / 1000}.${"%06d".format(now % 1000 * 1000)}")
                        })
                    }
                })
            }
            call.respondText(responseJson.toString(), ContentType.Application.Json, HttpStatusCode.OK)
        }
    }
}

private suspend fun handleAuthTest(call: ApplicationCall) {
    val responseJson = buildJsonObject {
        put("ok", true)
        put("team", "Test Panel")
        put("user", "test-bot")
        put("team_id", "T00000000")
        put("user_id", "U00000000")
    }
    call.respondText(responseJson.toString(), ContentType.Application.Json, HttpStatusCode.OK)
}
