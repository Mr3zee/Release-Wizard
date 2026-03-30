package com.github.mr3zee.testpanel.server.routes

import com.github.mr3zee.testpanel.model.TestPanelState
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.*
import java.util.Base64

fun Routing.gitHubRepoRoutes(state: TestPanelState) {
    get("/repos/{owner}/{repo}") {
        val owner = call.parameters["owner"] ?: ""
        val repo = call.parameters["repo"] ?: ""
        val repoKey = "$owner/$repo"

        // Always return success for repo existence check — auto-register unknown repos
        // so that "test connection" works without manual setup in the test panel.
        val exists = state.currentState().ghRepos.any { "${it.owner}/${it.repo}" == repoKey }
        if (!exists) {
            state.addGhRepo(com.github.mr3zee.testpanel.model.GhRepo(owner = owner, repo = repo))
        }

        call.respond(
            buildJsonObject {
                put("id", 1)
                put("name", repo)
                put("full_name", repoKey)
            }
        )
    }

    get("/repos/{owner}/{repo}/actions/workflows") {
        val owner = call.parameters["owner"] ?: ""
        val repo = call.parameters["repo"] ?: ""
        val repoKey = "$owner/$repo"

        val workflows = state.currentState().ghWorkflows[repoKey].orEmpty()

        call.respond(
            buildJsonObject {
                put("total_count", workflows.size)
                putJsonArray("workflows") {
                    for (wf in workflows) {
                        addJsonObject {
                            put("id", wf.id)
                            put("name", wf.name)
                            put("path", wf.path)
                            put("state", wf.state)
                        }
                    }
                }
            }
        )
    }

    get("/repos/{owner}/{repo}/contents/.github/workflows/{file}") {
        val owner = call.parameters["owner"] ?: ""
        val repo = call.parameters["repo"] ?: ""
        val file = call.parameters["file"] ?: ""
        val repoKey = "$owner/$repo"

        val workflows = state.currentState().ghWorkflows[repoKey].orEmpty()
        val workflow = workflows.find { it.path.endsWith(file) }

        if (workflow == null) {
            call.respondText("Workflow file not found: $file", status = HttpStatusCode.NotFound)
            return@get
        }

        val yamlContent = workflow.yamlContent
        val encoded = Base64.getEncoder().encodeToString(yamlContent.toByteArray())

        call.respond(
            buildJsonObject {
                put("name", file)
                put("path", workflow.path)
                put("content", encoded)
                put("encoding", "base64")
                put("size", yamlContent.toByteArray().size)
            }
        )
    }
}
