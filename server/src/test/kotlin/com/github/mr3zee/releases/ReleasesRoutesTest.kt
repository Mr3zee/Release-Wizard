package com.github.mr3zee.releases

import com.github.mr3zee.api.*
import com.github.mr3zee.jsonClient
import com.github.mr3zee.testModule
import com.github.mr3zee.model.*
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ReleasesRoutesTest {

    private suspend fun HttpClient.login() {
        post(ApiRoutes.Auth.LOGIN) {
            contentType(ContentType.Application.Json)
            setBody(LoginRequest(username = "admin", password = "admin"))
        }
    }

    private suspend fun HttpClient.createTestProject(
        name: String = "Test Project",
        blocks: List<Block> = listOf(
            Block.ActionBlock(
                id = BlockId("block-a"),
                name = "Build",
                type = BlockType.TEAMCITY_BUILD,
            ),
        ),
        edges: List<Edge> = emptyList(),
        parameters: List<Parameter> = emptyList(),
    ): ProjectTemplate {
        val response = post(ApiRoutes.Projects.BASE) {
            contentType(ContentType.Application.Json)
            setBody(
                CreateProjectRequest(
                    name = name,
                    dagGraph = DagGraph(blocks = blocks, edges = edges),
                    parameters = parameters,
                )
            )
        }
        return response.body<ProjectResponse>().project
    }

    private suspend fun HttpClient.startAndAwaitRelease(
        projectTemplateId: ProjectId,
        parameters: List<Parameter> = emptyList(),
    ): ReleaseResponse {
        val startResponse = post(ApiRoutes.Releases.BASE) {
            contentType(ContentType.Application.Json)
            setBody(CreateReleaseRequest(projectTemplateId = projectTemplateId, parameters = parameters))
        }
        assertEquals(HttpStatusCode.Created, startResponse.status)
        val created = startResponse.body<ReleaseResponse>()

        // Await execution completion
        val awaitResponse = post(ApiRoutes.Releases.await(created.release.id.value))
        assertEquals(HttpStatusCode.OK, awaitResponse.status)
        return awaitResponse.body<ReleaseResponse>()
    }

    @Test
    fun `list releases returns empty list initially`() = testApplication {
        application { testModule() }
        val client = jsonClient()
        client.login()

        val response = client.get(ApiRoutes.Releases.BASE)
        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.body<ReleaseListResponse>()
        assertTrue(body.releases.isEmpty())
    }

    @Test
    fun `start release and get it`() = testApplication {
        application { testModule() }
        val client = jsonClient()
        client.login()

        val project = client.createTestProject()
        val result = client.startAndAwaitRelease(project.id)

        assertEquals(project.id, result.release.projectTemplateId)
        assertEquals(ReleaseStatus.SUCCEEDED, result.release.status)
        assertTrue(result.blockExecutions.isNotEmpty())

        // Verify GET returns the same
        val getResponse = client.get(ApiRoutes.Releases.byId(result.release.id.value))
        assertEquals(HttpStatusCode.OK, getResponse.status)
        val fetched = getResponse.body<ReleaseResponse>()
        assertEquals(result.release.id, fetched.release.id)
        assertEquals(ReleaseStatus.SUCCEEDED, fetched.release.status)
    }

    @Test
    fun `start release with nonexistent project returns 400`() = testApplication {
        application { testModule() }
        val client = jsonClient()
        client.login()

        val response = client.post(ApiRoutes.Releases.BASE) {
            contentType(ContentType.Application.Json)
            setBody(CreateReleaseRequest(projectTemplateId = ProjectId("00000000-0000-0000-0000-000000000000")))
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `start release with empty DAG returns 400`() = testApplication {
        application { testModule() }
        val client = jsonClient()
        client.login()

        val project = client.createTestProject(blocks = emptyList())

        val response = client.post(ApiRoutes.Releases.BASE) {
            contentType(ContentType.Application.Json)
            setBody(CreateReleaseRequest(projectTemplateId = project.id))
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `sequential DAG executes in correct order`() = testApplication {
        application { testModule() }
        val client = jsonClient()
        client.login()

        // A -> B -> C
        val blocks = listOf(
            Block.ActionBlock(id = BlockId("a"), name = "A", type = BlockType.TEAMCITY_BUILD),
            Block.ActionBlock(id = BlockId("b"), name = "B", type = BlockType.GITHUB_ACTION),
            Block.ActionBlock(id = BlockId("c"), name = "C", type = BlockType.SLACK_MESSAGE),
        )
        val edges = listOf(
            Edge(fromBlockId = BlockId("a"), toBlockId = BlockId("b")),
            Edge(fromBlockId = BlockId("b"), toBlockId = BlockId("c")),
        )
        val project = client.createTestProject(blocks = blocks, edges = edges)
        val result = client.startAndAwaitRelease(project.id)

        assertEquals(ReleaseStatus.SUCCEEDED, result.release.status)
        assertEquals(3, result.blockExecutions.size)
        assertTrue(result.blockExecutions.all { it.status == BlockStatus.SUCCEEDED })
    }

    @Test
    fun `diamond DAG executes correctly`() = testApplication {
        application { testModule() }
        val client = jsonClient()
        client.login()

        // A -> B, A -> C, B -> D, C -> D
        val blocks = listOf(
            Block.ActionBlock(id = BlockId("a"), name = "A", type = BlockType.TEAMCITY_BUILD),
            Block.ActionBlock(id = BlockId("b"), name = "B", type = BlockType.GITHUB_ACTION),
            Block.ActionBlock(id = BlockId("c"), name = "C", type = BlockType.SLACK_MESSAGE),
            Block.ActionBlock(id = BlockId("d"), name = "D", type = BlockType.GITHUB_PUBLICATION),
        )
        val edges = listOf(
            Edge(fromBlockId = BlockId("a"), toBlockId = BlockId("b")),
            Edge(fromBlockId = BlockId("a"), toBlockId = BlockId("c")),
            Edge(fromBlockId = BlockId("b"), toBlockId = BlockId("d")),
            Edge(fromBlockId = BlockId("c"), toBlockId = BlockId("d")),
        )
        val project = client.createTestProject(blocks = blocks, edges = edges)
        val result = client.startAndAwaitRelease(project.id)

        assertEquals(ReleaseStatus.SUCCEEDED, result.release.status)
        assertEquals(4, result.blockExecutions.size)
        assertTrue(result.blockExecutions.all { it.status == BlockStatus.SUCCEEDED })

        // D should have started after both B and C
        val dExec = result.blockExecutions.find { it.blockId == BlockId("d") }!!
        val bExec = result.blockExecutions.find { it.blockId == BlockId("b") }!!
        val cExec = result.blockExecutions.find { it.blockId == BlockId("c") }!!
        assertNotNull(dExec.startedAt)
        assertNotNull(bExec.finishedAt)
        assertNotNull(cExec.finishedAt)
        assertTrue(dExec.startedAt!! >= bExec.finishedAt!!)
        assertTrue(dExec.startedAt!! >= cExec.finishedAt!!)
    }

    @Test
    fun `user action block waits for approval`() = testApplication {
        application { testModule() }
        val client = jsonClient()
        client.login()

        val blocks = listOf(
            Block.ActionBlock(id = BlockId("action"), name = "Approve", type = BlockType.USER_ACTION),
        )
        val project = client.createTestProject(blocks = blocks)

        val created = client.post(ApiRoutes.Releases.BASE) {
            contentType(ContentType.Application.Json)
            setBody(CreateReleaseRequest(projectTemplateId = project.id))
        }.body<ReleaseResponse>()

        // Launch await in background — it will block until the release completes
        val awaitScope = CoroutineScope(Dispatchers.Default)
        val awaitDeferred = awaitScope.async {
            client.post(ApiRoutes.Releases.await(created.release.id.value))
                .body<ReleaseResponse>()
        }

        // Retry approval until the engine reaches WAITING_FOR_INPUT
        while (true) {
            val resp = client.post(ApiRoutes.Releases.approveBlock(created.release.id.value, "action")) {
                contentType(ContentType.Application.Json)
                setBody(ApproveBlockRequest(input = mapOf("approved" to "true")))
            }
            if (resp.status == HttpStatusCode.OK) break
            yield()
        }

        val result = awaitDeferred.await()
        assertEquals(ReleaseStatus.SUCCEEDED, result.release.status)
        awaitScope.cancel()
    }

    @Test
    fun `cancel running release`() = testApplication {
        application { testModule() }
        val client = jsonClient()
        client.login()

        val blocks = listOf(
            Block.ActionBlock(id = BlockId("action"), name = "Wait", type = BlockType.USER_ACTION),
        )
        val project = client.createTestProject(blocks = blocks)

        val created = client.post(ApiRoutes.Releases.BASE) {
            contentType(ContentType.Application.Json)
            setBody(CreateReleaseRequest(projectTemplateId = project.id))
        }.body<ReleaseResponse>()

        // cancelRelease calls cancelExecution which cancels and joins
        val cancelResponse = client.post(ApiRoutes.Releases.cancel(created.release.id.value))
        assertEquals(HttpStatusCode.OK, cancelResponse.status)

        val fetched = client.get(ApiRoutes.Releases.byId(created.release.id.value))
            .body<ReleaseResponse>()
        assertEquals(ReleaseStatus.CANCELLED, fetched.release.status)
    }

    @Test
    fun `get nonexistent release returns 404`() = testApplication {
        application { testModule() }
        val client = jsonClient()
        client.login()

        val response = client.get(ApiRoutes.Releases.byId("00000000-0000-0000-0000-000000000000"))
        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `unauthenticated release request returns 401`() = testApplication {
        application { testModule() }
        val client = jsonClient()

        val response = client.get(ApiRoutes.Releases.BASE)
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `release with parameters merges project and request params`() = testApplication {
        application { testModule() }
        val client = jsonClient()
        client.login()

        val blocks = listOf(
            Block.ActionBlock(id = BlockId("a"), name = "A", type = BlockType.TEAMCITY_BUILD),
        )
        val project = client.createTestProject(
            blocks = blocks,
            parameters = listOf(
                Parameter(key = "version", value = "1.0.0"),
                Parameter(key = "env", value = "staging"),
            ),
        )

        val result = client.startAndAwaitRelease(
            projectTemplateId = project.id,
            parameters = listOf(Parameter(key = "version", value = "2.0.0")),
        )

        val params = result.release.parameters
        assertEquals("2.0.0", params.find { it.key == "version" }?.value)
        assertEquals("staging", params.find { it.key == "env" }?.value)
    }

    @Test
    fun `block outputs are captured`() = testApplication {
        application { testModule() }
        val client = jsonClient()
        client.login()

        val blocks = listOf(
            Block.ActionBlock(id = BlockId("build"), name = "Build", type = BlockType.TEAMCITY_BUILD),
        )
        val project = client.createTestProject(blocks = blocks)
        val result = client.startAndAwaitRelease(project.id)

        val buildExec = result.blockExecutions.find { it.blockId == BlockId("build") }!!
        assertEquals(BlockStatus.SUCCEEDED, buildExec.status)
        assertTrue(buildExec.outputs.containsKey("buildNumber"))
        assertTrue(buildExec.outputs.containsKey("buildUrl"))
    }
}
