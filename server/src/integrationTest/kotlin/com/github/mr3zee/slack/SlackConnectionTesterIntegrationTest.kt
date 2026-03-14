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
    fun `valid webhook URL format succeeds`() = runBlocking {
        val cfg = config!!
        val tester = ConnectionTester(client!!)
        val result = tester.test(
            ConnectionConfig.SlackConfig(webhookUrl = cfg.webhookUrl)
        )
        assertTrue(result.success, "Expected success but got: ${result.message}")
        assertTrue(result.message.contains("valid"), "Message should mention valid: ${result.message}")
    }

    @Test
    fun `invalid webhook URL format fails`() = runBlocking {
        val tester = ConnectionTester(client!!)
        val result = tester.test(
            ConnectionConfig.SlackConfig(webhookUrl = "https://example.com/not-a-slack-webhook")
        )
        assertFalse(result.success, "Expected failure for invalid URL format")
        assertTrue(result.message.contains("Invalid Slack webhook URL"), "Message: ${result.message}")
    }
}
