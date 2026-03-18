package com.github.mr3zee.mavenpublication

import com.github.mr3zee.api.*
import com.github.mr3zee.createTestProject
import com.github.mr3zee.jsonClient
import com.github.mr3zee.loginAndCreateTeam
import com.github.mr3zee.testModule
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// Uses https://repo.example.com as the test repo URL.
// This domain is unlikely to resolve DNS, so validateUrlNotPrivate() passes.
// The MockEngine in createTestHttpClient() returns stub maven-metadata.xml for any
// URL containing "maven-metadata.xml".
private const val TEST_REPO_URL = "https://repo.example.com/maven2"

class MavenTriggerRoutesTest {

    @Test
    fun `create maven trigger returns trigger with no knownVersions in response`() = testApplication {
        application { testModule() }
        val client = jsonClient()
        val teamId = client.loginAndCreateTeam()
        val projectId = client.createTestProject(teamId)

        val response = client.post(ApiRoutes.MavenTriggers.byProject(projectId)) {
            contentType(ContentType.Application.Json)
            setBody(
                CreateMavenTriggerRequest(
                    repoUrl = TEST_REPO_URL,
                    groupId = "com.example",
                    artifactId = "my-library",
                    parameterKey = "version",
                )
            )
        }
        assertEquals(HttpStatusCode.Created, response.status)
        val body = response.body<MavenTriggerResponse>()
        assertEquals("com.example", body.trigger.groupId)
        assertEquals("my-library", body.trigger.artifactId)
        assertEquals("version", body.trigger.parameterKey)
        assertTrue(body.trigger.enabled)
        assertFalse(body.trigger.includeSnapshots)
    }

    @Test
    fun `list maven triggers for project`() = testApplication {
        application { testModule() }
        val client = jsonClient()
        val teamId = client.loginAndCreateTeam()
        val projectId = client.createTestProject(teamId)

        client.post(ApiRoutes.MavenTriggers.byProject(projectId)) {
            contentType(ContentType.Application.Json)
            setBody(
                CreateMavenTriggerRequest(
                    repoUrl = TEST_REPO_URL,
                    groupId = "com.example",
                    artifactId = "my-library",
                    parameterKey = "version",
                )
            )
        }

        val listResponse = client.get(ApiRoutes.MavenTriggers.byProject(projectId))
        assertEquals(HttpStatusCode.OK, listResponse.status)
        val body = listResponse.body<MavenTriggerListResponse>()
        assertEquals(1, body.triggers.size)
        assertEquals("com.example", body.triggers[0].groupId)
    }

    @Test
    fun `get maven trigger by id`() = testApplication {
        application { testModule() }
        val client = jsonClient()
        val teamId = client.loginAndCreateTeam()
        val projectId = client.createTestProject(teamId)

        val created = client.post(ApiRoutes.MavenTriggers.byProject(projectId)) {
            contentType(ContentType.Application.Json)
            setBody(
                CreateMavenTriggerRequest(
                    repoUrl = TEST_REPO_URL,
                    groupId = "com.example",
                    artifactId = "my-library",
                    parameterKey = "version",
                )
            )
        }.body<MavenTriggerResponse>()

        val getResponse = client.get(ApiRoutes.MavenTriggers.byId(projectId, created.trigger.id))
        assertEquals(HttpStatusCode.OK, getResponse.status)
        val body = getResponse.body<MavenTriggerResponse>()
        assertEquals(created.trigger.id, body.trigger.id)
    }

    @Test
    fun `toggle maven trigger`() = testApplication {
        application { testModule() }
        val client = jsonClient()
        val teamId = client.loginAndCreateTeam()
        val projectId = client.createTestProject(teamId)

        val created = client.post(ApiRoutes.MavenTriggers.byProject(projectId)) {
            contentType(ContentType.Application.Json)
            setBody(
                CreateMavenTriggerRequest(
                    repoUrl = TEST_REPO_URL,
                    groupId = "com.example",
                    artifactId = "lib",
                    parameterKey = "version",
                )
            )
        }.body<MavenTriggerResponse>()
        assertTrue(created.trigger.enabled)

        val toggleResponse = client.put(ApiRoutes.MavenTriggers.byId(projectId, created.trigger.id)) {
            contentType(ContentType.Application.Json)
            setBody(ToggleMavenTriggerRequest(enabled = false))
        }
        assertEquals(HttpStatusCode.OK, toggleResponse.status)
        val toggled = toggleResponse.body<MavenTriggerResponse>()
        assertFalse(toggled.trigger.enabled)
    }

    @Test
    fun `delete maven trigger`() = testApplication {
        application { testModule() }
        val client = jsonClient()
        val teamId = client.loginAndCreateTeam()
        val projectId = client.createTestProject(teamId)

        val created = client.post(ApiRoutes.MavenTriggers.byProject(projectId)) {
            contentType(ContentType.Application.Json)
            setBody(
                CreateMavenTriggerRequest(
                    repoUrl = TEST_REPO_URL,
                    groupId = "com.example",
                    artifactId = "lib",
                    parameterKey = "version",
                )
            )
        }.body<MavenTriggerResponse>()

        val deleteResponse = client.delete(ApiRoutes.MavenTriggers.byId(projectId, created.trigger.id))
        assertEquals(HttpStatusCode.NoContent, deleteResponse.status)

        val listResponse = client.get(ApiRoutes.MavenTriggers.byProject(projectId))
        val body = listResponse.body<MavenTriggerListResponse>()
        assertTrue(body.triggers.isEmpty())
    }

