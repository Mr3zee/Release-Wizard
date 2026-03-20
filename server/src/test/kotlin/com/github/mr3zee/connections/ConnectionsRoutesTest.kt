package com.github.mr3zee.connections

import com.github.mr3zee.api.*
import com.github.mr3zee.jsonClient
import com.github.mr3zee.login
import com.github.mr3zee.loginAndCreateTeam
import com.github.mr3zee.testModule
import com.github.mr3zee.model.ConnectionConfig
import com.github.mr3zee.model.ConnectionType
import com.github.mr3zee.model.TeamId
import io.ktor.client.HttpClient
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ConnectionsRoutesTest {

    @Test
    fun `list connections returns empty list initially`() = testApplication {
        application { testModule() }
        val client = jsonClient()
        client.login()

        val response = client.get(ApiRoutes.Connections.BASE)
        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.body<ConnectionListResponse>()
        assertTrue(body.connections.isEmpty())
    }

    @Test
    fun `create and get connection`() = testApplication {
        application { testModule() }
        val client = jsonClient()
        val teamId = client.loginAndCreateTeam()

        val createResponse = client.post(ApiRoutes.Connections.BASE) {
            contentType(ContentType.Application.Json)
            setBody(
                CreateConnectionRequest(
                    name = "My GitHub",
                    teamId = teamId,
                    type = ConnectionType.GITHUB,
                    config = ConnectionConfig.GitHubConfig(
                        token = "ghp_1234567890abcdef",
                        owner = "mr3zee",
                        repo = "release-wizard",
                    ),
                )
            )
        }
        assertEquals(HttpStatusCode.Created, createResponse.status)
        val created = createResponse.body<ConnectionResponse>()
        assertEquals("My GitHub", created.connection.name)
        assertEquals(ConnectionType.GITHUB, created.connection.type)
        val config = created.connection.config as ConnectionConfig.GitHubConfig
        assertTrue(config.token.startsWith("****"))
        assertEquals("mr3zee", config.owner)
        assertEquals("release-wizard", config.repo)

        val getResponse = client.get(ApiRoutes.Connections.byId(created.connection.id.value))
        assertEquals(HttpStatusCode.OK, getResponse.status)
        val fetched = getResponse.body<ConnectionResponse>()
        assertEquals(created.connection.id, fetched.connection.id)
    }

    @Test
    fun `create connection with blank name returns 400`() = testApplication {
        application { testModule() }
        val client = jsonClient()
        val teamId = client.loginAndCreateTeam()

        val response = client.post(ApiRoutes.Connections.BASE) {
            contentType(ContentType.Application.Json)
            setBody(
                CreateConnectionRequest(
                    name = "",
                    teamId = teamId,
                    type = ConnectionType.SLACK,
                    config = ConnectionConfig.SlackConfig(webhookUrl = "https://hooks.slack.com/test"),
                )
            )
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `update connection`() = testApplication {
        application { testModule() }
        val client = jsonClient()
        val teamId = client.loginAndCreateTeam()

        val createResponse = client.post(ApiRoutes.Connections.BASE) {
            contentType(ContentType.Application.Json)
            setBody(
                CreateConnectionRequest(
                    name = "Original",
                    teamId = teamId,
                    type = ConnectionType.TEAMCITY,
                    config = ConnectionConfig.TeamCityConfig(
                        serverUrl = "https://tc.example.com",
                        token = "tc-token-1234",
                    ),
                )
            )
        }
        val created = createResponse.body<ConnectionResponse>()

        val updateResponse = client.put(ApiRoutes.Connections.byId(created.connection.id.value)) {
            contentType(ContentType.Application.Json)
            setBody(UpdateConnectionRequest(name = "Updated"))
        }
        assertEquals(HttpStatusCode.OK, updateResponse.status)
        val updated = updateResponse.body<ConnectionResponse>()
        assertEquals("Updated", updated.connection.name)
        assertEquals(created.connection.createdAt, updated.connection.createdAt)
        assertTrue(updated.connection.updatedAt >= created.connection.updatedAt)
    }

    @Test
    fun `update connection config`() = testApplication {
        application { testModule() }
        val client = jsonClient()
        val teamId = client.loginAndCreateTeam()

        val createResponse = client.post(ApiRoutes.Connections.BASE) {
            contentType(ContentType.Application.Json)
            setBody(
                CreateConnectionRequest(
                    name = "GitHub",
                    teamId = teamId,
                    type = ConnectionType.GITHUB,
                    config = ConnectionConfig.GitHubConfig(
                        token = "ghp_old_token",
                        owner = "old-owner",
                        repo = "old-repo",
                    ),
                )
            )
        }
        val created = createResponse.body<ConnectionResponse>()

        val updateResponse = client.put(ApiRoutes.Connections.byId(created.connection.id.value)) {
            contentType(ContentType.Application.Json)
            setBody(
                UpdateConnectionRequest(
                    config = ConnectionConfig.GitHubConfig(
                        token = "ghp_new_token_12345678",
                        owner = "new-owner",
                        repo = "new-repo",
                    ),
                )
            )
        }
        assertEquals(HttpStatusCode.OK, updateResponse.status)
        val updated = updateResponse.body<ConnectionResponse>()
        val config = updated.connection.config as ConnectionConfig.GitHubConfig
        assertEquals("new-owner", config.owner)
        assertEquals("new-repo", config.repo)
        assertEquals("********", config.token)
    }

    @Test
    fun `delete connection`() = testApplication {
        application { testModule() }
        val client = jsonClient()
        val teamId = client.loginAndCreateTeam()

        val createResponse = client.post(ApiRoutes.Connections.BASE) {
            contentType(ContentType.Application.Json)
            setBody(
                CreateConnectionRequest(
                    name = "To Delete",
                    teamId = teamId,
                    type = ConnectionType.SLACK,
                    config = ConnectionConfig.SlackConfig(webhookUrl = "https://hooks.slack.com/test"),
                )
            )
        }
        val created = createResponse.body<ConnectionResponse>()

        val deleteResponse = client.delete(ApiRoutes.Connections.byId(created.connection.id.value))
        assertEquals(HttpStatusCode.NoContent, deleteResponse.status)

        val getResponse = client.get(ApiRoutes.Connections.byId(created.connection.id.value))
        assertEquals(HttpStatusCode.NotFound, getResponse.status)
    }

    @Test
    fun `test connection endpoint`() = testApplication {
        application { testModule() }
        val client = jsonClient()
        val teamId = client.loginAndCreateTeam()

        val createResponse = client.post(ApiRoutes.Connections.BASE) {
            contentType(ContentType.Application.Json)
            setBody(
                CreateConnectionRequest(
                    name = "Test Conn",
                    teamId = teamId,
                    type = ConnectionType.SLACK,
                    config = ConnectionConfig.SlackConfig(
                        webhookUrl = "https://hooks.slack.com/services/T00/B00/xxx",
                    ),
                )
            )
        }
        val created = createResponse.body<ConnectionResponse>()

        val testResponse = client.post(ApiRoutes.Connections.test(created.connection.id.value))
        assertEquals(HttpStatusCode.OK, testResponse.status)
        val result = testResponse.body<ConnectionTestResult>()
        assertTrue(result.success)
    }

    @Test
    fun `unauthenticated connection request returns 401`() = testApplication {
        application { testModule() }
        val client = jsonClient()

        val response = client.get(ApiRoutes.Connections.BASE)
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    // --- Build Type Discovery ---

    private suspend fun createTcConnection(client: HttpClient, teamId: TeamId): ConnectionResponse {
        val response = client.post(ApiRoutes.Connections.BASE) {
            contentType(ContentType.Application.Json)
            setBody(
                CreateConnectionRequest(
                    name = "TC Server",
                    teamId = teamId,
                    type = ConnectionType.TEAMCITY,
                    config = ConnectionConfig.TeamCityConfig(
                        serverUrl = "https://tc.example.com",
                        token = "tc-token-12345678",
                    ),
                )
            )
        }
        return response.body()
    }

    @Test
    fun `fetch build types returns configs for teamcity connection`() = testApplication {
        application { testModule() }
        val client = jsonClient()
        val teamId = client.loginAndCreateTeam()
        val created = createTcConnection(client, teamId)

        val response = client.get(ApiRoutes.Connections.teamcityBuildTypes(created.connection.id.value))
        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.body<ExternalConfigsResponse>()
        assertTrue(body.configs.isNotEmpty())
        val build = body.configs.first()
        assertEquals("Proj_Build", build.id)
        assertEquals("Build", build.name)
        assertTrue(build.path.contains("Project"))
    }

    @Test
    fun `fetch build types returns 404 for unknown connection`() = testApplication {
        application { testModule() }
        val client = jsonClient()
        client.login()

        val response = client.get(ApiRoutes.Connections.teamcityBuildTypes("00000000-0000-0000-0000-000000000000"))
        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `fetch build types returns 400 for invalid connection id`() = testApplication {
        application { testModule() }
        val client = jsonClient()
        client.login()

        val response = client.get(ApiRoutes.Connections.teamcityBuildTypes("not-a-uuid"))
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `fetch build types returns 401 without auth`() = testApplication {
        application { testModule() }
        val client = jsonClient()

        val response = client.get(ApiRoutes.Connections.teamcityBuildTypes("00000000-0000-0000-0000-000000000000"))
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `fetch build type parameters returns params for teamcity connection`() = testApplication {
        application { testModule() }
        val client = jsonClient()
        val teamId = client.loginAndCreateTeam()
        val created = createTcConnection(client, teamId)

        val response = client.get(ApiRoutes.Connections.teamcityBuildTypeParameters(created.connection.id.value, "Proj_Build"))
        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.body<ExternalConfigParametersResponse>()
        assertTrue(body.parameters.isNotEmpty())
        val version = body.parameters.first()
        assertEquals("env.VERSION", version.name)
        assertEquals("Version", version.label)
        assertEquals("Release version", version.description)
    }

    @Test
    fun `fetch build type parameters returns 404 for unknown connection`() = testApplication {
        application { testModule() }
        val client = jsonClient()
        client.login()

        val response = client.get(ApiRoutes.Connections.teamcityBuildTypeParameters("00000000-0000-0000-0000-000000000000", "bt1"))
        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `fetch build type parameters returns 400 for non-teamcity connection`() = testApplication {
        application { testModule() }
        val client = jsonClient()
        val teamId = client.loginAndCreateTeam()

        val createResponse = client.post(ApiRoutes.Connections.BASE) {
            contentType(ContentType.Application.Json)
            setBody(
                CreateConnectionRequest(
                    name = "Slack Conn",
                    teamId = teamId,
                    type = ConnectionType.SLACK,
                    config = ConnectionConfig.SlackConfig(webhookUrl = "https://hooks.slack.com/test"),
                )
            )
        }
        val created = createResponse.body<ConnectionResponse>()

        // TC build types endpoint on a Slack connection should return 400
        val response = client.get(ApiRoutes.Connections.teamcityBuildTypes(created.connection.id.value))
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    // --- GitHub Workflow Discovery ---

    private suspend fun createGhConnection(client: HttpClient, teamId: TeamId): ConnectionResponse {
        val response = client.post(ApiRoutes.Connections.BASE) {
            contentType(ContentType.Application.Json)
            setBody(
                CreateConnectionRequest(
                    name = "GH Repo",
                    teamId = teamId,
                    type = ConnectionType.GITHUB,
                    config = ConnectionConfig.GitHubConfig(
                        token = "ghp_test12345678",
                        owner = "mr3zee",
                        repo = "release-wizard",
                    ),
                )
            )
        }
        return response.body()
    }

    @Test
    fun `fetch workflows returns configs for github connection`() = testApplication {
        application { testModule() }
        val client = jsonClient()
        val teamId = client.loginAndCreateTeam()
        val created = createGhConnection(client, teamId)

        val response = client.get(ApiRoutes.Connections.githubWorkflows(created.connection.id.value))
        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.body<ExternalConfigsResponse>()
        assertTrue(body.configs.isNotEmpty())
        assertEquals("CI", body.configs.first().name)
        assertEquals("ci.yml", body.configs.first().id)
    }

    @Test
    fun `fetch workflows returns 404 for unknown connection`() = testApplication {
        application { testModule() }
        val client = jsonClient()
        client.login()

        val response = client.get(ApiRoutes.Connections.githubWorkflows("00000000-0000-0000-0000-000000000000"))
        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `fetch workflow parameters returns parsed inputs from YAML`() = testApplication {
        application { testModule() }
        val client = jsonClient()
        val teamId = client.loginAndCreateTeam()
        val created = createGhConnection(client, teamId)

        val response = client.get(ApiRoutes.Connections.githubWorkflowParameters(created.connection.id.value, "ci.yml"))
        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.body<ExternalConfigParametersResponse>()
        assertTrue(body.parameters.isNotEmpty())
        val env = body.parameters.first()
        assertEquals("environment", env.name)
        assertEquals("staging", env.value)
        assertEquals("Target env", env.description)
    }

    @Test
    fun `credentials are masked in API responses`() = testApplication {
        application { testModule() }
        val client = jsonClient()
        val teamId = client.loginAndCreateTeam()

        val createResponse = client.post(ApiRoutes.Connections.BASE) {
            contentType(ContentType.Application.Json)
            setBody(
                CreateConnectionRequest(
                    name = "Encrypted Test",
                    teamId = teamId,
                    type = ConnectionType.GITHUB,
                    config = ConnectionConfig.GitHubConfig(
                        token = "ghp_supersecret",
                        owner = "test",
                        repo = "test",
                    ),
                )
            )
        }
        val created = createResponse.body<ConnectionResponse>()

        val config = created.connection.config as ConnectionConfig.GitHubConfig
        assertTrue(config.token.contains("****"))
        assertTrue(!config.token.contains("supersecret"))
    }
}
