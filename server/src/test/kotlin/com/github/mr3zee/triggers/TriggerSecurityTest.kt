package com.github.mr3zee.triggers

import com.github.mr3zee.api.*
import com.github.mr3zee.jsonClient
import com.github.mr3zee.loginAndCreateTeam
import com.github.mr3zee.createTestProject
import com.github.mr3zee.testModule
import com.github.mr3zee.waitUntil
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TriggerSecurityTest {

    @Test
    fun `cannot exceed per-project trigger limit`() = testApplication {
        application { testModule() }
        val client = jsonClient()
        val teamId = client.loginAndCreateTeam()

        val projectId = client.createTestProject(teamId)

        // Create triggers up to the limit (50)
        repeat(50) {
            val resp = client.post(ApiRoutes.Triggers.byProject(projectId)) {
                contentType(ContentType.Application.Json)
                setBody(CreateTriggerRequest())
            }
            assertEquals(HttpStatusCode.Created, resp.status, "Trigger $it should be created successfully")
        }

        // The 51st trigger should fail
        val response = client.post(ApiRoutes.Triggers.byProject(projectId)) {
            contentType(ContentType.Application.Json)
            setBody(CreateTriggerRequest())
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
        val body = response.body<ErrorResponse>()
        assertTrue(body.error.contains("Maximum"), "Error should mention maximum trigger limit, got: ${body.error}")
    }

    @Test
    fun `trigger creation creates TRIGGER_CREATED audit event`() = testApplication {
        application { testModule() }
        val client = jsonClient()
        val teamId = client.loginAndCreateTeam()

        val projectId = client.createTestProject(teamId)

        val createResponse = client.post(ApiRoutes.Triggers.byProject(projectId)) {
            contentType(ContentType.Application.Json)
            setBody(CreateTriggerRequest())
        }
        assertEquals(HttpStatusCode.Created, createResponse.status)

        waitUntil(delayMillis = 50) {
            val resp = client.get(ApiRoutes.Teams.audit(teamId.value))
            resp.status == HttpStatusCode.OK &&
                resp.body<AuditEventListResponse>().events.any { it.action.name == "TRIGGER_CREATED" }
        }

        val auditResponse = client.get(ApiRoutes.Teams.audit(teamId.value))
        val events = auditResponse.body<AuditEventListResponse>().events
        assertTrue(events.any { it.action.name == "TRIGGER_CREATED" },
            "Should have TRIGGER_CREATED audit event, but found: ${events.map { it.action }}")
    }

    @Test
    fun `trigger deletion creates TRIGGER_DELETED audit event`() = testApplication {
        application { testModule() }
        val client = jsonClient()
        val teamId = client.loginAndCreateTeam()

        val projectId = client.createTestProject(teamId)

        val createResponse = client.post(ApiRoutes.Triggers.byProject(projectId)) {
            contentType(ContentType.Application.Json)
            setBody(CreateTriggerRequest())
        }
        val triggerId = createResponse.body<TriggerResponse>().id

        val deleteResponse = client.delete(ApiRoutes.Triggers.byId(projectId, triggerId))
        assertEquals(HttpStatusCode.NoContent, deleteResponse.status)

        waitUntil(delayMillis = 50) {
            val resp = client.get(ApiRoutes.Teams.audit(teamId.value))
            resp.status == HttpStatusCode.OK &&
                resp.body<AuditEventListResponse>().events.any { it.action.name == "TRIGGER_DELETED" }
        }

        val auditResponse = client.get(ApiRoutes.Teams.audit(teamId.value))
        val events = auditResponse.body<AuditEventListResponse>().events
        assertTrue(events.any { it.action.name == "TRIGGER_DELETED" },
            "Should have TRIGGER_DELETED audit event, but found: ${events.map { it.action }}")
    }
}