    @Test
    fun `create trigger with invalid groupId returns 400`() = testApplication {
        application { testModule() }
        val client = jsonClient()
        val teamId = client.loginAndCreateTeam()
        val projectId = client.createTestProject(teamId)

        val response = client.post(ApiRoutes.MavenTriggers.byProject(projectId)) {
            contentType(ContentType.Application.Json)
            setBody(
                CreateMavenTriggerRequest(
                    repoUrl = TEST_REPO_URL,
                    groupId = "INVALID GROUP ID!",
                    artifactId = "my-library",
                    parameterKey = "version",
                )
            )
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `create trigger with blank parameterKey returns 400`() = testApplication {
        application { testModule() }
        val client = jsonClient()
        val teamId = client.loginAndCreateTeam()
        val projectId = client.createTestProject(teamId)

        val response = client.post(ApiRoutes.MavenTriggers.byProject(projectId)) {
            contentType(ContentType.Application.Json)
            setBody(
                CreateMavenTriggerRequest(
                    repoUrl = TEST_REPO_URL,
                    groupId = "com.example",
                    artifactId = "lib",
                    parameterKey = "",
                )
            )
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `unauthenticated request returns 401`() = testApplication {
        application { testModule() }
        val unauthClient = jsonClient() // no login — no session cookie

        val response = unauthClient.get(ApiRoutes.MavenTriggers.byProject("00000000-0000-0000-0000-000000000001"))
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `non-member user gets 403 on another teams project`() = testApplication {
        application { testModule() }

        // Admin user creates team and project
        val adminClient = jsonClient()
        val teamId = adminClient.loginAndCreateTeam("admin", "adminpass", "Admin Team")
        val projectId = adminClient.createTestProject(teamId)
        adminClient.post(ApiRoutes.MavenTriggers.byProject(projectId)) {
            contentType(ContentType.Application.Json)
            setBody(CreateMavenTriggerRequest(
                repoUrl = TEST_REPO_URL,
                groupId = "com.example",
                artifactId = "lib",
                parameterKey = "version",
            ))
        }

        // Second user (non-admin) — no membership in admin's team
        val userClient = jsonClient()
        userClient.post(ApiRoutes.Auth.REGISTER) {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest(username = "user2", password = "user2pass"))
        }
        userClient.post(ApiRoutes.Auth.LOGIN) {
            contentType(ContentType.Application.Json)
            setBody(LoginRequest(username = "user2", password = "user2pass"))
        }

        val response = userClient.get(ApiRoutes.MavenTriggers.byProject(projectId))
        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    @Test
    fun `create trigger with repoUrl without https scheme returns 400`() = testApplication {
        application { testModule() }
        val client = jsonClient()
        val teamId = client.loginAndCreateTeam()
        val projectId = client.createTestProject(teamId)

        val response = client.post(ApiRoutes.MavenTriggers.byProject(projectId)) {
            contentType(ContentType.Application.Json)
            setBody(CreateMavenTriggerRequest(
                repoUrl = "ftp://repo.example.com/maven2",
                groupId = "com.example",
                artifactId = "lib",
                parameterKey = "version",
            ))
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `create trigger with consecutive dots in groupId returns 400`() = testApplication {
        application { testModule() }
        val client = jsonClient()
        val teamId = client.loginAndCreateTeam()
        val projectId = client.createTestProject(teamId)

        val response = client.post(ApiRoutes.MavenTriggers.byProject(projectId)) {
            contentType(ContentType.Application.Json)
            setBody(CreateMavenTriggerRequest(
                repoUrl = TEST_REPO_URL,
                groupId = "com..example",
                artifactId = "lib",
                parameterKey = "version",
            ))
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `create trigger with blank artifactId returns 400`() = testApplication {
        application { testModule() }
        val client = jsonClient()
        val teamId = client.loginAndCreateTeam()
        val projectId = client.createTestProject(teamId)

        val response = client.post(ApiRoutes.MavenTriggers.byProject(projectId)) {
            contentType(ContentType.Application.Json)
            setBody(CreateMavenTriggerRequest(
                repoUrl = TEST_REPO_URL,
                groupId = "com.example",
                artifactId = "",
                parameterKey = "version",
            ))
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `get non-existent trigger returns 404`() = testApplication {
        application { testModule() }
        val client = jsonClient()
        val teamId = client.loginAndCreateTeam()
        val projectId = client.createTestProject(teamId)

        val response = client.get(ApiRoutes.MavenTriggers.byId(projectId, "00000000-0000-0000-0000-000000000000"))
        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `toggle non-existent trigger returns 404`() = testApplication {
        application { testModule() }
        val client = jsonClient()
        val teamId = client.loginAndCreateTeam()
        val projectId = client.createTestProject(teamId)

        val response = client.put(ApiRoutes.MavenTriggers.byId(projectId, "00000000-0000-0000-0000-000000000000")) {
            contentType(ContentType.Application.Json)
            setBody(ToggleMavenTriggerRequest(enabled = false))
        }
        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `delete non-existent trigger returns 404`() = testApplication {
        application { testModule() }
        val client = jsonClient()
        val teamId = client.loginAndCreateTeam()
        val projectId = client.createTestProject(teamId)

        val response = client.delete(ApiRoutes.MavenTriggers.byId(projectId, "00000000-0000-0000-0000-000000000000"))
        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `private IP repoUrl returns 400`() = testApplication {
        application { testModule() }
        val client = jsonClient()
        val teamId = client.loginAndCreateTeam()
        val projectId = client.createTestProject(teamId)

        val response = client.post(ApiRoutes.MavenTriggers.byProject(projectId)) {
            contentType(ContentType.Application.Json)
            setBody(CreateMavenTriggerRequest(
                repoUrl = "http://127.0.0.1/maven2",
                groupId = "com.example",
                artifactId = "lib",
                parameterKey = "version",
            ))
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }
}
