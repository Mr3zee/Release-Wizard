package com.github.mr3zee.triggers

import com.github.mr3zee.api.*
import com.github.mr3zee.jsonClient
import com.github.mr3zee.loginAndCreateTeam
import com.github.mr3zee.createTestProject
import com.github.mr3zee.createTestProjectWithBlocks
import com.github.mr3zee.testModule
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class TriggerRoutesTest {

    @Test
    fun `create trigger returns secret`() = testApplication {
        application { testModule() }
        val client = jsonClient()
        val teamId = client.loginAndCreateTeam()

        val projectId = client.createTestProject(teamId)

        val createResponse = client.post(ApiRoutes.Triggers.byProject(projectId)) {
            contentType(ContentType.Application.Json)
            setBody(CreateTriggerRequest())
        }
        assertEquals(HttpStatusCode.Created, createResponse.status)
        val body = createResponse.body<TriggerResponse>()
        assertNotEquals("********", body.secret, "Create response should return the raw secret, not masked")
        assertTrue(body.secret.isNotBlank())
        assertTrue(body.enabled)
    }

    @Test
    fun `list triggers shows masked secret`() = testApplication {
        application { testModule() }
        val client = jsonClient()
        val teamId = client.loginAndCreateTeam()

        val projectId = client.createTestProject(teamId)

        client.post(ApiRoutes.Triggers.byProject(projectId)) {
            contentType(ContentType.Application.Json)
            setBody(CreateTriggerRequest())
        }

        val listResponse = client.get(ApiRoutes.Triggers.byProject(projectId))
        assertEquals(HttpStatusCode.OK, listResponse.status)
        val body = listResponse.body<TriggerListResponse>()
        assertEquals(1, body.triggers.size)
        assertEquals("********", body.triggers[0].secret)
    }

    @Test
    fun `delete trigger`() = testApplication {
        application { testModule() }
        val client = jsonClient()
        val teamId = client.loginAndCreateTeam()

        val projectId = client.createTestProject(teamId)

        val createResponse = client.post(ApiRoutes.Triggers.byProject(projectId)) {
            contentType(ContentType.Application.Json)
            setBody(CreateTriggerRequest())
        }
        val created = createResponse.body<TriggerResponse>()

        val deleteResponse = client.delete(ApiRoutes.Triggers.byId(projectId, created.id))
        assertEquals(HttpStatusCode.NoContent, deleteResponse.status)

        val listResponse = client.get(ApiRoutes.Triggers.byProject(projectId))
        val body = listResponse.body<TriggerListResponse>()
        assertTrue(body.triggers.isEmpty())
    }

    @Test
    fun `webhook fires release with correct secret`() = testApplication {
        application { testModule() }
        val client = jsonClient()
        val teamId = client.loginAndCreateTeam()

        // Create a project WITH blocks so startScheduledRelease succeeds
        val projectId = client.createTestProjectWithBlocks(teamId)

        val createResponse = client.post(ApiRoutes.Triggers.byProject(projectId)) {
            contentType(ContentType.Application.Json)
            setBody(CreateTriggerRequest())
        }
        val created = createResponse.body<TriggerResponse>()
        val rawSecret = created.secret

        // Webhook endpoint is unauthenticated; use a plain client without session cookies
        val webhookClient = jsonClient()
        val webhookResponse = webhookClient.post(ApiRoutes.Triggers.webhook(created.id)) {
            header(HttpHeaders.Authorization, "Bearer $rawSecret")
        }
        // The webhook should succeed (200 = "Webhook triggered")
        assertEquals(HttpStatusCode.OK, webhookResponse.status)
    }

    @Test
    fun `webhook with wrong secret returns 401`() = testApplication {
        application { testModule() }
        val client = jsonClient()
        val teamId = client.loginAndCreateTeam()

        val projectId = client.createTestProject(teamId)

        val createResponse = client.post(ApiRoutes.Triggers.byProject(projectId)) {
            contentType(ContentType.Application.Json)
            setBody(CreateTriggerRequest())
        }
        val created = createResponse.body<TriggerResponse>()

        val webhookClient = jsonClient()
        val webhookResponse = webhookClient.post(ApiRoutes.Triggers.webhook(created.id)) {
            header(HttpHeaders.Authorization, "Bearer wrong-secret-value")
        }
        // The service returns false from fireWebhook -> route responds 401 "Invalid trigger or secret"
        assertEquals(HttpStatusCode.Unauthorized, webhookResponse.status)
    }
}
