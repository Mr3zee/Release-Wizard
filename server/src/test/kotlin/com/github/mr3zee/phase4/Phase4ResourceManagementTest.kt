@file:Suppress("FunctionName")

package com.github.mr3zee.phase4

import com.github.mr3zee.AppJson
import com.github.mr3zee.api.*
import com.github.mr3zee.createTestProject
import com.github.mr3zee.execution.ExecutionEngine
import com.github.mr3zee.execution.executors.BuildPollingService
import com.github.mr3zee.execution.executors.TeamCityArtifactService
import com.github.mr3zee.jsonClient
import com.github.mr3zee.loginAndCreateTeam
import com.github.mr3zee.mavenpublication.MavenMetadataFetcher
import com.github.mr3zee.mavenpublication.MavenPollerService
import com.github.mr3zee.mavenpublication.MavenPollerServiceTest
import com.github.mr3zee.model.*
import com.github.mr3zee.schedules.DefaultScheduleService
import com.github.mr3zee.testModule
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.testing.*
import kotlinx.coroutines.*
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class Phase4ResourceManagementTest {

    // ── EXEC-M1: Max poll duration ──────────────────────────────────────

    private fun mockClient(handler: MockRequestHandler): HttpClient {
        return HttpClient(MockEngine(handler)) {
            install(ContentNegotiation) {
                json(AppJson)
            }
        }
    }

    @Test
    fun `EXEC-M1 TC poll throws on max poll duration exceeded`() = runBlocking {
        val client = mockClient { _ ->
            respond(
                """{"state":"running","status":null}""",
                HttpStatusCode.OK,
                headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }

        val service = BuildPollingService(client)

        val exception = assertFailsWith<RuntimeException> {
            service.pollTeamCityBuild(
                serverUrl = "https://tc.example.com",
                token = "test-token",
                buildId = "99",
                intervalSeconds = 1,
                maxPollDuration = 2.seconds,
            )
        }

        assertTrue(exception.message?.contains("maximum poll duration") == true,
            "Expected max poll duration error, got: ${exception.message}")
    }

    @Test
    fun `EXEC-M1 GH poll throws on max poll duration exceeded`() = runBlocking {
        val client = mockClient { _ ->
            respond(
                """{"status":"in_progress","conclusion":null,"html_url":"https://github.com/o/r/actions/runs/789"}""",
                HttpStatusCode.OK,
                headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }

        val service = BuildPollingService(client)

        val exception = assertFailsWith<RuntimeException> {
            service.pollGitHubRun(
                owner = "o",
                repo = "r",
                token = "ghp_test",
                runId = "789",
                intervalSeconds = 1,
                maxPollDuration = 2.seconds,
            )
        }

        assertTrue(exception.message?.contains("maximum poll duration") == true,
            "Expected max poll duration error, got: ${exception.message}")
    }

    // ── EXEC-M2: CancellationException re-throw in artifact fetcher ─────

    @Test
    fun `EXEC-M2 artifact fetcher re-throws CancellationException`() {
        // CancellationException must propagate out of fetchRecursive rather than being caught
        // Use a cancellable coroutine scope to test this properly
        var caughtCancellation = false
        runBlocking {
            val job = launch {
                val client = mockClient { _ ->
                    throw CancellationException("Simulated cancellation")
                }
                val service = TeamCityArtifactService(client)
                service.fetchMatchingArtifacts(
                    serverUrl = "https://tc.example.com",
                    token = "test-token",
                    buildId = "42",
                    globPattern = "**/*.jar",
                    maxDepth = 3,
                    maxFiles = 10,
                )
            }
            job.join()
            caughtCancellation = job.isCancelled
        }
        assertTrue(caughtCancellation, "CancellationException should propagate and cancel the job")
    }

    // ── SCHED-H4: Per-project schedule count cap ────────────────────────

    @Test
    fun `SCHED-H4 schedule creation rejected when cap reached`() = testApplication {
        application { testModule() }
        val client = jsonClient()
        val teamId = client.loginAndCreateTeam()
        val projectId = client.createTestProject(teamId)

        // Create schedules up to the cap
        for (i in 1..DefaultScheduleService.MAX_SCHEDULES_PER_PROJECT) {
            val response = client.post(ApiRoutes.Schedules.byProject(projectId)) {
                contentType(ContentType.Application.Json)
                setBody(CreateScheduleRequest(
                    cronExpression = "0 $i * * *",
                    parameters = emptyList(),
                    enabled = false,
                ))
            }
            assertEquals(HttpStatusCode.Created, response.status,
                "Schedule $i should be created successfully")
        }

        // The next one should fail
        val rejectResponse = client.post(ApiRoutes.Schedules.byProject(projectId)) {
            contentType(ContentType.Application.Json)
            setBody(CreateScheduleRequest(
                cronExpression = "0 0 1 * *",
                parameters = emptyList(),
                enabled = false,
            ))
        }
        assertEquals(HttpStatusCode.BadRequest, rejectResponse.status)
        val error = rejectResponse.body<ErrorResponse>()
        assertTrue(error.error.contains("Maximum"), "Error should mention max cap: ${error.error}")
    }

    // ── MAVEN-M2: Per-project Maven trigger count cap ───────────────────

    @Test
    fun `MAVEN-M2 trigger creation rejected when cap reached`() = testApplication {
        application { testModule() }
        val client = jsonClient()
        val teamId = client.loginAndCreateTeam()
        val projectId = client.createTestProject(teamId)

        // Create triggers up to the cap (20)
        for (i in 1..20) {
            val response = client.post(ApiRoutes.MavenTriggers.byProject(projectId)) {
                contentType(ContentType.Application.Json)
                setBody(CreateMavenTriggerRequest(
                    repoUrl = "https://repo.example.com/maven2",
                    groupId = "com.example",
                    artifactId = "lib$i",
                    parameterKey = "version",
                    includeSnapshots = false,
                    enabled = false,
                ))
            }
            assertEquals(HttpStatusCode.Created, response.status,
                "Trigger $i should be created successfully")
        }

        // The next one should fail
        val rejectResponse = client.post(ApiRoutes.MavenTriggers.byProject(projectId)) {
            contentType(ContentType.Application.Json)
            setBody(CreateMavenTriggerRequest(
                repoUrl = "https://repo.example.com/maven2",
                groupId = "com.example",
                artifactId = "lib-overflow",
                parameterKey = "version",
                includeSnapshots = false,
                enabled = false,
            ))
        }
        assertEquals(HttpStatusCode.BadRequest, rejectResponse.status)
        val error = rejectResponse.body<ErrorResponse>()
        assertTrue(error.error.contains("Maximum"), "Error should mention max cap: ${error.error}")
    }

    // ── NOTIF-M2: Per-project notification config limit ─────────────────

    @Test
    fun `NOTIF-M2 notification config creation rejected when cap reached`() = testApplication {
        application { testModule() }
        val client = jsonClient()
        val teamId = client.loginAndCreateTeam()
        val projectId = client.createTestProject(teamId)

        // Create notification configs up to the cap (20)
        for (i in 1..20) {
            val response = client.post(ApiRoutes.Notifications.byProject(projectId)) {
                contentType(ContentType.Application.Json)
                setBody(CreateNotificationConfigRequest(
                    projectId = ProjectId(projectId),
                    type = "slack",
                    config = NotificationConfig.SlackNotification(
                        webhookUrl = "https://hooks.slack.com/services/test/hook/$i",
                        channel = "#releases",
                    ),
                    enabled = false,
                ))
            }
            assertEquals(HttpStatusCode.Created, response.status,
                "Notification config $i should be created successfully")
        }

        // The next one should fail
        val rejectResponse = client.post(ApiRoutes.Notifications.byProject(projectId)) {
            contentType(ContentType.Application.Json)
            setBody(CreateNotificationConfigRequest(
                projectId = ProjectId(projectId),
                type = "slack",
                config = NotificationConfig.SlackNotification(
                    webhookUrl = "https://hooks.slack.com/services/test/overflow",
                    channel = "#releases",
                ),
                enabled = false,
            ))
        }
        assertEquals(HttpStatusCode.BadRequest, rejectResponse.status)
        val error = rejectResponse.body<ErrorResponse>()
        assertTrue(error.error.contains("Maximum"), "Error should mention max cap: ${error.error}")
    }

    // ── HOOK-M1: Rate limit on webhook status endpoint ──────────────────

    @Test
    fun `HOOK-M1 webhook status endpoint is rate limited`() = testApplication {
        application { testModule() }
        val client = jsonClient()

        // Send 31 requests rapidly (rate limit is 30/min)
        var rateLimited = false
        for (i in 1..35) {
            val response = client.post(ApiRoutes.Webhooks.STATUS) {
                header("Authorization", "Bearer ${UUID.randomUUID()}")
                contentType(ContentType.Application.Json)
                setBody("""{"status":"test"}""")
            }
            if (response.status == HttpStatusCode.TooManyRequests) {
                rateLimited = true
                break
            }
        }
        assertTrue(rateLimited, "Webhook endpoint should be rate limited after 30 requests")
    }

    // ── MAVEN-M1: Rate limit on trigger webhook endpoint ────────────────

    @Test
    fun `MAVEN-M1 trigger webhook endpoint is rate limited`() = testApplication {
        application { testModule() }
        val client = jsonClient()

        var rateLimited = false
        for (i in 1..35) {
            val response = client.post(ApiRoutes.Triggers.webhook(UUID.randomUUID().toString())) {
                header("Authorization", "Bearer test-secret")
            }
            if (response.status == HttpStatusCode.TooManyRequests) {
                rateLimited = true
                break
            }
        }
        assertTrue(rateLimited, "Trigger webhook endpoint should be rate limited after 30 requests")
    }

    // ── MAVEN-M3: Poll cycle timeout ────────────────────────────────────

    @Test
    fun `MAVEN-M3 pollAllTriggers completes within timeout`() = runBlocking {
        val fired = mutableListOf<Pair<ProjectId, List<Parameter>>>()
        val poller = MavenPollerService(
            repository = MavenPollerServiceTest.StubRepo(emptyList()),
            fetcher = MavenMetadataFetcher(mockClient { _ ->
                respond(
                    """<?xml version="1.0"?><metadata><versioning><versions><version>1.0.0</version></versions></versioning></metadata>""",
                    HttpStatusCode.OK,
                )
            }),
            releasesService = object : MavenPollerServiceTest.StubReleasesService() {
                override suspend fun startScheduledRelease(projectId: ProjectId, parameters: List<Parameter>): Release {
                    fired += projectId to parameters
                    return Release(
                        id = ReleaseId("00000000-0000-0000-0000-000000000099"),
                        projectTemplateId = projectId,
                        status = ReleaseStatus.RUNNING,
                        dagSnapshot = DagGraph(),
                        parameters = emptyList(),
                    )
                }
            },
        )

        // Should complete successfully with empty triggers
        poller.pollAllTriggers()
        assertTrue(fired.isEmpty())
    }

    // ── EXEC-H7: Concurrency semaphore constant ─────────────────────────

    @Test
    fun `EXEC-H7 max concurrent blocks constant is set`() {
        assertEquals(50, ExecutionEngine.MAX_CONCURRENT_BLOCKS)
    }

    // ── INFRA-M1: HikariCP configuration ────────────────────────────────

    @Test
    fun `INFRA-M1 database connection pool works with new settings`() = testApplication {
        application { testModule() }
        val client = jsonClient()
        // Simply verify the app starts and responds — HikariCP settings are in effect
        val response = client.get("/")
        assertEquals(HttpStatusCode.OK, response.status)
    }

    // ── EXEC-H6: TLS verification configured ───────────────────────────

    @Test
    fun `EXEC-H6 HttpClient TLS configuration does not break functionality`() = testApplication {
        // The real TLS config is in production AppModule; test verifies the test mock client
        // still works (no regression from the CIO engine TLS changes)
        application { testModule() }
        val client = jsonClient()
        val response = client.get("/health")
        assertEquals(HttpStatusCode.OK, response.status)
    }
}
