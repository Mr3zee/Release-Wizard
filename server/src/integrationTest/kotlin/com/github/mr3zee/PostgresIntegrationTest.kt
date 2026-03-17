@file:Suppress("FunctionName")

package com.github.mr3zee

import com.github.mr3zee.api.*
import com.github.mr3zee.model.*
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.testing.*
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PostgresIntegrationTest {

    companion object {
        private lateinit var dbConfig: DatabaseConfig

        @JvmStatic
        @BeforeClass
        fun startContainer() {
            dbConfig = PostgresTestContainer.start()
        }

        @JvmStatic
        @AfterClass
        fun stopContainer() {
            PostgresTestContainer.stop()
        }
    }

    private fun Application.postgresTestModule() = testModule(dbConfig)

    private suspend fun HttpClient.createTestProject(
        name: String = "Test Project",
        teamId: TeamId,
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
                    teamId = teamId,
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

        val awaitResponse = post(ApiRoutes.Releases.await(created.release.id.value))
        assertEquals(HttpStatusCode.OK, awaitResponse.status)
        return awaitResponse.body<ReleaseResponse>()
    }

    @Test
    fun `create project, start release, and await completion against real Postgres`() = testApplication {
        application { postgresTestModule() }
        val client = jsonClient()
        val teamId = client.loginAndCreateTeam()

        // Create project with a sequential DAG: A -> B
        val blocks = listOf(
            Block.ActionBlock(id = BlockId("a"), name = "Build", type = BlockType.TEAMCITY_BUILD),
            Block.ActionBlock(id = BlockId("b"), name = "Notify", type = BlockType.SLACK_MESSAGE),
        )
        val edges = listOf(
            Edge(fromBlockId = BlockId("a"), toBlockId = BlockId("b")),
        )
        val project = client.createTestProject(
            name = "E2E Test Project",
            teamId = teamId,
            blocks = blocks,
            edges = edges,
        )
        assertEquals("E2E Test Project", project.name)

        // Start release and await completion
        val result = client.startAndAwaitRelease(project.id)
        assertEquals(ReleaseStatus.SUCCEEDED, result.release.status)
        assertEquals(2, result.blockExecutions.size)
        assertTrue(result.blockExecutions.all { it.status == BlockStatus.SUCCEEDED })

        // Verify the release is persisted and retrievable
        val getResponse = client.get(ApiRoutes.Releases.byId(result.release.id.value))
        assertEquals(HttpStatusCode.OK, getResponse.status)
        val fetched = getResponse.body<ReleaseResponse>()
        assertEquals(result.release.id, fetched.release.id)
        assertEquals(ReleaseStatus.SUCCEEDED, fetched.release.status)
    }

    @Test
    fun `create and delete project against real Postgres`() = testApplication {
        application { postgresTestModule() }
        val client = jsonClient()
        val teamId = client.loginAndCreateTeam()

        // Create a project
        val project = client.createTestProject(name = "To Delete", teamId = teamId)
        assertNotNull(project.id)
        assertEquals("To Delete", project.name)

        // Verify it exists
        val getResponse = client.get(ApiRoutes.Projects.byId(project.id.value))
        assertEquals(HttpStatusCode.OK, getResponse.status)

        // Delete it
        val deleteResponse = client.delete(ApiRoutes.Projects.byId(project.id.value))
        assertEquals(HttpStatusCode.NoContent, deleteResponse.status)

        // Verify it no longer exists
        val getAfterDelete = client.get(ApiRoutes.Projects.byId(project.id.value))
        assertEquals(HttpStatusCode.NotFound, getAfterDelete.status)
    }

    @Test
    fun `release rerun creates a new release against real Postgres`() = testApplication {
        application { postgresTestModule() }
        val client = jsonClient()
        val teamId = client.loginAndCreateTeam()

        // Create a project
        val blocks = listOf(
            Block.ActionBlock(id = BlockId("build"), name = "Build", type = BlockType.TEAMCITY_BUILD),
        )
        val project = client.createTestProject(
            name = "Rerun Test",
            teamId = teamId,
            blocks = blocks,
        )

        // Start release and await completion
        val original = client.startAndAwaitRelease(project.id)
        assertEquals(ReleaseStatus.SUCCEEDED, original.release.status)

        // Re-run the completed release
        val rerunResponse = client.post(ApiRoutes.Releases.rerun(original.release.id.value))
        assertEquals(HttpStatusCode.Created, rerunResponse.status)
        val rerunRelease = rerunResponse.body<ReleaseResponse>()

        // The rerun should be a different release
        assertTrue(rerunRelease.release.id != original.release.id)
        assertEquals(project.id, rerunRelease.release.projectTemplateId)

        // Await the rerun
        val awaitResponse = client.post(ApiRoutes.Releases.await(rerunRelease.release.id.value))
        assertEquals(HttpStatusCode.OK, awaitResponse.status)
        val completed = awaitResponse.body<ReleaseResponse>()
        assertEquals(ReleaseStatus.SUCCEEDED, completed.release.status)

        // Verify both releases exist
        val listResponse = client.get(ApiRoutes.Releases.BASE)
        assertEquals(HttpStatusCode.OK, listResponse.status)
        val allReleases = listResponse.body<ReleaseListResponse>()
        val projectReleases = allReleases.releases.filter { it.projectTemplateId == project.id }
        assertTrue(projectReleases.size >= 2)
    }
}
