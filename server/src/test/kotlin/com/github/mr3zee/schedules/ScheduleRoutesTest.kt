package com.github.mr3zee.schedules

import com.github.mr3zee.api.*
import com.github.mr3zee.jsonClient
import com.github.mr3zee.login
import com.github.mr3zee.model.TeamId
import com.github.mr3zee.testModule
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ScheduleRoutesTest {

    @Test
    fun `create schedule for project`() = testApplication {
        application { testModule() }
        val client = jsonClient()
        client.login()

        val projectId = client.createProject()

        val createResponse = client.post(ApiRoutes.Schedules.byProject(projectId)) {
            contentType(ContentType.Application.Json)
            setBody(CreateScheduleRequest(cronExpression = "0 0 * * *"))
        }
        assertEquals(HttpStatusCode.Created, createResponse.status)
        val body = createResponse.body<ScheduleResponse>()
        assertEquals("0 0 * * *", body.schedule.cronExpression)
        assertTrue(body.schedule.enabled)
    }

    @Test
    fun `list schedules for project`() = testApplication {
        application { testModule() }
        val client = jsonClient()
        client.login()

        val projectId = client.createProject()

        client.post(ApiRoutes.Schedules.byProject(projectId)) {
            contentType(ContentType.Application.Json)
            setBody(CreateScheduleRequest(cronExpression = "0 0 * * *"))
        }

        val listResponse = client.get(ApiRoutes.Schedules.byProject(projectId))
        assertEquals(HttpStatusCode.OK, listResponse.status)
        val body = listResponse.body<ScheduleListResponse>()
        assertEquals(1, body.schedules.size)
        assertEquals("0 0 * * *", body.schedules[0].cronExpression)
    }

    @Test
    fun `invalid cron expression returns 400`() = testApplication {
        application { testModule() }
        val client = jsonClient()
        client.login()

        val projectId = client.createProject()

        val response = client.post(ApiRoutes.Schedules.byProject(projectId)) {
            contentType(ContentType.Application.Json)
            setBody(CreateScheduleRequest(cronExpression = "not a cron"))
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `toggle schedule`() = testApplication {
        application { testModule() }
        val client = jsonClient()
        client.login()

        val projectId = client.createProject()

        val createResponse = client.post(ApiRoutes.Schedules.byProject(projectId)) {
            contentType(ContentType.Application.Json)
            setBody(CreateScheduleRequest(cronExpression = "0 0 * * *"))
        }
        val created = createResponse.body<ScheduleResponse>()
        assertTrue(created.schedule.enabled)

        val toggleResponse = client.put(ApiRoutes.Schedules.byId(projectId, created.schedule.id)) {
            contentType(ContentType.Application.Json)
            setBody(ToggleScheduleRequest(enabled = false))
        }
        assertEquals(HttpStatusCode.OK, toggleResponse.status)
        val toggled = toggleResponse.body<ScheduleResponse>()
        assertFalse(toggled.schedule.enabled)
    }

    @Test
    fun `delete schedule`() = testApplication {
        application { testModule() }
        val client = jsonClient()
        client.login()

        val projectId = client.createProject()

        val createResponse = client.post(ApiRoutes.Schedules.byProject(projectId)) {
            contentType(ContentType.Application.Json)
            setBody(CreateScheduleRequest(cronExpression = "0 0 * * *"))
        }
        val created = createResponse.body<ScheduleResponse>()

        val deleteResponse = client.delete(ApiRoutes.Schedules.byId(projectId, created.schedule.id))
        assertEquals(HttpStatusCode.NoContent, deleteResponse.status)

        val listResponse = client.get(ApiRoutes.Schedules.byProject(projectId))
        val body = listResponse.body<ScheduleListResponse>()
        assertTrue(body.schedules.isEmpty())
    }
}

/**
 * Helper: creates a project and returns its ID as a String.
 */
private suspend fun io.ktor.client.HttpClient.createProject(name: String = "Test Project"): String {
    val response = post(ApiRoutes.Projects.BASE) {
        contentType(ContentType.Application.Json)
        setBody(CreateProjectRequest(name = name, teamId = TeamId("00000000-0000-0000-0000-000000000000")))
    }
    return response.body<ProjectResponse>().project.id.value
}
