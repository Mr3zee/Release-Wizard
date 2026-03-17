package com.github.mr3zee.execution

import com.github.mr3zee.AppJson
import com.github.mr3zee.execution.executors.BuildPollingService
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
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

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
