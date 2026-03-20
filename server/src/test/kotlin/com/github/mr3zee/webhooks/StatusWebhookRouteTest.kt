@file:Suppress("FunctionName")

package com.github.mr3zee.webhooks

import com.github.mr3zee.AppJson
import com.github.mr3zee.api.*
import com.github.mr3zee.jsonClient
import com.github.mr3zee.loginAndCreateTeam
import com.github.mr3zee.model.*
import com.github.mr3zee.releases.ReleasesRepository
import com.github.mr3zee.testModule
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.testing.*
import org.koin.ktor.ext.get
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class StatusWebhookRouteTest {

    private data class TestEnv(
        val client: HttpClient,
        val service: StatusWebhookService,
        val repo: ReleasesRepository,
        val teamId: TeamId,
    )

    private fun runTest(test: suspend TestEnv.() -> Unit) = testApplication {
        lateinit var service: StatusWebhookService
        lateinit var repo: ReleasesRepository
        application {
            testModule()
            service = this@application.get()
            repo = this@application.get()
        }
        // jsonClient triggers the application build, initializing the captured references
        val client = jsonClient()
        val teamId = client.loginAndCreateTeam()
        TestEnv(client, service, repo, teamId).test()
    }

    /**
     * Creates a release record directly via the repository (bypassing execution trigger),
     * inserts a block execution with the given status, and creates a status webhook token.
     */
    private suspend fun TestEnv.createBlockWithToken(
        status: BlockStatus = BlockStatus.RUNNING,
    ): Triple<UUID, ReleaseId, BlockId> {
        // Create a project first (needed as FK target for the release)
        val projectResp = client.post(ApiRoutes.Projects.BASE) {
            contentType(ContentType.Application.Json)
            setBody(CreateProjectRequest(
                name = "Webhook Test Project ${UUID.randomUUID().toString().take(8)}",
                teamId = teamId,
                dagGraph = DagGraph(
                    blocks = listOf(
                        Block.ActionBlock(
                            id = BlockId("test-block"),
                            name = "TC Build",
                            type = BlockType.TEAMCITY_BUILD,
                        )
                    ),
                ),
            ))
        }
        val project = projectResp.body<ProjectResponse>()

        // Create release directly via repo — does NOT trigger execution
        val release = repo.create(
            projectTemplateId = project.project.id,
            dagSnapshot = project.project.dagGraph,
            parameters = emptyList(),
            teamId = teamId.value,
        )
        val releaseId = release.id
        val blockId = BlockId("test-block")

        // Insert a block execution with the desired status
        repo.upsertBlockExecution(
            BlockExecution(blockId = blockId, releaseId = releaseId, status = status)
        )
        val token = service.createToken(releaseId, blockId)
        return Triple(token, releaseId, blockId)
    }

    @Test
    fun `POST status with valid token returns 200`() = runTest {
        val (token, releaseId, blockId) = createBlockWithToken()

        val response = client.post(ApiRoutes.Webhooks.STATUS) {
            header("Authorization", "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(AppJson.encodeToString(StatusUpdatePayload.serializer(), StatusUpdatePayload(status = "Building")))
        }
        assertEquals(HttpStatusCode.OK, response.status)

        val execution = repo.findBlockExecution(releaseId, blockId)
        assertNotNull(execution)
        assertNotNull(execution.webhookStatus)
        assertEquals("Building", execution.webhookStatus?.status)
    }

    @Test
    fun `POST status without Authorization header returns 404`() = runTest {
        val response = client.post(ApiRoutes.Webhooks.STATUS) {
            contentType(ContentType.Application.Json)
            setBody("""{"status":"test"}""")
        }
        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `POST status with malformed token returns 404`() = runTest {
        val response = client.post(ApiRoutes.Webhooks.STATUS) {
            header("Authorization", "Bearer not-a-uuid")
            contentType(ContentType.Application.Json)
            setBody("""{"status":"test"}""")
        }
        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `POST status with unknown token returns 404`() = runTest {
        val response = client.post(ApiRoutes.Webhooks.STATUS) {
            header("Authorization", "Bearer ${UUID.randomUUID()}")
            contentType(ContentType.Application.Json)
            setBody("""{"status":"test"}""")
        }
        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `POST status with empty status returns 400`() = runTest {
        val (token) = createBlockWithToken()

        val response = client.post(ApiRoutes.Webhooks.STATUS) {
            header("Authorization", "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody("""{"status":""}""")
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `POST status with invalid JSON returns 400`() = runTest {
        val response = client.post(ApiRoutes.Webhooks.STATUS) {
            header("Authorization", "Bearer ${UUID.randomUUID()}")
            contentType(ContentType.Application.Json)
            setBody("not json")
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `POST status to non-RUNNING block returns 410 Gone`() = runTest {
        val (token) = createBlockWithToken(status = BlockStatus.SUCCEEDED)

        val response = client.post(ApiRoutes.Webhooks.STATUS) {
            header("Authorization", "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody("""{"status":"Building"}""")
        }
        // HOOK-H2: Non-RUNNING blocks return 410 Gone instead of 404
        assertEquals(HttpStatusCode.Gone, response.status)
    }

    @Test
    fun `POST status with oversized payload returns 400`() = runTest {
        val response = client.post(ApiRoutes.Webhooks.STATUS) {
            header("Authorization", "Bearer ${UUID.randomUUID()}")
            contentType(ContentType.Application.Json)
            // Build a body larger than 8 KB (the size check happens before token validation)
            val oversizedBody = """{"status":"${"x".repeat(9000)}"}"""
            setBody(oversizedBody)
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `POST status overwrites previous status with new token`() = runTest {
        val (token1, releaseId, blockId) = createBlockWithToken()

        val response1 = client.post(ApiRoutes.Webhooks.STATUS) {
            header("Authorization", "Bearer $token1")
            contentType(ContentType.Application.Json)
            setBody("""{"status":"Step 1"}""")
        }
        assertEquals(HttpStatusCode.OK, response1.status)

        // HOOK-H1: Token is deactivated after first successful use, create a new one
        val token2 = service.createToken(releaseId, blockId)

        val response2 = client.post(ApiRoutes.Webhooks.STATUS) {
            header("Authorization", "Bearer $token2")
            contentType(ContentType.Application.Json)
            setBody("""{"status":"Step 2","description":"Testing phase"}""")
        }
        assertEquals(HttpStatusCode.OK, response2.status)

        val execution = repo.findBlockExecution(releaseId, blockId)
        assertNotNull(execution)
        assertEquals("Step 2", execution.webhookStatus?.status)
        assertEquals("Testing phase", execution.webhookStatus?.description)
    }
}
