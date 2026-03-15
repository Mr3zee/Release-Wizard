package com.github.mr3zee.notifications

import com.github.mr3zee.api.*
import com.github.mr3zee.jsonClient
import com.github.mr3zee.loginAndCreateTeam
import com.github.mr3zee.createTestProject
import com.github.mr3zee.model.NotificationConfig
import com.github.mr3zee.model.ProjectId
import com.github.mr3zee.testModule
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NotificationRoutesTest {

    @Test
    fun `create notification config for owned project`() = testApplication {
        application { testModule() }
        val client = jsonClient()
        val teamId = client.loginAndCreateTeam()

        val projectId = client.createTestProject(teamId)

        val createResponse = client.post(ApiRoutes.Notifications.byProject(projectId)) {
            contentType(ContentType.Application.Json)
            setBody(
                CreateNotificationConfigRequest(
                    projectId = ProjectId(projectId),
                    type = "slack",
                    config = NotificationConfig.SlackNotification(
                        webhookUrl = "https://hooks.slack.example.com/test",
                        channel = "#releases",
                    ),
                )
            )
        }
        assertEquals(HttpStatusCode.Created, createResponse.status)
        val body = createResponse.body<NotificationConfigResponse>()
        assertEquals(ProjectId(projectId), body.projectId)
        assertEquals("slack", body.type)
        assertTrue(body.enabled)
    }

    @Test
    fun `list notification configs for project`() = testApplication {
        application { testModule() }
        val client = jsonClient()
        val teamId = client.loginAndCreateTeam()

        val projectId = client.createTestProject(teamId)

        client.post(ApiRoutes.Notifications.byProject(projectId)) {
            contentType(ContentType.Application.Json)
            setBody(
                CreateNotificationConfigRequest(
                    projectId = ProjectId(projectId),
                    type = "slack",
                    config = NotificationConfig.SlackNotification(
                        webhookUrl = "https://hooks.slack.example.com/test",
                        channel = "#releases",
                    ),
                )
            )
        }

        val listResponse = client.get(ApiRoutes.Notifications.byProject(projectId))
        assertEquals(HttpStatusCode.OK, listResponse.status)
        val body = listResponse.body<NotificationConfigListResponse>()
        assertEquals(1, body.configs.size)
        assertEquals("slack", body.configs[0].type)
    }

    @Test
    fun `delete notification config`() = testApplication {
        application { testModule() }
        val client = jsonClient()
        val teamId = client.loginAndCreateTeam()

        val projectId = client.createTestProject(teamId)

        val createResponse = client.post(ApiRoutes.Notifications.byProject(projectId)) {
            contentType(ContentType.Application.Json)
            setBody(
                CreateNotificationConfigRequest(
                    projectId = ProjectId(projectId),
                    type = "slack",
                    config = NotificationConfig.SlackNotification(
                        webhookUrl = "https://hooks.slack.example.com/test",
                        channel = "#releases",
                    ),
                )
            )
        }
        val created = createResponse.body<NotificationConfigResponse>()

        val deleteResponse = client.delete(ApiRoutes.Notifications.byId(projectId, created.id))
        assertEquals(HttpStatusCode.NoContent, deleteResponse.status)

        val listResponse = client.get(ApiRoutes.Notifications.byProject(projectId))
        val body = listResponse.body<NotificationConfigListResponse>()
        assertTrue(body.configs.isEmpty())
    }

    @Test
    fun `create notification config for nonexistent project does not crash`() = testApplication {
        application { testModule() }
        val client = jsonClient()
        client.loginAndCreateTeam()

        val fakeProjectId = "00000000-0000-0000-0000-000000000000"

        val response = client.post(ApiRoutes.Notifications.byProject(fakeProjectId)) {
            contentType(ContentType.Application.Json)
            setBody(
                CreateNotificationConfigRequest(
                    projectId = ProjectId(fakeProjectId),
                    type = "slack",
                    config = NotificationConfig.SlackNotification(
                        webhookUrl = "https://hooks.slack.example.com/test",
                        channel = "#releases",
                    ),
                )
            )
        }
        // FK constraints require a real project to exist, so this should return an error (4xx or 5xx).
        assertTrue(response.status.value >= 400, "Should reject notification for nonexistent project")
    }
}
