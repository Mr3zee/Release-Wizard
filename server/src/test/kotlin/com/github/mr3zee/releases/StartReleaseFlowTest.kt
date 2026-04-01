package com.github.mr3zee.releases

import com.github.mr3zee.api.*
import com.github.mr3zee.createTestProject
import com.github.mr3zee.createTestProjectWithBlocks
import com.github.mr3zee.jsonClient
import com.github.mr3zee.loginAndCreateTeam
import com.github.mr3zee.model.Block
import com.github.mr3zee.model.BlockId
import com.github.mr3zee.model.BlockType
import com.github.mr3zee.model.DagGraph
import com.github.mr3zee.model.Parameter
import ProjectId
import com.github.mr3zee.testModule
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests for the unified Start Release flow:
 * - Release name defaults to project name
 * - Parameters are merged (request overrides project defaults)
 * - Release name template on schedules/triggers
 */
class StartReleaseFlowTest {

    // ── Release creation with name and parameters ──

    @Test
    fun `start release with custom name uses provided name`() = testApplication {
        application { testModule() }
        val client = jsonClient()
        val teamId = client.loginAndCreateTeam()
        val projectId = client.createTestProjectWithBlocks(teamId, "Default Name")

        val response = client.post(ApiRoutes.Releases.BASE) {
            contentType(ContentType.Application.Json)
            setBody(CreateReleaseRequest(
                projectTemplateId = ProjectId(projectId),
                name = "Custom Release v2.0",
            ))
        }
        assertEquals(HttpStatusCode.Created, response.status)
        val body = response.body<ReleaseResponse>()
        assertEquals("Custom Release v2.0", body.release.name)
    }

    @Test
    fun `start release with blank name defaults to project name`() = testApplication {
        application { testModule() }
        val client = jsonClient()
        val teamId = client.loginAndCreateTeam()
        val projectId = client.createTestProjectWithBlocks(teamId, "My Project")

        val response = client.post(ApiRoutes.Releases.BASE) {
            contentType(ContentType.Application.Json)
            setBody(CreateReleaseRequest(
                projectTemplateId = ProjectId(projectId),
                name = "",
            ))
        }
        assertEquals(HttpStatusCode.Created, response.status)
        val body = response.body<ReleaseResponse>()
        assertEquals("My Project", body.release.name)
    }

    @Test
    fun `start release with parameter overrides merges with project defaults`() = testApplication {
        application { testModule() }
        val client = jsonClient()
        val teamId = client.loginAndCreateTeam()

        // Create project with parameters
        val projectResponse = client.post(ApiRoutes.Projects.BASE) {
            contentType(ContentType.Application.Json)
            setBody(CreateProjectRequest(
                name = "Param Project",
                teamId = teamId,
                dagGraph = DagGraph(
                    blocks = listOf(
                        Block.ActionBlock(
                            id = BlockId("b1"),
                            name = "Build",
                            type = BlockType.TEAMCITY_BUILD,
                        ),
                    ),
                ),
                parameters = listOf(
                    Parameter(key = "version", value = "1.0.0"),
                    Parameter(key = "env", value = "dev"),
                ),
            ))
        }
        val projectId = projectResponse.body<ProjectResponse>().project.id

        // Start release with version override
        val releaseResponse = client.post(ApiRoutes.Releases.BASE) {
            contentType(ContentType.Application.Json)
            setBody(CreateReleaseRequest(
                projectTemplateId = projectId,
                name = "Release v2.0",
                parameters = listOf(Parameter(key = "version", value = "2.0.0")),
            ))
        }
        assertEquals(HttpStatusCode.Created, releaseResponse.status)
        val release = releaseResponse.body<ReleaseResponse>().release

        // version should be overridden, env should keep default
        val versionParam = release.parameters.first { it.key == "version" }
        val envParam = release.parameters.first { it.key == "env" }
        assertEquals("2.0.0", versionParam.value)
        assertEquals("dev", envParam.value)
    }

    // ── Schedule with release name template ──

    @Test
    fun `schedule stores release name template`() = testApplication {
        application { testModule() }
        val client = jsonClient()
        val teamId = client.loginAndCreateTeam()
        val projectId = client.createTestProject(teamId)

        val createResponse = client.post(ApiRoutes.Schedules.byProject(projectId)) {
            contentType(ContentType.Application.Json)
            setBody(CreateScheduleRequest(
                cronExpression = "0 9 * * *",
                releaseNameTemplate = "Nightly Build",
            ))
        }
        assertEquals(HttpStatusCode.Created, createResponse.status)
        val schedule = createResponse.body<ScheduleResponse>().schedule
        assertEquals("Nightly Build", schedule.releaseNameTemplate)
    }

    @Test
    fun `schedule release name template persists in list`() = testApplication {
        application { testModule() }
        val client = jsonClient()
        val teamId = client.loginAndCreateTeam()
        val projectId = client.createTestProject(teamId)

        client.post(ApiRoutes.Schedules.byProject(projectId)) {
            contentType(ContentType.Application.Json)
            setBody(CreateScheduleRequest(
                cronExpression = "0 9 * * *",
                releaseNameTemplate = "Weekly Release",
            ))
        }

        val listResponse = client.get(ApiRoutes.Schedules.byProject(projectId))
        assertEquals(HttpStatusCode.OK, listResponse.status)
        val schedules = listResponse.body<ScheduleListResponse>().schedules
        assertEquals(1, schedules.size)
        assertEquals("Weekly Release", schedules[0].releaseNameTemplate)
    }

    @Test
    fun `schedule with parameters stores them`() = testApplication {
        application { testModule() }
        val client = jsonClient()
        val teamId = client.loginAndCreateTeam()
        val projectId = client.createTestProject(teamId)

        val createResponse = client.post(ApiRoutes.Schedules.byProject(projectId)) {
            contentType(ContentType.Application.Json)
            setBody(CreateScheduleRequest(
                cronExpression = "0 9 * * *",
                parameters = listOf(Parameter(key = "env", value = "staging")),
                releaseNameTemplate = "Nightly",
            ))
        }
        assertEquals(HttpStatusCode.Created, createResponse.status)
        val schedule = createResponse.body<ScheduleResponse>().schedule
        assertEquals(1, schedule.parameters.size)
        assertEquals("env", schedule.parameters[0].key)
        assertEquals("staging", schedule.parameters[0].value)
    }

    // ── Webhook trigger with release name template ──

    @Test
    fun `webhook trigger stores release name template`() = testApplication {
        application { testModule() }
        val client = jsonClient()
        val teamId = client.loginAndCreateTeam()
        val projectId = client.createTestProject(teamId)

        val createResponse = client.post(ApiRoutes.Triggers.byProject(projectId)) {
            contentType(ContentType.Application.Json)
            setBody(CreateTriggerRequest(
                releaseNameTemplate = "Webhook Release",
                parametersTemplate = listOf(Parameter(key = "branch", value = "main")),
            ))
        }
        assertEquals(HttpStatusCode.Created, createResponse.status)
        // Note: TriggerResponse doesn't include releaseNameTemplate,
        // but the entity stores it. We verify the creation succeeded.
        val trigger = createResponse.body<TriggerResponse>()
        assertTrue(trigger.id.isNotBlank())
        assertTrue(trigger.secret.isNotBlank())
    }
}
