package com.github.mr3zee.testpanel.server.routes

import com.github.mr3zee.testpanel.model.TestPanelState
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

fun Routing.slackWebhookRoutes(state: TestPanelState) {
    post("/services/{path...}") {
        val body = call.receiveText()

        val parsed = try {
            val json = Json.parseToJsonElement(body).jsonObject
            Pair(json["text"]?.jsonPrimitive?.content ?: body, json["channel"]?.jsonPrimitive?.content)
        } catch (_: Exception) {
            Pair(body, null)
        }
        val text = parsed.first
        val channel = parsed.second

        state.addSlackMessage(text, channel)
        call.respondText("ok", status = HttpStatusCode.OK)
    }
}
