package com.github.mr3zee.webhooks

import com.github.mr3zee.api.*
import com.github.mr3zee.jsonClient
import com.github.mr3zee.model.ConnectionConfig
import com.github.mr3zee.model.ConnectionType
import com.github.mr3zee.testModule
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.testing.*
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WebhookRoutesTest {

    private suspend fun HttpClient.login() {
        post(ApiRoutes.Auth.LOGIN) {
            contentType(ContentType.Application.Json)
            setBody(LoginRequest(username = "admin", password = "admin"))
        }
    }

    private suspend fun HttpClient.createConnection(
        name: String,
        type: ConnectionType,
        config: ConnectionConfig,
    ): ConnectionResponse {
        val response = post(ApiRoutes.Connections.BASE) {
            contentType(ContentType.Application.Json)
            setBody(CreateConnectionRequest(name = name, type = type, config = config))
        }
        return response.body()
    }

    @Test
    fun `teamcity webhook without pending webhooks returns OK`() = testApplication {
        application { testModule() }
        val client = jsonClient()
        client.login()

        val conn = client.createConnection(
            "TC", ConnectionType.TEAMCITY,
            ConnectionConfig.TeamCityConfig(serverUrl = "https://tc.example.com", token = "token"),
        )

        val response = client.post(ApiRoutes.Webhooks.teamcity(conn.connection.id.value)) {
            setBody("{\"build\":{\"buildId\":\"42\"}}")
            contentType(ContentType.Application.Json)
        }
        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `teamcity webhook with valid secret accepted`() = testApplication {
        application { testModule() }
        val client = jsonClient()
        client.login()

        val conn = client.createConnection(
            "TC Secret", ConnectionType.TEAMCITY,
            ConnectionConfig.TeamCityConfig(
                serverUrl = "https://tc.example.com",
                token = "token",
                webhookSecret = "my-secret-123",
            ),
        )

        val response = client.post(ApiRoutes.Webhooks.teamcity(conn.connection.id.value)) {
            header("X-Webhook-Secret", "my-secret-123")
            setBody("{\"build\":{\"buildId\":\"42\"}}")
            contentType(ContentType.Application.Json)
        }
        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `teamcity webhook with wrong secret returns 401`() = testApplication {
        application { testModule() }
        val client = jsonClient()
        client.login()

        val conn = client.createConnection(
            "TC Secret", ConnectionType.TEAMCITY,
            ConnectionConfig.TeamCityConfig(
                serverUrl = "https://tc.example.com",
                token = "token",
                webhookSecret = "correct-secret",
            ),
        )

        val response = client.post(ApiRoutes.Webhooks.teamcity(conn.connection.id.value)) {
            header("X-Webhook-Secret", "wrong-secret")
            setBody("{\"build\":{\"buildId\":\"42\"}}")
            contentType(ContentType.Application.Json)
        }
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `teamcity webhook with no secret when required returns 401`() = testApplication {
        application { testModule() }
        val client = jsonClient()
        client.login()

        val conn = client.createConnection(
            "TC Secret", ConnectionType.TEAMCITY,
            ConnectionConfig.TeamCityConfig(
                serverUrl = "https://tc.example.com",
                token = "token",
                webhookSecret = "secret",
            ),
        )

        val response = client.post(ApiRoutes.Webhooks.teamcity(conn.connection.id.value)) {
            setBody("{\"build\":{\"buildId\":\"42\"}}")
            contentType(ContentType.Application.Json)
        }
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `github webhook with valid HMAC accepted`() = testApplication {
        application { testModule() }
        val client = jsonClient()
        client.login()

        val secret = "gh-webhook-secret"
        val conn = client.createConnection(
            "GitHub", ConnectionType.GITHUB,
            ConnectionConfig.GitHubConfig(
                token = "ghp_test",
                owner = "test",
                repo = "test",
                webhookSecret = secret,
            ),
        )

        val payload = """{"action":"completed","workflow_run":{"id":123}}"""
        val signature = computeGitHubSignature(payload, secret)

        val response = client.post(ApiRoutes.Webhooks.github(conn.connection.id.value)) {
            header("X-Hub-Signature-256", signature)
            setBody(payload)
            contentType(ContentType.Application.Json)
        }
        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `github webhook with invalid HMAC returns 401`() = testApplication {
        application { testModule() }
        val client = jsonClient()
        client.login()

        val conn = client.createConnection(
            "GitHub", ConnectionType.GITHUB,
            ConnectionConfig.GitHubConfig(
                token = "ghp_test",
                owner = "test",
                repo = "test",
                webhookSecret = "correct-secret",
            ),
        )

        val payload = """{"action":"completed"}"""
        val wrongSignature = computeGitHubSignature(payload, "wrong-secret")

        val response = client.post(ApiRoutes.Webhooks.github(conn.connection.id.value)) {
            header("X-Hub-Signature-256", wrongSignature)
            setBody(payload)
            contentType(ContentType.Application.Json)
        }
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `github webhook without signature when secret required returns 401`() = testApplication {
        application { testModule() }
        val client = jsonClient()
        client.login()

        val conn = client.createConnection(
            "GitHub", ConnectionType.GITHUB,
            ConnectionConfig.GitHubConfig(
                token = "ghp_test",
                owner = "test",
                repo = "test",
                webhookSecret = "secret",
            ),
        )

        val response = client.post(ApiRoutes.Webhooks.github(conn.connection.id.value)) {
            setBody("""{"action":"completed"}""")
            contentType(ContentType.Application.Json)
        }
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `webhook to nonexistent connection returns 404`() = testApplication {
        application { testModule() }
        val client = jsonClient()

        val response = client.post(ApiRoutes.Webhooks.teamcity("00000000-0000-0000-0000-000000000000")) {
            setBody("{}")
            contentType(ContentType.Application.Json)
        }
        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `webhook to invalid connection ID returns 400`() = testApplication {
        application { testModule() }
        val client = jsonClient()

        val response = client.post(ApiRoutes.Webhooks.teamcity("not-a-uuid")) {
            setBody("{}")
            contentType(ContentType.Application.Json)
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `teamcity webhook to github connection returns 400`() = testApplication {
        application { testModule() }
        val client = jsonClient()
        client.login()

        val conn = client.createConnection(
            "GitHub", ConnectionType.GITHUB,
            ConnectionConfig.GitHubConfig(token = "ghp_test", owner = "test", repo = "test"),
        )

        val response = client.post(ApiRoutes.Webhooks.teamcity(conn.connection.id.value)) {
            setBody("{}")
            contentType(ContentType.Application.Json)
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `webhook endpoints do not require authentication`() = testApplication {
        application { testModule() }
        val client = jsonClient()
        // Note: NOT logging in

        // First create a connection (need auth for that)
        val authedClient = jsonClient()
        authedClient.login()
        val conn = authedClient.createConnection(
            "TC", ConnectionType.TEAMCITY,
            ConnectionConfig.TeamCityConfig(serverUrl = "https://tc.example.com", token = "token"),
        )

        // Unauthenticated webhook request should NOT return 401
        val response = client.post(ApiRoutes.Webhooks.teamcity(conn.connection.id.value)) {
            setBody("{}")
            contentType(ContentType.Application.Json)
        }
        // Should be OK (no pending webhooks), not 401
        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `github HMAC verification function works correctly`() {
        val payload = "Hello, World!"
        val secret = "test-secret"
        val signature = computeGitHubSignature(payload, secret)

        assertTrue(WebhookService.verifyGitHubSignature(payload, signature, secret))
        assertTrue(!WebhookService.verifyGitHubSignature(payload, signature, "wrong-secret"))
        assertTrue(!WebhookService.verifyGitHubSignature("tampered", signature, secret))
        assertTrue(!WebhookService.verifyGitHubSignature(payload, "sha256=invalid", secret))
        assertTrue(!WebhookService.verifyGitHubSignature(payload, "invalid-format", secret))
    }

    private fun computeGitHubSignature(payload: String, secret: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(), "HmacSHA256"))
        val hash = mac.doFinal(payload.toByteArray())
        return "sha256=" + hash.joinToString("") { "%02x".format(it) }
    }
}
