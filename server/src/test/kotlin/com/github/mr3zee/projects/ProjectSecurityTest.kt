package com.github.mr3zee.projects

import com.github.mr3zee.api.*
import com.github.mr3zee.jsonClient
import com.github.mr3zee.loginAndCreateTeam
import com.github.mr3zee.testModule
import com.github.mr3zee.model.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProjectSecurityTest {

    @Test
    fun `DAG with too many blocks is rejected`() = testApplication {
        application { testModule() }
        val client = jsonClient()
        val teamId = client.loginAndCreateTeam()

        val blocks = (1..501).map { i ->
            Block.ActionBlock(
                id = BlockId("block-$i"),
                name = "Block $i",
                type = BlockType.TEAMCITY_BUILD,
            )
        }
        val response = client.post(ApiRoutes.Projects.BASE) {
            contentType(ContentType.Application.Json)
            setBody(CreateProjectRequest(name = "Too Many Blocks", teamId = teamId, dagGraph = DagGraph(blocks = blocks)))
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
        val body = response.body<ErrorResponse>()
        assertTrue(body.error.contains("maximum"), "Error should mention maximum blocks limit, got: ${body.error}")
    }

    @Test
    fun `DAG with deeply nested containers is rejected`() = testApplication {
        application { testModule() }
        val client = jsonClient()
        val teamId = client.loginAndCreateTeam()

        fun buildNested(depth: Int, idx: Int = 0): Block.ContainerBlock {
            return if (depth <= 0) {
                Block.ContainerBlock(
                    id = BlockId("container-$idx"),
                    name = "Leaf Container",
                    children = DagGraph(
                        blocks = listOf(
                            Block.ActionBlock(id = BlockId("action-$idx"), name = "Action", type = BlockType.TEAMCITY_BUILD)
                        )
                    ),
                )
            } else {
                Block.ContainerBlock(
                    id = BlockId("container-$idx"),
                    name = "Container $idx",
                    children = DagGraph(blocks = listOf(buildNested(depth - 1, idx + 1))),
                )
            }
        }

        val deeplyNested = buildNested(11)
        val response = client.post(ApiRoutes.Projects.BASE) {
            contentType(ContentType.Application.Json)
            setBody(CreateProjectRequest(name = "Deep Nesting", teamId = teamId, dagGraph = DagGraph(blocks = listOf(deeplyNested))))
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
        val body = response.body<ErrorResponse>()
        assertTrue(body.error.contains("nesting", ignoreCase = true), "Error should mention nesting depth, got: ${body.error}")
    }

    @Test
    fun `DAG with acceptable nesting depth is accepted`() = testApplication {
        application { testModule() }
        val client = jsonClient()
        val teamId = client.loginAndCreateTeam()

        fun buildNested(depth: Int, idx: Int = 0): Block.ContainerBlock {
            return if (depth <= 0) {
                Block.ContainerBlock(
                    id = BlockId("container-$idx"),
                    name = "Leaf",
                    children = DagGraph(
                        blocks = listOf(
                            Block.ActionBlock(id = BlockId("action-$idx"), name = "Action", type = BlockType.TEAMCITY_BUILD)
                        )
                    ),
                )
            } else {
                Block.ContainerBlock(
                    id = BlockId("container-$idx"),
                    name = "Container $idx",
                    children = DagGraph(blocks = listOf(buildNested(depth - 1, idx + 1))),
                )
            }
        }

        val nestedWithinLimit = buildNested(9)
        val response = client.post(ApiRoutes.Projects.BASE) {
            contentType(ContentType.Application.Json)
            setBody(CreateProjectRequest(name = "OK Nesting", teamId = teamId, dagGraph = DagGraph(blocks = listOf(nestedWithinLimit))))
        }
        assertEquals(HttpStatusCode.Created, response.status)
    }

    @Test
    fun `delete project with active release returns 400`() = testApplication {
        application { testModule() }
        val client = jsonClient()
        val teamId = client.loginAndCreateTeam()

        val blocks = listOf(
            Block.ActionBlock(id = BlockId("action"), name = "Wait", type = BlockType.SLACK_MESSAGE, preGate = Gate()),
        )
        val createResponse = client.post(ApiRoutes.Projects.BASE) {
            contentType(ContentType.Application.Json)
            setBody(CreateProjectRequest(name = "Active Project", teamId = teamId, dagGraph = DagGraph(blocks = blocks)))
        }
        val projectId = createResponse.body<ProjectResponse>().project.id

        client.post(ApiRoutes.Releases.BASE) {
            contentType(ContentType.Application.Json)
            setBody(CreateReleaseRequest(projectTemplateId = projectId))
        }

        val deleteResponse = client.delete(ApiRoutes.Projects.byId(projectId.value))
        assertEquals(HttpStatusCode.BadRequest, deleteResponse.status)
        val body = deleteResponse.body<ErrorResponse>()
        assertTrue(body.error.contains("active releases"), "Error should mention active releases, got: ${body.error}")
    }

    @Test
    fun `delete project with no releases succeeds`() = testApplication {
        application { testModule() }
        val client = jsonClient()
        val teamId = client.loginAndCreateTeam()

        val createResponse = client.post(ApiRoutes.Projects.BASE) {
            contentType(ContentType.Application.Json)
            setBody(CreateProjectRequest(name = "Deletable Project", teamId = teamId))
        }
        val projectId = createResponse.body<ProjectResponse>().project.id

        val deleteResponse = client.delete(ApiRoutes.Projects.byId(projectId.value))
        assertEquals(HttpStatusCode.NoContent, deleteResponse.status)
    }

    @Test
    fun `project name exceeding 255 chars is rejected on create`() = testApplication {
        application { testModule() }
        val client = jsonClient()
        val teamId = client.loginAndCreateTeam()

        val longName = "A".repeat(256)
        val response = client.post(ApiRoutes.Projects.BASE) {
            contentType(ContentType.Application.Json)
            setBody(CreateProjectRequest(name = longName, teamId = teamId))
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `project name at 255 chars is accepted`() = testApplication {
        application { testModule() }
        val client = jsonClient()
        val teamId = client.loginAndCreateTeam()

        val maxName = "A".repeat(255)
        val response = client.post(ApiRoutes.Projects.BASE) {
            contentType(ContentType.Application.Json)
            setBody(CreateProjectRequest(name = maxName, teamId = teamId))
        }
        assertEquals(HttpStatusCode.Created, response.status)
    }

    @Test
    fun `project description exceeding 2000 chars is rejected`() = testApplication {
        application { testModule() }
        val client = jsonClient()
        val teamId = client.loginAndCreateTeam()

        val longDesc = "D".repeat(2001)
        val response = client.post(ApiRoutes.Projects.BASE) {
            contentType(ContentType.Application.Json)
            setBody(CreateProjectRequest(name = "Desc Test", teamId = teamId, description = longDesc))
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `project name too long on update is rejected`() = testApplication {
        application { testModule() }
        val client = jsonClient()
        val teamId = client.loginAndCreateTeam()

        val createResponse = client.post(ApiRoutes.Projects.BASE) {
            contentType(ContentType.Application.Json)
            setBody(CreateProjectRequest(name = "Original", teamId = teamId))
        }
        val projectId = createResponse.body<ProjectResponse>().project.id

        val response = client.put(ApiRoutes.Projects.byId(projectId.value)) {
            contentType(ContentType.Application.Json)
            setBody(UpdateProjectRequest(name = "B".repeat(256)))
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }
}
