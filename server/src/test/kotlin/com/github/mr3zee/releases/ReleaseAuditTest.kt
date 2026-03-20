package com.github.mr3zee.releases

import com.github.mr3zee.api.*
import com.github.mr3zee.jsonClient
import com.github.mr3zee.loginAndCreateTeam
import com.github.mr3zee.testModule
import com.github.mr3zee.waitUntil
import com.github.mr3zee.model.*
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReleaseAuditTest {

    private suspend fun HttpClient.createProjectAndRelease(
        teamId: TeamId,
    ): Pair<ProjectId, ReleaseId> {
        val blocks = listOf(
            Block.ActionBlock(id = BlockId("a"), name = "Build", type = BlockType.TEAMCITY_BUILD),
        )
        val createResponse = post(ApiRoutes.Projects.BASE) {
            contentType(ContentType.Application.Json)
            setBody(CreateProjectRequest(name = "Audit Test Project", teamId = teamId, dagGraph = DagGraph(blocks = blocks)))
        }
        val projectId = createResponse.body<ProjectResponse>().project.id

        val startResponse = post(ApiRoutes.Releases.BASE) {
            contentType(ContentType.Application.Json)
            setBody(CreateReleaseRequest(projectTemplateId = projectId))
        }
        val releaseId = startResponse.body<ReleaseResponse>().release.id
        post(ApiRoutes.Releases.await(releaseId.value))
        return projectId to releaseId
    }

    @Test
    fun `rerun release creates RELEASE_RERUN audit event`() = testApplication {
        application { testModule() }
        val client = jsonClient()
        val teamId = client.loginAndCreateTeam()

        val (_, releaseId) = client.createProjectAndRelease(teamId)

        val rerunResponse = client.post(ApiRoutes.Releases.rerun(releaseId.value))
        assertEquals(HttpStatusCode.Created, rerunResponse.status)

        waitUntil(delayMillis = 50) {
            val resp = client.get(ApiRoutes.Teams.audit(teamId.value))
            resp.status == HttpStatusCode.OK &&
                resp.body<AuditEventListResponse>().events.any { it.action.name == "RELEASE_RERUN" }
        }

        val auditResponse = client.get(ApiRoutes.Teams.audit(teamId.value))
        val events = auditResponse.body<AuditEventListResponse>().events
        assertTrue(events.any { it.action.name == "RELEASE_RERUN" },
            "Should have RELEASE_RERUN audit event, but found: ${events.map { it.action }}")
    }

    @Test
    fun `archive release creates RELEASE_ARCHIVED audit event`() = testApplication {
        application { testModule() }
        val client = jsonClient()
        val teamId = client.loginAndCreateTeam()

        val (_, releaseId) = client.createProjectAndRelease(teamId)

        val archiveResponse = client.post(ApiRoutes.Releases.archive(releaseId.value))
        assertEquals(HttpStatusCode.OK, archiveResponse.status)

        waitUntil(delayMillis = 50) {
            val resp = client.get(ApiRoutes.Teams.audit(teamId.value))
            resp.status == HttpStatusCode.OK &&
                resp.body<AuditEventListResponse>().events.any { it.action.name == "RELEASE_ARCHIVED" }
        }

        val auditResponse = client.get(ApiRoutes.Teams.audit(teamId.value))
        val events = auditResponse.body<AuditEventListResponse>().events
        assertTrue(events.any { it.action.name == "RELEASE_ARCHIVED" },
            "Should have RELEASE_ARCHIVED audit event, but found: ${events.map { it.action }}")
    }

    @Test
    fun `delete release creates RELEASE_DELETED audit event`() = testApplication {
        application { testModule() }
        val client = jsonClient()
        val teamId = client.loginAndCreateTeam()

        val (_, releaseId) = client.createProjectAndRelease(teamId)

        val deleteResponse = client.delete(ApiRoutes.Releases.byId(releaseId.value))
        assertEquals(HttpStatusCode.NoContent, deleteResponse.status)

        waitUntil(delayMillis = 50) {
            val resp = client.get(ApiRoutes.Teams.audit(teamId.value))
            resp.status == HttpStatusCode.OK &&
                resp.body<AuditEventListResponse>().events.any { it.action.name == "RELEASE_DELETED" }
        }

        val auditResponse = client.get(ApiRoutes.Teams.audit(teamId.value))
        val events = auditResponse.body<AuditEventListResponse>().events
        assertTrue(events.any { it.action.name == "RELEASE_DELETED" },
            "Should have RELEASE_DELETED audit event, but found: ${events.map { it.action }}")
    }

    @Test
    fun `project update creates PROJECT_UPDATED audit event`() = testApplication {
        application { testModule() }
        val client = jsonClient()
        val teamId = client.loginAndCreateTeam()

        val createResponse = client.post(ApiRoutes.Projects.BASE) {
            contentType(ContentType.Application.Json)
            setBody(CreateProjectRequest(name = "Audit Update Test", teamId = teamId))
        }
        val projectId = createResponse.body<ProjectResponse>().project.id

        client.put(ApiRoutes.Projects.byId(projectId.value)) {
            contentType(ContentType.Application.Json)
            setBody(UpdateProjectRequest(name = "Updated Name"))
        }

        waitUntil(delayMillis = 50) {
            val resp = client.get(ApiRoutes.Teams.audit(teamId.value))
            resp.status == HttpStatusCode.OK &&
                resp.body<AuditEventListResponse>().events.any { it.action.name == "PROJECT_UPDATED" }
        }

        val auditResponse = client.get(ApiRoutes.Teams.audit(teamId.value))
        val events = auditResponse.body<AuditEventListResponse>().events
        assertTrue(events.any { it.action.name == "PROJECT_UPDATED" },
            "Should have PROJECT_UPDATED audit event, but found: ${events.map { it.action }}")
    }
}
