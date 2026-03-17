package com.github.mr3zee.execution.executors

import com.github.mr3zee.model.SubBuild
import com.github.mr3zee.model.SubBuildStatus
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/**
 * Thrown when a build stays in the queue longer than the allowed timeout.
 */
class QueueTimeoutException(message: String) : RuntimeException(message)

/**
 * Service for polling TeamCity and GitHub APIs for build/run status.
 * Provides sub-build discovery and status polling for TC build chains.
 */
class BuildPollingService(
    private val httpClient: HttpClient,
) {
    private val log = LoggerFactory.getLogger(BuildPollingService::class.java)

    // ── TeamCity ─────────────────────────────────────────────────────────

    /**
     * Poll a TeamCity build until it reaches a terminal state.
     * Returns the final build JSON as a map of outputs.
     *
     * @param onUpdate called on each poll tick with the current build state string
     */
    suspend fun pollTeamCityBuild(
        serverUrl: String,
        token: String,
        buildId: String,
        intervalSeconds: Int,
        queueTimeout: Duration = QUEUE_TIMEOUT,
        onUpdate: suspend (state: String, status: String?) -> Unit = { _, _ -> },
    ): Map<String, String> {
        var queuedSince: TimeMark? = null

        while (true) {
            val json = fetchTeamCityBuild(serverUrl, token, buildId) ?: run {
                delay(intervalSeconds.seconds)
                continue
            }

            val state = json["state"]?.jsonPrimitive?.content ?: "unknown"
            val status = json["status"]?.jsonPrimitive?.content
            onUpdate(state, status)

            if (state == "queued") {
                if (queuedSince == null) {
                    queuedSince = TimeSource.Monotonic.markNow()
                }
                val elapsed = queuedSince.elapsedNow()
                if (elapsed >= queueTimeout) {
                    throw QueueTimeoutException(
                        "TeamCity build $buildId stayed in queue for over $queueTimeout"
                    )
                }
            } else {
                queuedSince = null
            }

            if (state == "finished") {
                val buildNumber = json["number"]?.jsonPrimitive?.content ?: buildId
                val buildStatus = status ?: "UNKNOWN"
                return mapOf(
                    "buildNumber" to buildNumber,
                    "buildUrl" to "$serverUrl/viewLog.html?buildId=$buildId",
                    "buildStatus" to buildStatus,
                )
            }

            delay(intervalSeconds.seconds)
        }
    }

    /**
     * Discover sub-builds (snapshot dependencies) for a TeamCity composite build.
     * Uses BFS to compute dependency levels. Returns empty list on failure (non-fatal).
     */
    suspend fun discoverTeamCitySubBuilds(
        serverUrl: String,
        token: String,
        topBuildId: String,
    ): List<SubBuild> {
        return try {
            val url = "$serverUrl/app/rest/builds?locator=snapshotDependency:(to:(id:$topBuildId),includeInitial:true)&fields=build(id,number,buildType(name),state,status,startDate,finishDate)"
            val response = httpClient.get(url) {
                header("Authorization", "Bearer $token")
                header("Accept", "application/json")
            }
            if (!response.status.isSuccess()) return emptyList()

            val body = response.body<JsonObject>()
            val builds = body["build"] as? JsonArray ?: return emptyList()

            val buildMap = mutableMapOf<String, JsonObject>()
            for (build in builds) {
                val obj = build.jsonObject
                val id = obj["id"]?.jsonPrimitive?.content ?: continue
                buildMap[id] = obj
            }

            if (buildMap.size <= 1) return emptyList()

            val subBuilds = mutableListOf<SubBuild>()
            var level = 0
            for ((id, obj) in buildMap) {
                if (id == topBuildId) continue
                subBuilds.add(parseSubBuild(obj, id, serverUrl, level))
                level++
            }
            subBuilds
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.warn("Failed to discover sub-builds for TC build {}: {}", topBuildId, e.message)
            emptyList()
        }
    }

    /**
     * Poll individual sub-build statuses. Lightweight — only fetches state/status.
     */
    suspend fun pollTeamCitySubBuildStatuses(
        serverUrl: String,
        token: String,
        subBuilds: List<SubBuild>,
    ): List<SubBuild> {
        return subBuilds.map { sub ->
            try {
                val json = fetchTeamCityBuild(serverUrl, token, sub.id) ?: return@map sub
                parseSubBuild(json, sub.id, serverUrl, sub.dependencyLevel)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log.debug("Failed to poll sub-build {}: {}", sub.id, e.message)
                sub
            }
        }
    }

    private suspend fun fetchTeamCityBuild(serverUrl: String, token: String, buildId: String): JsonObject? {
        return try {
            val response = httpClient.get("$serverUrl/app/rest/builds/id:$buildId") {
                header("Authorization", "Bearer $token")
                header("Accept", "application/json")
            }
            if (response.status.isSuccess()) {
                response.body<JsonObject>()
            } else {
                log.debug("TC build poll returned {}", response.status)
                null
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.debug("TC build poll failed for {}: {}", buildId, e.message)
            null
        }
    }

    private fun parseSubBuild(json: JsonObject, buildId: String, serverUrl: String, level: Int): SubBuild {
        val state = json["state"]?.jsonPrimitive?.content ?: "unknown"
        val status = json["status"]?.jsonPrimitive?.content
        val name = json["buildType"]?.jsonObject?.get("name")?.jsonPrimitive?.content ?: "Build $buildId"

        val subStatus = when {
            state == "finished" && status == "SUCCESS" -> SubBuildStatus.SUCCEEDED
            state == "finished" && status == "FAILURE" -> SubBuildStatus.FAILED
            state == "finished" && status == "UNKNOWN" -> SubBuildStatus.CANCELLED
            state == "running" -> SubBuildStatus.RUNNING
            state == "queued" -> SubBuildStatus.QUEUED
            else -> SubBuildStatus.UNKNOWN
        }

        return SubBuild(
            id = buildId,
            name = name,
            status = subStatus,
            buildUrl = "$serverUrl/viewLog.html?buildId=$buildId",
            dependencyLevel = level,
        )
    }

    // ── GitHub Actions ───────────────────────────────────────────────────

    /**
     * Discover jobs within a GitHub Actions workflow run.
     * Returns current job statuses as SubBuilds. Reusable for both initial discovery
     * and subsequent status polling (GH returns all jobs in one call).
     * Non-fatal: returns empty list on failure.
     */
    suspend fun discoverGitHubJobs(
        owner: String,
        repo: String,
        token: String,
        runId: String,
    ): List<SubBuild> {
        return try {
            val url = "https://api.github.com/repos/$owner/$repo/actions/runs/$runId/jobs?per_page=100"
            val response = httpClient.get(url) {
                header("Authorization", "Bearer $token")
                header("Accept", "application/vnd.github+json")
            }

            if (response.status == HttpStatusCode.TooManyRequests) {
                log.debug("GitHub jobs API rate-limited for run {}", runId)
                return emptyList()
            }
            if (response.status == HttpStatusCode.Forbidden) {
                log.info("GitHub jobs API returned 403 for run {} — token may lack actions:read scope", runId)
                return emptyList()
            }
            if (!response.status.isSuccess()) return emptyList()

            val body = response.body<JsonObject>()
            val jobs = body["jobs"] as? JsonArray ?: return emptyList()

            jobs.mapNotNull { jobElement ->
                val job = jobElement.jsonObject
                val id = job["id"]?.jsonPrimitive?.long?.toString() ?: return@mapNotNull null
                val name = job["name"]?.jsonPrimitive?.contentOrNull ?: "Job $id"
                val status = job["status"]?.jsonPrimitive?.contentOrNull
                val conclusion = job["conclusion"]?.jsonPrimitive?.contentOrNull
                val htmlUrl = job["html_url"]?.jsonPrimitive?.contentOrNull
                    ?.takeIf { it.startsWith("https://github.com/") }

                val subStatus = mapGitHubJobStatus(status, conclusion)

                val startedAt = job["started_at"]?.jsonPrimitive?.contentOrNull
                val completedAt = job["completed_at"]?.jsonPrimitive?.contentOrNull
                val durationSeconds = if (startedAt != null && completedAt != null) {
                    try {
                        val start = java.time.Instant.parse(startedAt)
                        val end = java.time.Instant.parse(completedAt)
                        java.time.Duration.between(start, end).seconds.coerceAtLeast(0)
                    } catch (_: Exception) {
                        null
                    }
                } else {
                    null
                }

                SubBuild(
                    id = id,
                    name = name,
                    status = subStatus,
                    buildUrl = htmlUrl,
                    durationSeconds = durationSeconds,
                    dependencyLevel = 0,
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.debug("Failed to discover GH jobs for run {}: {}", runId, e.message)
            emptyList()
        }
    }

    companion object {
        val QUEUE_TIMEOUT: Duration = 20.minutes
    }

    private fun mapGitHubJobStatus(status: String?, conclusion: String?): SubBuildStatus = when {
        status == "queued" || status == "waiting" -> SubBuildStatus.QUEUED
        status == "in_progress" -> SubBuildStatus.RUNNING
        status == "completed" -> when (conclusion) {
            "success", "neutral" -> SubBuildStatus.SUCCEEDED
            "failure", "timed_out", "startup_failure" -> SubBuildStatus.FAILED
            "cancelled", "skipped", "stale" -> SubBuildStatus.CANCELLED
            "action_required" -> SubBuildStatus.QUEUED
            else -> SubBuildStatus.UNKNOWN
        }
        else -> SubBuildStatus.UNKNOWN
    }

    /**
     * Poll a GitHub Actions run until it reaches a terminal state.
     * Respects X-RateLimit-Remaining header and backs off when low.
     */
    suspend fun pollGitHubRun(
        owner: String,
        repo: String,
        token: String,
        runId: String,
        intervalSeconds: Int,
        queueTimeout: Duration = QUEUE_TIMEOUT,
        onUpdate: suspend (status: String, conclusion: String?) -> Unit = { _, _ -> },
    ): Map<String, String> {
        val baseUrl = "https://api.github.com/repos/$owner/$repo"
        var queuedSince: TimeMark? = null

        while (true) {
            try {
                val response = httpClient.get("$baseUrl/actions/runs/$runId") {
                    header("Authorization", "Bearer $token")
                    header("Accept", "application/vnd.github+json")
                }

                // Respect rate limits
                val remaining = response.headers["X-RateLimit-Remaining"]?.toIntOrNull()
                if (remaining != null && remaining <= 5) {
                    log.info("GitHub rate limit low ({}), backing off", remaining)
                    delay((intervalSeconds * 3).coerceAtMost(300).seconds)
                    continue
                }

                if (response.status.isSuccess()) {
                    val json = response.body<JsonObject>()
                    val status = json["status"]?.jsonPrimitive?.content ?: "unknown"
                    val conclusion = json["conclusion"]?.jsonPrimitive?.contentOrNull

                    onUpdate(status, conclusion)

                    if (status == "queued" || status == "waiting") {
                        if (queuedSince == null) {
                            queuedSince = TimeSource.Monotonic.markNow()
                        }
                        val elapsed = queuedSince.elapsedNow()
                        if (elapsed >= queueTimeout) {
                            throw QueueTimeoutException(
                                "GitHub Actions run $runId stayed in queue for over $queueTimeout"
                            )
                        }
                    } else {
                        queuedSince = null
                    }

                    if (status == "completed") {
                        return mapOf(
                            "runId" to runId,
                            "runUrl" to (json["html_url"]?.jsonPrimitive?.content
                                ?: "https://github.com/$owner/$repo/actions/runs/$runId"),
                            "runStatus" to (conclusion ?: "unknown"),
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: QueueTimeoutException) {
                throw e
            } catch (e: Exception) {
                log.debug("GitHub run poll failed for {}: {}", runId, e.message)
            }

            delay(intervalSeconds.seconds)
        }
    }
}
