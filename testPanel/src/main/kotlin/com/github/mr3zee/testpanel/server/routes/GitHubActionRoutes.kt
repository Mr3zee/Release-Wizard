package com.github.mr3zee.testpanel.server.routes

import com.github.mr3zee.testpanel.model.GhRunConclusion
import com.github.mr3zee.testpanel.model.GhRunStatus
import com.github.mr3zee.testpanel.model.TestPanelState
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.*

fun Routing.gitHubActionRoutes(state: TestPanelState) {
    post("/repos/{owner}/{repo}/actions/workflows/{file}/dispatches") {
        val owner = call.parameters["owner"] ?: ""
        val repo = call.parameters["repo"] ?: ""
        val file = call.parameters["file"] ?: ""
        val repoKey = "$owner/$repo"

        val body = call.receiveText()
        val ref = try {
            Json.parseToJsonElement(body).jsonObject["ref"]?.jsonPrimitive?.content
        } catch (_: Exception) {
            null
        }

        val workflows = state.currentState().ghWorkflows[repoKey].orEmpty()
        val workflowPath = workflows.find { it.path.endsWith(file) }?.path
            ?: ".github/workflows/$file"

        state.triggerGhRun(repoKey, workflowPath, ref)
        call.respond(HttpStatusCode.NoContent)
    }

    get("/repos/{owner}/{repo}/actions/workflows/{file}/runs") {
        val owner = call.parameters["owner"] ?: ""
        val repo = call.parameters["repo"] ?: ""
        val file = call.parameters["file"] ?: ""
        val repoKey = "$owner/$repo"

        val workflows = state.currentState().ghWorkflows[repoKey].orEmpty()
        val workflowPath = workflows.find { it.path.endsWith(file) }?.path
            ?: ".github/workflows/$file"

        var runs = state.currentState().ghRuns.filter {
            it.repoKey == repoKey && it.workflowPath == workflowPath
        }

        val createdFilter = call.request.queryParameters["created"]
        if (createdFilter != null && createdFilter.startsWith(">=")) {
            val threshold = createdFilter.removePrefix(">=")
            runs = runs.filter { it.createdAt >= threshold }
        }

        val branchFilter = call.request.queryParameters["branch"]
        if (branchFilter != null) {
            runs = runs.filter { it.ref == branchFilter }
        }

        call.respond(
            buildJsonObject {
                put("total_count", runs.size)
                putJsonArray("workflow_runs") {
                    for (run in runs) {
                        addJsonObject {
                            put("id", run.id)
                            put("status", run.status.toGhString())
                            if (run.conclusion != null) {
                                put("conclusion", run.conclusion.toGhString())
                            } else {
                                put("conclusion", JsonNull)
                            }
                            put("html_url", "http://localhost:8111/repos/$repoKey/actions/runs/${run.id}")
                            put("head_branch", run.ref ?: "main")
                            put("created_at", run.createdAt)
                        }
                    }
                }
            }
        )
    }

    get("/repos/{owner}/{repo}/actions/runs/{runId}") {
        val owner = call.parameters["owner"] ?: ""
        val repo = call.parameters["repo"] ?: ""
        val runId = call.parameters["runId"]?.toIntOrNull()
        val repoKey = "$owner/$repo"

        if (runId == null) {
            call.respondText("Invalid run ID", status = HttpStatusCode.BadRequest)
            return@get
        }

        val run = state.currentState().ghRuns.find { it.id == runId && it.repoKey == repoKey }
        if (run == null) {
            call.respondText("Run not found: $runId", status = HttpStatusCode.NotFound)
            return@get
        }

        call.respond(
            buildJsonObject {
                put("id", run.id)
                put("status", run.status.toGhString())
                if (run.conclusion != null) {
                    put("conclusion", run.conclusion.toGhString())
                } else {
                    put("conclusion", JsonNull)
                }
                put("html_url", "http://localhost:8111/repos/$repoKey/actions/runs/${run.id}")
                put("head_branch", run.ref ?: "main")
                put("created_at", run.createdAt)
            }
        )
    }

    get("/repos/{owner}/{repo}/actions/runs/{runId}/jobs") {
        val owner = call.parameters["owner"] ?: ""
        val repo = call.parameters["repo"] ?: ""
        val runId = call.parameters["runId"]?.toIntOrNull()
        val repoKey = "$owner/$repo"

        if (runId == null) {
            call.respondText("Invalid run ID", status = HttpStatusCode.BadRequest)
            return@get
        }

        val run = state.currentState().ghRuns.find { it.id == runId && it.repoKey == repoKey }
        if (run == null) {
            call.respondText("Run not found: $runId", status = HttpStatusCode.NotFound)
            return@get
        }

        call.respond(
            buildJsonObject {
                put("total_count", run.jobs.size)
                putJsonArray("jobs") {
                    for (job in run.jobs) {
                        addJsonObject {
                            put("id", job.id)
                            put("name", job.name)
                            put("status", job.status.toGhString())
                            if (job.conclusion != null) {
                                put("conclusion", job.conclusion.toGhString())
                            } else {
                                put("conclusion", JsonNull)
                            }
                            if (job.startedAt != null) put("started_at", job.startedAt)
                            if (job.completedAt != null) put("completed_at", job.completedAt)
                            put("html_url", "http://localhost:8111/repos/$repoKey/actions/runs/$runId/jobs/${job.id}")
                        }
                    }
                }
            }
        )
    }

    post("/repos/{owner}/{repo}/actions/runs/{runId}/cancel") {
        val runId = call.parameters["runId"]?.toIntOrNull()

        if (runId == null) {
            call.respondText("Invalid run ID", status = HttpStatusCode.BadRequest)
            return@post
        }

        state.cancelGhRun(runId)
        call.respond(HttpStatusCode.Accepted)
    }
}

private fun GhRunStatus.toGhString(): String = when (this) {
    GhRunStatus.QUEUED -> "queued"
    GhRunStatus.IN_PROGRESS -> "in_progress"
    GhRunStatus.COMPLETED -> "completed"
}

private fun GhRunConclusion.toGhString(): String = when (this) {
    GhRunConclusion.SUCCESS -> "success"
    GhRunConclusion.FAILURE -> "failure"
    GhRunConclusion.CANCELLED -> "cancelled"
    GhRunConclusion.TIMED_OUT -> "timed_out"
}
