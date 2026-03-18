package com.github.mr3zee.audit

import com.github.mr3zee.api.*
import com.github.mr3zee.jsonClient
import com.github.mr3zee.login
import com.github.mr3zee.createTestTeam
import com.github.mr3zee.testModule
import com.github.mr3zee.waitUntil
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AuditServiceTest {

    @Test
    fun `team creation produces audit event`() = testApplication {
        application { testModule() }
        val client = jsonClient()
        client.login()
        val teamId = client.createTestTeam("Audit Team")

        // Audit events are written asynchronously; poll until they appear
        waitUntil(delayMillis = 50) {
            val resp = client.get(ApiRoutes.Teams.audit(teamId.value))
            resp.status == HttpStatusCode.OK &&
                resp.body<AuditEventListResponse>().events.any { it.action.name == "TEAM_CREATED" }
        }

        val response = client.get(ApiRoutes.Teams.audit(teamId.value))
        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.body<AuditEventListResponse>()
        assertTrue(body.events.any { it.action.name == "TEAM_CREATED" },
            "Should have TEAM_CREATED audit event, but found: ${body.events.map { it.action }}")
    }

    @Test
    fun `member changes produce audit events`() = testApplication {
        application { testModule() }
        val admin = jsonClient()
        admin.login("admin", "adminpass")
        val teamId = admin.createTestTeam("Member Audit Team")

        // Register and invite a second user
        val user2 = jsonClient()
        user2.login("member", "memberpass")
        val meResponse = user2.get(ApiRoutes.Auth.ME).body<UserInfo>()
        val user2Id = meResponse.id ?: error("No user ID")

        admin.post(ApiRoutes.Teams.invites(teamId.value)) {
            contentType(ContentType.Application.Json)
            setBody(CreateInviteRequest(userId = com.github.mr3zee.model.UserId(user2Id)))
        }

        // Audit events are written asynchronously; poll until they appear
        waitUntil(delayMillis = 50) {
            val resp = admin.get(ApiRoutes.Teams.audit(teamId.value))
            resp.status == HttpStatusCode.OK &&
                resp.body<AuditEventListResponse>().events.any { it.action.name == "INVITE_SENT" }
        }

        val response = admin.get(ApiRoutes.Teams.audit(teamId.value))
        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.body<AuditEventListResponse>()
        assertTrue(body.events.any { it.action.name == "INVITE_SENT" },
            "Should have INVITE_SENT audit event, but found: ${body.events.map { it.action }}")
    }

    @Test
    fun `project deletion produces audit event`() = testApplication {
        application { testModule() }
        val client = jsonClient()
        client.login()
        val teamId = client.createTestTeam("Project Audit Team")

        // Create a project
        val createResponse = client.post(ApiRoutes.Projects.BASE) {
            contentType(ContentType.Application.Json)
            setBody(CreateProjectRequest(name = "Audit Project", teamId = teamId))
        }
        assertEquals(HttpStatusCode.Created, createResponse.status)
        val projectId = createResponse.body<ProjectResponse>().project.id

        // Delete the project
        client.delete(ApiRoutes.Projects.byId(projectId.value))

        // Audit events are written asynchronously; poll until they appear
        waitUntil(delayMillis = 50) {
            val resp = client.get(ApiRoutes.Teams.audit(teamId.value))
            resp.status == HttpStatusCode.OK &&
                resp.body<AuditEventListResponse>().events.any { it.action.name == "PROJECT_DELETED" }
        }

        val response = client.get(ApiRoutes.Teams.audit(teamId.value))
        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.body<AuditEventListResponse>()
        assertTrue(body.events.any { it.action.name == "PROJECT_DELETED" },
            "Should have PROJECT_DELETED audit event, but found: ${body.events.map { it.action }}")
    }

    @Test
    fun `findByTeam supports pagination`() = testApplication {
        application { testModule() }
        val client = jsonClient()
        client.login()
        val teamId = client.createTestTeam("Pagination Audit Team")

        // Audit events are written asynchronously; poll until they appear
        waitUntil(delayMillis = 50) {
            val resp = client.get(ApiRoutes.Teams.audit(teamId.value))
            resp.status == HttpStatusCode.OK &&
                resp.body<AuditEventListResponse>().events.isNotEmpty()
        }

        // Request with offset and limit
        val response = client.get("${ApiRoutes.Teams.audit(teamId.value)}?offset=0&limit=5")
        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.body<AuditEventListResponse>()
        // Should have at least the TEAM_CREATED event
        assertTrue(body.events.isNotEmpty(), "Should have at least one audit event")
        assertTrue(body.events.size <= 5, "Should respect the limit")
    }
}
