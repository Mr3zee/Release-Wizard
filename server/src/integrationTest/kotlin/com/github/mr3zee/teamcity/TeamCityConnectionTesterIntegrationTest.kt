@file:Suppress("FunctionName")

package com.github.mr3zee.teamcity

import com.github.mr3zee.connections.ConnectionTester
import com.github.mr3zee.model.ConnectionConfig
import io.ktor.client.*
import kotlinx.coroutines.runBlocking
import org.junit.AfterClass
import org.junit.Assume
import org.junit.BeforeClass
import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TeamCityConnectionTesterIntegrationTest {

    companion object {
        private var config: TeamCityTestConfig? = null
        private var client: HttpClient? = null

        @BeforeClass
        @JvmStatic
        fun setUp() {
            config = TeamCityTestConfig.loadOrNull()
            Assume.assumeNotNull(config)
            client = createTeamCityTestHttpClient()
        }

        @AfterClass
        @JvmStatic
        fun tearDown() {
            client?.close()
        }
    }

    @Test
    fun `valid token and server succeeds`() = runBlocking {
        val cfg = config ?: error("TeamCityTestConfig not loaded — setUp should have skipped this test")
        val tester = ConnectionTester(client ?: error("HttpClient not initialized"))
        val result = tester.test(
            ConnectionConfig.TeamCityConfig(
                serverUrl = cfg.serverUrl,
                token = cfg.token,
            )
        )
        assertTrue(result.success, "Expected success but got: ${result.message}")
        assertTrue(result.message.contains("Connected"), "Message should mention connected: ${result.message}")
    }

    @Test
    fun `invalid token fails`() = runBlocking {
        val cfg = config ?: error("TeamCityTestConfig not loaded — setUp should have skipped this test")
        val tester = ConnectionTester(client ?: error("HttpClient not initialized"))
        val result = tester.test(
            ConnectionConfig.TeamCityConfig(
                serverUrl = cfg.serverUrl,
                token = "invalid-token-${System.currentTimeMillis()}",
            )
        )
        assertFalse(result.success, "Expected failure for invalid token")
        assertTrue(result.message.contains("401"), "Message should contain 401 status: ${result.message}")
    }

    @Test
    fun `unreachable server fails`() = runBlocking {
        val tester = ConnectionTester(client ?: error("HttpClient not initialized"))
        // Use a non-routable IP (RFC 5737 TEST-NET-1) to avoid SSRF validation rejecting the URL
        // and to guarantee a connection timeout rather than DNS resolution issues
        val result = tester.test(
            ConnectionConfig.TeamCityConfig(
                serverUrl = "https://192.0.2.1",
                token = "any-token",
            )
        )
        assertFalse(result.success, "Expected failure for unreachable server")
        assertTrue(result.message.contains("Failed to connect"), "Message: ${result.message}")
    }
}
