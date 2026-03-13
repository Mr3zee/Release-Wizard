package com.github.mr3zee.connections

import com.github.mr3zee.model.ConnectionConfig
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ConnectionTesterTest {

    private fun createTester(handler: MockRequestHandler): ConnectionTester {
        return ConnectionTester(HttpClient(MockEngine(handler)))
    }

    // Slack

    @Test
    fun `slack valid webhook URL succeeds`() = runTest {
        val tester = createTester { respondOk() }
        val result = tester.test(ConnectionConfig.SlackConfig(webhookUrl = "https://hooks.slack.com/services/T00/B00/xxx"))
        assertTrue(result.success)
    }

    @Test
    fun `slack invalid webhook URL fails`() = runTest {
        val tester = createTester { respondOk() }
        val result = tester.test(ConnectionConfig.SlackConfig(webhookUrl = "https://example.com/hook"))
        assertFalse(result.success)
        assertTrue(result.message.contains("Invalid Slack webhook URL"))
    }

    // TeamCity

    @Test
    fun `teamcity successful connection`() = runTest {
        val tester = createTester { respond("""{"version":"2023.11"}""", HttpStatusCode.OK) }
        val result = tester.test(ConnectionConfig.TeamCityConfig(serverUrl = "https://tc.example.com", token = "token"))
        assertTrue(result.success)
        assertEquals("Connected to TeamCity server", result.message)
    }

    @Test
    fun `teamcity unauthorized returns failure`() = runTest {
        val tester = createTester { respond("", HttpStatusCode.Unauthorized) }
        val result = tester.test(ConnectionConfig.TeamCityConfig(serverUrl = "https://tc.example.com", token = "bad"))
        assertFalse(result.success)
        assertTrue(result.message.contains("401"))
    }

    @Test
    fun `teamcity connection error returns failure`() = runTest {
        val tester = createTester { throw RuntimeException("Connection refused") }
        val result = tester.test(ConnectionConfig.TeamCityConfig(serverUrl = "https://tc.example.com", token = "token"))
        assertFalse(result.success)
        assertTrue(result.message.contains("Connection refused"))
    }

    // GitHub

    @Test
    fun `github successful connection`() = runTest {
        val tester = createTester { respond("""{"id":1}""", HttpStatusCode.OK) }
        val result = tester.test(ConnectionConfig.GitHubConfig(token = "ghp_123", owner = "mr3zee", repo = "rw"))
        assertTrue(result.success)
        assertTrue(result.message.contains("mr3zee/rw"))
    }

    @Test
    fun `github not found returns failure`() = runTest {
        val tester = createTester { respond("", HttpStatusCode.NotFound) }
        val result = tester.test(ConnectionConfig.GitHubConfig(token = "ghp_123", owner = "bad", repo = "repo"))
        assertFalse(result.success)
        assertTrue(result.message.contains("404"))
    }

    @Test
    fun `github connection error returns failure`() = runTest {
        val tester = createTester { throw RuntimeException("DNS resolution failed") }
        val result = tester.test(ConnectionConfig.GitHubConfig(token = "ghp_123", owner = "o", repo = "r"))
        assertFalse(result.success)
        assertTrue(result.message.contains("DNS resolution failed"))
    }

    // Maven Central

    @Test
    fun `maven central successful connection`() = runTest {
        val tester = createTester { respond("""{"status":"ok"}""", HttpStatusCode.OK) }
        val result = tester.test(ConnectionConfig.MavenCentralConfig(username = "user", password = "pass"))
        assertTrue(result.success)
        assertEquals("Connected to Maven Central", result.message)
    }

    @Test
    fun `maven central forbidden returns failure`() = runTest {
        val tester = createTester { respond("", HttpStatusCode.Forbidden) }
        val result = tester.test(ConnectionConfig.MavenCentralConfig(username = "user", password = "bad"))
        assertFalse(result.success)
        assertTrue(result.message.contains("403"))
    }

    @Test
    fun `maven central connection error returns failure`() = runTest {
        val tester = createTester { throw RuntimeException("Timeout") }
        val result = tester.test(ConnectionConfig.MavenCentralConfig(username = "user", password = "pass"))
        assertFalse(result.success)
        assertTrue(result.message.contains("Timeout"))
    }
}
