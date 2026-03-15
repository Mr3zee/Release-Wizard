package com.github.mr3zee.projects

import com.github.mr3zee.api.*
import com.github.mr3zee.jsonClient
import com.github.mr3zee.login
import com.github.mr3zee.loginAndCreateTeam
import com.github.mr3zee.testModule
import com.github.mr3zee.model.TeamId
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProjectsRoutesTest {

    @Test
    fun `list projects returns empty list initially`() = testApplication {
        application { testModule() }
        val client = jsonClient()
        client.login()
        val response = client.get(ApiRoutes.Projects.BASE)
        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.body<ProjectListResponse>()
        assertTrue(body.projects.isEmpty())
    }

    @Test
    fun `create and get project`() = testApplication {
        application { testModule() }
        val client = jsonClient()
        val teamId = client.loginAndCreateTeam()

        val createResponse = client.post(ApiRoutes.Projects.BASE) {
            contentType(ContentType.Application.Json)
            setBody(CreateProjectRequest(name = "Test Project", teamId = teamId, description = "A test"))
        }
        assertEquals(HttpStatusCode.Created, createResponse.status)
        val created = createResponse.body<ProjectResponse>()
        assertEquals("Test Project", created.project.name)
        assertEquals("A test", created.project.description)

        val getResponse = client.get(ApiRoutes.Projects.byId(created.project.id.value))
        assertEquals(HttpStatusCode.OK, getResponse.status)
        val fetched = getResponse.body<ProjectResponse>()
        assertEquals(created.project.id, fetched.project.id)
        assertEquals("Test Project", fetched.project.name)
    }

    @Test
    fun `create project with blank name returns 400`() = testApplication {
        application { testModule() }
        val client = jsonClient()
        val teamId = client.loginAndCreateTeam()

        val response = client.post(ApiRoutes.Projects.BASE) {
            contentType(ContentType.Application.Json)
            setBody(CreateProjectRequest(name = "", teamId = teamId))
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `update project preserves createdAt and updates updatedAt`() = testApplication {
        application { testModule() }
        val client = jsonClient()
        val teamId = client.loginAndCreateTeam()

        val createResponse = client.post(ApiRoutes.Projects.BASE) {
            contentType(ContentType.Application.Json)
            setBody(CreateProjectRequest(name = "Original", teamId = teamId))
        }
        val created = createResponse.body<ProjectResponse>()

        val updateResponse = client.put(ApiRoutes.Projects.byId(created.project.id.value)) {
            contentType(ContentType.Application.Json)
            setBody(UpdateProjectRequest(name = "Updated"))
        }
        assertEquals(HttpStatusCode.OK, updateResponse.status)
        val updated = updateResponse.body<ProjectResponse>()
        assertEquals("Updated", updated.project.name)
        assertEquals(created.project.createdAt, updated.project.createdAt)
        assertTrue(updated.project.updatedAt >= created.project.updatedAt)
    }

    @Test
    fun `update nonexistent project returns 404`() = testApplication {
        application { testModule() }
        val client = jsonClient()
        client.login()

        val response = client.put(ApiRoutes.Projects.byId("00000000-0000-0000-0000-000000000000")) {
            contentType(ContentType.Application.Json)
            setBody(UpdateProjectRequest(name = "Nope"))
        }
        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `delete project`() = testApplication {
        application { testModule() }
        val client = jsonClient()
        val teamId = client.loginAndCreateTeam()

        val createResponse = client.post(ApiRoutes.Projects.BASE) {
            contentType(ContentType.Application.Json)
            setBody(CreateProjectRequest(name = "To Delete", teamId = teamId))
        }
        val created = createResponse.body<ProjectResponse>()

        val deleteResponse = client.delete(ApiRoutes.Projects.byId(created.project.id.value))
        assertEquals(HttpStatusCode.NoContent, deleteResponse.status)

        val getResponse = client.get(ApiRoutes.Projects.byId(created.project.id.value))
        assertEquals(HttpStatusCode.NotFound, getResponse.status)
    }

    @Test
    fun `delete nonexistent project returns 404`() = testApplication {
        application { testModule() }
        val client = jsonClient()
        client.login()

        val response = client.delete(ApiRoutes.Projects.byId("00000000-0000-0000-0000-000000000000"))
        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `get nonexistent project returns 404`() = testApplication {
        application { testModule() }
        val client = jsonClient()
        client.login()

        val response = client.get(ApiRoutes.Projects.byId("00000000-0000-0000-0000-000000000000"))
        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `malformed UUID returns 400`() = testApplication {
        application { testModule() }
        val client = jsonClient()
        client.login()

        val response = client.get(ApiRoutes.Projects.byId("not-a-uuid"))
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `list projects returns created projects`() = testApplication {
        application { testModule() }
        val client = jsonClient()
        val teamId = client.loginAndCreateTeam()

        client.post(ApiRoutes.Projects.BASE) {
            contentType(ContentType.Application.Json)
            setBody(CreateProjectRequest(name = "Project A", teamId = teamId))
        }
        client.post(ApiRoutes.Projects.BASE) {
            contentType(ContentType.Application.Json)
            setBody(CreateProjectRequest(name = "Project B", teamId = teamId))
        }

        val listResponse = client.get(ApiRoutes.Projects.BASE)
        val body = listResponse.body<ProjectListResponse>()
        assertEquals(2, body.projects.size)
    }

    @Test
    fun `unauthenticated request returns 401`() = testApplication {
        application { testModule() }
        val client = jsonClient()
        val response = client.get(ApiRoutes.Projects.BASE)
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }
}
