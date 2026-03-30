package com.github.mr3zee.testpanel.server.routes

import com.github.mr3zee.testpanel.model.TestPanelState
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.*

fun Routing.gitHubReleaseRoutes(state: TestPanelState) {
    get("/repos/{owner}/{repo}/releases/tags/{tag}") {
        val owner = call.parameters["owner"] ?: ""
        val repo = call.parameters["repo"] ?: ""
        val tag = call.parameters["tag"] ?: ""
        val repoKey = "$owner/$repo"

        val release = state.currentState().ghReleases.find {
            it.repoKey == repoKey && it.tagName == tag
        }

        if (release == null) {
            call.respondText("Release not found: $tag", status = HttpStatusCode.NotFound)
            return@get
        }

        call.respond(
            buildJsonObject {
                put("id", release.id)
                put("html_url", release.htmlUrl)
                put("tag_name", release.tagName)
                put("name", release.name)
                put("body", release.body)
                put("draft", release.draft)
                put("prerelease", release.prerelease)
            }
        )
    }

    post("/repos/{owner}/{repo}/releases") {
        val owner = call.parameters["owner"] ?: ""
        val repo = call.parameters["repo"] ?: ""
        val repoKey = "$owner/$repo"

        val body = call.receiveText()
        val json = try {
            Json.parseToJsonElement(body).jsonObject
        } catch (_: Exception) {
            call.respondText("Invalid JSON body", status = HttpStatusCode.BadRequest)
            return@post
        }

        val tagName = json["tag_name"]?.jsonPrimitive?.content
        if (tagName == null) {
            call.respondText("Missing tag_name", status = HttpStatusCode.BadRequest)
            return@post
        }

        val name = json["name"]?.jsonPrimitive?.content ?: tagName
        val releaseBody = json["body"]?.jsonPrimitive?.content ?: ""
        val draft = json["draft"]?.jsonPrimitive?.booleanOrNull ?: false
        val prerelease = json["prerelease"]?.jsonPrimitive?.booleanOrNull ?: false

        val release = state.createGhRelease(
            repoKey = repoKey,
            tagName = tagName,
            name = name,
            body = releaseBody,
            draft = draft,
            prerelease = prerelease,
        )

        call.respond(
            HttpStatusCode.Created,
            buildJsonObject {
                put("id", release.id)
                put("html_url", release.htmlUrl)
                put("tag_name", release.tagName)
            }
        )
    }
}
