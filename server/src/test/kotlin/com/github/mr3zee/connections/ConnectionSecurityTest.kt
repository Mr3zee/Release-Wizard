package com.github.mr3zee.connections

import com.github.mr3zee.api.*
import com.github.mr3zee.jsonClient
import com.github.mr3zee.loginAndCreateTeam
import com.github.mr3zee.testModule
import com.github.mr3zee.model.ConnectionConfig
import com.github.mr3zee.model.ConnectionType
import io.ktor.client.call.*
import io.ktor.client.engine.mock.*
import io.ktor.client.request.*
import io.ktor.client.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ConnectionSecurityTest {

    @Test
    fun `mask returns fixed asterisks with no suffix leak`() {
        val config = ConnectionConfig.GitHubConfig(
            token = "ghp_supersecrettoken1234567890",
            owner = "test",
            repo = "repo",
        )
        testApplication {
            application { testModule() }
            val client = jsonClient()
            val teamId = client.loginAndCreateTeam()

            val createResponse = client.post(ApiRoutes.Connections.BASE) {
                contentType(ContentType.Application.Json)
                setBody(
                    CreateConnectionRequest(
                        name = "Mask Test",
                        teamId = teamId,
                        type = ConnectionType.GITHUB,
                        config = config,
                    )
                )
            }
            assertEquals(HttpStatusCode.Created, createResponse.status)
            val created = createResponse.body<ConnectionResponse>()
            val maskedConfig = created.connection.config as ConnectionConfig.GitHubConfig
            assertEquals("********", maskedConfig.token)
            assertFalse(maskedConfig.token.contains("1234567890"))
            assertFalse(maskedConfig.token.contains("7890"))
        }
    }

    @Test
    fun `mask returns fixed asterisks for short secrets`() {
        testApplication {
            application { testModule() }
            val client = jsonClient()
            val teamId = client.loginAndCreateTeam()

            val createResponse = client.post(ApiRoutes.Connections.BASE) {
                contentType(ContentType.Application.Json)
                setBody(
                    CreateConnectionRequest(
                        name = "Short Secret",
                        teamId = teamId,
                        type = ConnectionType.GITHUB,
                        config = ConnectionConfig.GitHubConfig(token = "ab", owner = "o", repo = "r"),
                    )
                )
            }
            assertEquals(HttpStatusCode.Created, createResponse.status)
            val created = createResponse.body<ConnectionResponse>()
            val maskedConfig = created.connection.config as ConnectionConfig.GitHubConfig
            assertEquals("********", maskedConfig.token)
        }
    }

    @Test
    fun `update with masked config preserves original secret`() = testApplication {
        application { testModule() }
        val client = jsonClient()
        val teamId = client.loginAndCreateTeam()

        val createResponse = client.post(ApiRoutes.Connections.BASE) {
            contentType(ContentType.Application.Json)
            setBody(
                CreateConnectionRequest(
                    name = "Preserve Secret",
                    teamId = teamId,
                    type = ConnectionType.GITHUB,
                    config = ConnectionConfig.GitHubConfig(
                        token = "ghp_original_secret_value",
                        owner = "original-owner",
                        repo = "original-repo",
                    ),
                )
            )
        }
        assertEquals(HttpStatusCode.Created, createResponse.status)
        val created = createResponse.body<ConnectionResponse>()

        val updateResponse = client.put(ApiRoutes.Connections.byId(created.connection.id.value)) {
            contentType(ContentType.Application.Json)
            setBody(
                UpdateConnectionRequest(
                    config = ConnectionConfig.GitHubConfig(
                        token = "********",
                        owner = "new-owner",
                        repo = "new-repo",
                    ),
                )
            )
        }
        assertEquals(HttpStatusCode.OK, updateResponse.status)
        val updated = updateResponse.body<ConnectionResponse>()
        val updatedConfig = updated.connection.config as ConnectionConfig.GitHubConfig
        assertEquals("new-owner", updatedConfig.owner)
        assertEquals("new-repo", updatedConfig.repo)
        assertEquals("********", updatedConfig.token)

        val testResult = client.post(ApiRoutes.Connections.test(created.connection.id.value))
        assertEquals(HttpStatusCode.OK, testResult.status)
        val result = testResult.body<ConnectionTestResult>()
        assertTrue(result.success)
    }

    @Test
    fun `update with masked slack config preserves webhook url`() = testApplication {
        application { testModule() }
        val client = jsonClient()
        val teamId = client.loginAndCreateTeam()

        val createResponse = client.post(ApiRoutes.Connections.BASE) {
            contentType(ContentType.Application.Json)
            setBody(
                CreateConnectionRequest(
                    name = "Slack Preserve",
                    teamId = teamId,
                    type = ConnectionType.SLACK,
                    config = ConnectionConfig.SlackConfig(
                        webhookUrl = "https://hooks.slack.com/services/T00/B00/original",
                    ),
                )
            )
        }
        assertEquals(HttpStatusCode.Created, createResponse.status)
        val created = createResponse.body<ConnectionResponse>()

        val updateResponse = client.put(ApiRoutes.Connections.byId(created.connection.id.value)) {
            contentType(ContentType.Application.Json)
            setBody(
                UpdateConnectionRequest(
                    name = "Slack Updated Name",
                    config = ConnectionConfig.SlackConfig(webhookUrl = "********"),
                )
            )
        }
        assertEquals(HttpStatusCode.OK, updateResponse.status)
        val updated = updateResponse.body<ConnectionResponse>()
        assertEquals("Slack Updated Name", updated.connection.name)
    }

    @Test
    fun `update with new real token replaces secret`() = testApplication {
        application { testModule() }
        val client = jsonClient()
        val teamId = client.loginAndCreateTeam()

        val createResponse = client.post(ApiRoutes.Connections.BASE) {
            contentType(ContentType.Application.Json)
            setBody(
                CreateConnectionRequest(
                    name = "Replace Token",
                    teamId = teamId,
                    type = ConnectionType.TEAMCITY,
                    config = ConnectionConfig.TeamCityConfig(
                        serverUrl = "https://tc.example.com",
                        token = "old-token-value",
                    ),
                )
            )
        }
        assertEquals(HttpStatusCode.Created, createResponse.status)
        val created = createResponse.body<ConnectionResponse>()

        val updateResponse = client.put(ApiRoutes.Connections.byId(created.connection.id.value)) {
            contentType(ContentType.Application.Json)
            setBody(
                UpdateConnectionRequest(
                    config = ConnectionConfig.TeamCityConfig(
                        serverUrl = "https://tc.example.com",
                        token = "brand-new-token-12345",
                    ),
                )
            )
        }
        assertEquals(HttpStatusCode.OK, updateResponse.status)
        val updated = updateResponse.body<ConnectionResponse>()
        val updatedConfig = updated.connection.config as ConnectionConfig.TeamCityConfig
        assertEquals("********", updatedConfig.token)
    }

    @Test
    fun `testGitHub encodes owner and repo in URL`() = runTest {
        var capturedUrl: String? = null
        val tester = ConnectionTester(HttpClient(MockEngine { request ->
            capturedUrl = request.url.toString()
            respond("""{"id":1,"full_name":"test/repo"}""", HttpStatusCode.OK,
                headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))
        }))

        val config = ConnectionConfig.GitHubConfig(
            token = "ghp_test",
            owner = "owner/with-slash",
            repo = "repo with spaces",
        )
        tester.test(config)

        val url = capturedUrl ?: error("URL should have been captured")
        assertTrue(url.contains("owner%2Fwith-slash"), "Owner slash should be encoded, got: $url")
        assertTrue(url.contains("repo%20with%20spaces"), "Repo spaces should be encoded, got: $url")
        assertFalse(url.contains("owner/with-slash/repo"), "Raw slash should not appear in path, got: $url")
    }
}
