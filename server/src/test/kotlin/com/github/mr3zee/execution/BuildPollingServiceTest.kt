package com.github.mr3zee.execution

import com.github.mr3zee.AppJson
import com.github.mr3zee.execution.executors.BuildPollingService
import com.github.mr3zee.execution.executors.QueueTimeoutException
import com.github.mr3zee.model.SubBuild
import com.github.mr3zee.model.SubBuildStatus
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.*
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class BuildPollingServiceTest {

    private fun mockClient(handler: MockRequestHandler): HttpClient {
        return HttpClient(MockEngine(handler)) {
            install(ContentNegotiation) {
                json(AppJson)
            }
        }
    }

    // --- TeamCity Polling ---

    @Test
    fun `TC poll transitions from queued to running to finished`() = runBlocking {
        var pollCount = 0

        val client = mockClient { request ->
            val url = request.url.toString()
            if (url.contains("/app/rest/builds/id:42")) {
                pollCount++
                val responseBody = when (pollCount) {
                    1 -> """{"state":"queued","status":null}"""
                    2 -> """{"state":"running","status":null}"""
                    else -> """{"state":"finished","status":"SUCCESS","number":"build-42"}"""
                }
                respond(
                    responseBody,
                    HttpStatusCode.OK,
                    headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                )
            } else {
                respond("{}", HttpStatusCode.OK)
            }
        }

        val service = BuildPollingService(client)
        val statesObserved = mutableListOf<String>()

        val result = withTimeoutOrNull(30_000.milliseconds) {
            service.pollTeamCityBuild(
                serverUrl = "https://tc.example.com",
                token = "test-token",
                buildId = "42",
                intervalSeconds = 1,
                onUpdate = { state, _ -> statesObserved.add(state) },
            )
        } ?: error("TC poll timed out")

        assertEquals("build-42", result["buildNumber"])
        assertEquals("SUCCESS", result["buildStatus"])
        assertTrue(result["buildUrl"]?.contains("tc.example.com") == true)
        assertTrue(statesObserved.contains("queued"), "Should have observed queued state")
        assertTrue(statesObserved.contains("running"), "Should have observed running state")
        assertTrue(statesObserved.contains("finished"), "Should have observed finished state")
    }

    // --- GitHub Polling ---

    @Test
    fun `GH poll transitions from queued to in_progress to completed`() = runBlocking {
        var pollCount = 0

        val client = mockClient { request ->
            val url = request.url.toString()
            if (url.contains("/actions/runs/789")) {
                pollCount++
                val responseBody = when (pollCount) {
                    1 -> """{"status":"queued","conclusion":null,"html_url":"https://github.com/o/r/actions/runs/789"}"""
                    2 -> """{"status":"in_progress","conclusion":null,"html_url":"https://github.com/o/r/actions/runs/789"}"""
                    else -> """{"status":"completed","conclusion":"success","html_url":"https://github.com/o/r/actions/runs/789"}"""
                }
                respond(
                    responseBody,
                    HttpStatusCode.OK,
                    headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                )
            } else {
                respond("{}", HttpStatusCode.OK)
            }
        }

        val service = BuildPollingService(client)
        val statusesObserved = mutableListOf<String>()

        val result = withTimeoutOrNull(30_000.milliseconds) {
            service.pollGitHubRun(
                owner = "o",
                repo = "r",
                token = "ghp_test",
                runId = "789",
                intervalSeconds = 1,
                onUpdate = { status, _ -> statusesObserved.add(status) },
            )
        } ?: error("GH poll timed out")

        assertEquals("789", result["runId"])
        assertEquals("success", result["runStatus"])
        assertTrue(result["runUrl"]?.contains("actions/runs/789") == true)
        assertTrue(statusesObserved.contains("queued"), "Should have observed queued status")
        assertTrue(statusesObserved.contains("in_progress"), "Should have observed in_progress status")
        assertTrue(statusesObserved.contains("completed"), "Should have observed completed status")
    }

    // --- Sub-build discovery ---

    @Test
    fun `sub-build discovery returns SubBuilds with dependency levels`() = runBlocking {
        val client = mockClient { request ->
            val url = request.url.toString()
            if (url.contains("/app/rest/builds?locator=snapshotDependency")) {
                respond(
                    """{
                        "build": [
                            {"id":"100","state":"running","status":null,"buildType":{"name":"Compile"}},
                            {"id":"101","state":"queued","status":null,"buildType":{"name":"Test"}},
                            {"id":"42","state":"running","status":null,"buildType":{"name":"Composite"}}
                        ]
                    }""",
                    HttpStatusCode.OK,
                    headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                )
            } else {
                respond("{}", HttpStatusCode.OK)
            }
        }

        val service = BuildPollingService(client)

        val subBuilds = service.discoverTeamCitySubBuilds(
            serverUrl = "https://tc.example.com",
            token = "test-token",
            topBuildId = "42",
        )

        // Should have 2 sub-builds (the top build "42" is excluded)
        assertEquals(2, subBuilds.size)
        assertEquals("100", subBuilds[0].id)
        assertEquals("Compile", subBuilds[0].name)
        assertEquals(SubBuildStatus.RUNNING, subBuilds[0].status)
        assertEquals(0, subBuilds[0].dependencyLevel)

        assertEquals("101", subBuilds[1].id)
        assertEquals("Test", subBuilds[1].name)
        assertEquals(SubBuildStatus.QUEUED, subBuilds[1].status)
        assertEquals(1, subBuilds[1].dependencyLevel)
    }

    // --- GitHub rate limit backoff ---

    @Test
    fun `GH poll backs off on low rate limit`() = runBlocking {
        var pollCount = 0

        val client = mockClient { request ->
            val url = request.url.toString()
            if (url.contains("/actions/runs/789")) {
                pollCount++
                if (pollCount == 1) {
                    // First response: low rate limit, should cause backoff
                    respond(
                        """{"status":"in_progress","conclusion":null,"html_url":"https://github.com/o/r/actions/runs/789"}""",
                        HttpStatusCode.OK,
                        headersOf(
                            HttpHeaders.ContentType to listOf(ContentType.Application.Json.toString()),
                            "X-RateLimit-Remaining" to listOf("0"),
                        ),
                    )
                } else {
                    // Subsequent response: completed
                    respond(
                        """{"status":"completed","conclusion":"success","html_url":"https://github.com/o/r/actions/runs/789"}""",
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                    )
                }
            } else {
                respond("{}", HttpStatusCode.OK)
            }
        }

        val service = BuildPollingService(client)

        val result = withTimeoutOrNull(30_000.milliseconds) {
            service.pollGitHubRun(
                owner = "o",
                repo = "r",
                token = "ghp_test",
                runId = "789",
                intervalSeconds = 1,
            )
        } ?: error("GH poll timed out")

        assertEquals("success", result["runStatus"])
        // The rate limit backoff should have caused at least 2 polls
        assertTrue(pollCount >= 2, "Should have polled at least twice (once with rate limit, once completed)")
    }

    // --- GitHub job discovery ---

    @Test
    fun `GH job discovery returns jobs with correct status mapping`() = runBlocking {
        val client = mockClient { request ->
            val url = request.url.toString()
            if (url.contains("/actions/runs/789/jobs")) {
                respond(
                    """{"total_count":11,"jobs":[
                        {"id":1,"name":"build","status":"completed","conclusion":"success","html_url":"https://github.com/o/r/actions/runs/789/jobs/1","started_at":"2025-03-17T10:00:00Z","completed_at":"2025-03-17T10:05:00Z"},
                        {"id":2,"name":"test","status":"completed","conclusion":"failure","html_url":"https://github.com/o/r/actions/runs/789/jobs/2","started_at":"2025-03-17T10:00:00Z","completed_at":"2025-03-17T10:03:00Z"},
                        {"id":3,"name":"lint","status":"in_progress","conclusion":null,"html_url":"https://github.com/o/r/actions/runs/789/jobs/3","started_at":"2025-03-17T10:00:00Z","completed_at":null},
                        {"id":4,"name":"deploy","status":"queued","conclusion":null,"html_url":"https://github.com/o/r/actions/runs/789/jobs/4","started_at":null,"completed_at":null},
                        {"id":5,"name":"skip-check","status":"completed","conclusion":"skipped","html_url":"https://github.com/o/r/actions/runs/789/jobs/5","started_at":"2025-03-17T10:00:00Z","completed_at":"2025-03-17T10:00:01Z"},
                        {"id":6,"name":"timeout-job","status":"completed","conclusion":"timed_out","html_url":"https://github.com/o/r/actions/runs/789/jobs/6","started_at":"2025-03-17T10:00:00Z","completed_at":"2025-03-17T10:30:00Z"},
                        {"id":7,"name":"neutral-check","status":"completed","conclusion":"neutral","html_url":"https://github.com/o/r/actions/runs/789/jobs/7","started_at":"2025-03-17T10:00:00Z","completed_at":"2025-03-17T10:00:02Z"},
                        {"id":8,"name":"cancel-job","status":"completed","conclusion":"cancelled","html_url":"https://github.com/o/r/actions/runs/789/jobs/8","started_at":"2025-03-17T10:00:00Z","completed_at":"2025-03-17T10:01:00Z"},
                        {"id":9,"name":"stale-job","status":"completed","conclusion":"stale","html_url":"https://github.com/o/r/actions/runs/789/jobs/9","started_at":"2025-03-17T10:00:00Z","completed_at":"2025-03-17T10:00:05Z"},
                        {"id":10,"name":"approval-job","status":"completed","conclusion":"action_required","html_url":"https://github.com/o/r/actions/runs/789/jobs/10","started_at":"2025-03-17T10:00:00Z","completed_at":"2025-03-17T10:02:00Z"},
                        {"id":11,"name":"wait-job","status":"waiting","conclusion":null,"html_url":"https://github.com/o/r/actions/runs/789/jobs/11","started_at":null,"completed_at":null}
                    ]}""",
                    HttpStatusCode.OK,
                    headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                )
            } else {
                respond("{}", HttpStatusCode.OK)
            }
        }

        val service = BuildPollingService(client)

        val jobs = service.discoverGitHubJobs(
            owner = "o",
            repo = "r",
            token = "ghp_test",
            runId = "789",
        )

        assertEquals(11, jobs.size)

        // Job 1: build — completed/success -> SUCCEEDED, duration = 300s
        assertEquals("build", jobs[0].name)
        assertEquals(SubBuildStatus.SUCCEEDED, jobs[0].status)
        assertEquals(300L, jobs[0].durationSeconds)
        assertEquals("https://github.com/o/r/actions/runs/789/jobs/1", jobs[0].buildUrl)
        assertEquals(0, jobs[0].dependencyLevel)

        // Job 2: test — completed/failure -> FAILED, duration = 180s
        assertEquals("test", jobs[1].name)
        assertEquals(SubBuildStatus.FAILED, jobs[1].status)
        assertEquals(180L, jobs[1].durationSeconds)

        // Job 3: lint — in_progress -> RUNNING, no completed_at -> null duration
        assertEquals("lint", jobs[2].name)
        assertEquals(SubBuildStatus.RUNNING, jobs[2].status)
        assertNull(jobs[2].durationSeconds)

        // Job 4: deploy — queued -> QUEUED, no started_at -> null duration
        assertEquals("deploy", jobs[3].name)
        assertEquals(SubBuildStatus.QUEUED, jobs[3].status)
        assertNull(jobs[3].durationSeconds)

        // Job 5: skip-check — completed/skipped -> CANCELLED
        assertEquals("skip-check", jobs[4].name)
        assertEquals(SubBuildStatus.CANCELLED, jobs[4].status)
        assertEquals(1L, jobs[4].durationSeconds)

        // Job 6: timeout-job — completed/timed_out -> FAILED
        assertEquals("timeout-job", jobs[5].name)
        assertEquals(SubBuildStatus.FAILED, jobs[5].status)
        assertEquals(1800L, jobs[5].durationSeconds)

        // Job 7: neutral-check — completed/neutral -> SUCCEEDED
        assertEquals("neutral-check", jobs[6].name)
        assertEquals(SubBuildStatus.SUCCEEDED, jobs[6].status)

        // Job 8: cancel-job — completed/cancelled -> CANCELLED
        assertEquals("cancel-job", jobs[7].name)
        assertEquals(SubBuildStatus.CANCELLED, jobs[7].status)

        // Job 9: stale-job — completed/stale -> CANCELLED
        assertEquals("stale-job", jobs[8].name)
        assertEquals(SubBuildStatus.CANCELLED, jobs[8].status)

        // Job 10: approval-job — completed/action_required -> QUEUED
        assertEquals("approval-job", jobs[9].name)
        assertEquals(SubBuildStatus.QUEUED, jobs[9].status)

        // Job 11: wait-job — status=waiting -> QUEUED
        assertEquals("wait-job", jobs[10].name)
        assertEquals(SubBuildStatus.QUEUED, jobs[10].status)
        assertNull(jobs[10].durationSeconds)
    }

    @Test
    fun `GH job discovery with empty jobs list returns empty`() = runBlocking {
        val client = mockClient { request ->
            val url = request.url.toString()
            if (url.contains("/actions/runs/789/jobs")) {
                respond(
                    """{"total_count":0,"jobs":[]}""",
                    HttpStatusCode.OK,
                    headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                )
            } else {
                respond("{}", HttpStatusCode.OK)
            }
        }

        val service = BuildPollingService(client)

        val jobs = service.discoverGitHubJobs(
            owner = "o",
            repo = "r",
            token = "ghp_test",
            runId = "789",
        )

        assertTrue(jobs.isEmpty(), "Should return empty list when no jobs")
    }

    @Test
    fun `GH job discovery failure returns empty list`() = runBlocking {
        val client = mockClient { request ->
            val url = request.url.toString()
            if (url.contains("/actions/runs/789/jobs")) {
                respond(
                    "Internal Server Error",
                    HttpStatusCode.InternalServerError,
                )
            } else {
                respond("{}", HttpStatusCode.OK)
            }
        }

        val service = BuildPollingService(client)

        val jobs = service.discoverGitHubJobs(
            owner = "o",
            repo = "r",
            token = "ghp_test",
            runId = "789",
        )

        assertTrue(jobs.isEmpty(), "Should return empty list on server error")
    }

    @Test
    fun `GH job discovery rate limited returns empty list`() = runBlocking {
        val client = mockClient { request ->
            val url = request.url.toString()
            if (url.contains("/actions/runs/789/jobs")) {
                respond(
                    """{"message":"API rate limit exceeded"}""",
                    HttpStatusCode.TooManyRequests,
                )
            } else {
                respond("{}", HttpStatusCode.OK)
            }
        }

        val service = BuildPollingService(client)

        val jobs = service.discoverGitHubJobs(
            owner = "o",
            repo = "r",
            token = "ghp_test",
            runId = "789",
        )

        assertTrue(jobs.isEmpty(), "Should return empty list when rate limited")
    }

    // --- Queue timeout ---

    @Test
    fun `TC poll throws QueueTimeoutException when build stays queued`() = runBlocking {
        val client = mockClient { request ->
            val url = request.url.toString()
            if (url.contains("/app/rest/builds/id:42")) {
                respond(
                    """{"state":"queued","status":null}""",
                    HttpStatusCode.OK,
                    headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                )
            } else {
                respond("{}", HttpStatusCode.OK)
            }
        }

        val service = BuildPollingService(client)

        val exception = assertFailsWith<QueueTimeoutException> {
            service.pollTeamCityBuild(
                serverUrl = "https://tc.example.com",
                token = "test-token",
                buildId = "42",
                intervalSeconds = 1,
                queueTimeout = 3.seconds,
            )
        }

        assertTrue(exception.message?.contains("queue") == true)
        assertTrue(exception.message?.contains("42") == true)
    }

    @Test
    fun `GH poll throws QueueTimeoutException when run stays queued`() = runBlocking {
        val client = mockClient { request ->
            val url = request.url.toString()
            if (url.contains("/actions/runs/789")) {
                respond(
                    """{"status":"queued","conclusion":null,"html_url":"https://github.com/o/r/actions/runs/789"}""",
                    HttpStatusCode.OK,
                    headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                )
            } else {
                respond("{}", HttpStatusCode.OK)
            }
        }

        val service = BuildPollingService(client)

        val exception = assertFailsWith<QueueTimeoutException> {
            service.pollGitHubRun(
                owner = "o",
                repo = "r",
                token = "ghp_test",
                runId = "789",
                intervalSeconds = 1,
                queueTimeout = 3.seconds,
            )
        }

        assertTrue(exception.message?.contains("queue") == true)
        assertTrue(exception.message?.contains("789") == true)
    }

    @Test
    fun `TC queue timeout resets once build starts running`() = runBlocking {
        var pollCount = 0

        val client = mockClient { request ->
            val url = request.url.toString()
            if (url.contains("/app/rest/builds/id:42")) {
                pollCount++
                val responseBody = when {
                    pollCount <= 2 -> """{"state":"queued","status":null}"""
                    pollCount <= 5 -> """{"state":"running","status":null}"""
                    else -> """{"state":"finished","status":"SUCCESS","number":"build-42"}"""
                }
                respond(
                    responseBody,
                    HttpStatusCode.OK,
                    headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                )
            } else {
                respond("{}", HttpStatusCode.OK)
            }
        }

        val service = BuildPollingService(client)

        val result = withTimeoutOrNull(30_000.milliseconds) {
            service.pollTeamCityBuild(
                serverUrl = "https://tc.example.com",
                token = "test-token",
                buildId = "42",
                intervalSeconds = 1,
                queueTimeout = 30.seconds,
            )
        } ?: error("TC poll timed out")

        assertEquals("SUCCESS", result["buildStatus"])
        assertTrue(pollCount >= 6, "Should have polled through queued, running, and finished states")
    }

    // --- Sub-build discovery failure ---

    @Test
    fun `sub-build discovery failure returns empty list`() = runBlocking {
        val client = mockClient { request ->
            val url = request.url.toString()
            if (url.contains("/app/rest/builds?locator=snapshotDependency")) {
                respond(
                    "Internal Server Error",
                    HttpStatusCode.InternalServerError,
                )
            } else {
                respond("{}", HttpStatusCode.OK)
            }
        }

        val service = BuildPollingService(client)

        val subBuilds = service.discoverTeamCitySubBuilds(
            serverUrl = "https://tc.example.com",
            token = "test-token",
            topBuildId = "42",
        )

        assertTrue(subBuilds.isEmpty(), "Should return empty list on failure")
    }
}
