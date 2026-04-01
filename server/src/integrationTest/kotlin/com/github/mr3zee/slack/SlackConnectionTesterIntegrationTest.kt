@file:Suppress("FunctionName")

package com.github.mr3zee.slack

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

class SlackConnectionTesterIntegrationTest {

    companion object {
        private var config: SlackTestConfig? = null
        private var client: HttpClient? = null

        @BeforeClass
        @JvmStatic
        fun setUp() {
            config = SlackTestConfig.loadOrNull()
            Assume.assumeNotNull(config)
            client = createSlackTestHttpClient()
        }

        @AfterClass
        @JvmStatic
        fun tearDown() {
            client?.close()
        }
    }

    @Test
    fun `valid bot token succeeds`() = runBlocking {
        val cfg = config ?: error("SlackTestConfig not loaded — setUp should have skipped this test")
        val tester = ConnectionTester(client ?: error("HttpClient not initialized"))
        val result = tester.test(
            ConnectionConfig.SlackConfig(botToken = cfg.botToken)
        )
        assertTrue(result.success, "Expected success but got: ${result.message}")
        assertTrue(result.message.contains("Connected to Slack workspace"), "Message should mention workspace: ${result.message}")
    }

    @Test
    fun `invalid bot token fails`() = runBlocking {
        val tester = ConnectionTester(client ?: error("HttpClient not initialized"))
        val result = tester.test(
            ConnectionConfig.SlackConfig(botToken = "xoxb-invalid-token-000")
        )
        assertFalse(result.success, "Expected failure for invalid bot token")
        assertTrue(result.message.contains("invalid_auth") || result.message.contains("not_authed"), "Message: ${result.message}")
    }
}
