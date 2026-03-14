@file:Suppress("FunctionName")

package com.github.mr3zee.slack

import com.github.mr3zee.execution.ExecutionContext
import com.github.mr3zee.execution.executors.SlackMessageExecutor
import com.github.mr3zee.model.*
import io.ktor.client.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.fail
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SlackMessageExecutorIntegrationTest {

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

    private fun block() = Block.ActionBlock(
        id = BlockId("slack-integ"),
        name = "Integration Slack Message",
        type = BlockType.SLACK_MESSAGE,
        connectionId = ConnectionId("conn-slack"),
    )

    private fun context(): ExecutionContext {
        // todo claude: proper null handling
        val cfg = config!!
        return ExecutionContext(
            releaseId = ReleaseId("integ-release-slack"),
            parameters = emptyList(),
            blockOutputs = emptyMap(),
            connections = mapOf(
                ConnectionId("conn-slack") to ConnectionConfig.SlackConfig(
                    webhookUrl = cfg.webhookUrl,
                )
            ),
        )
    }

    @Test
    fun `execute sends message via webhook and message arrives in channel`() = runBlocking {
        // todo claude: proper null handling
        val cfg = config!!
        val uuid = UUID.randomUUID().toString()
        val text = "Integration test message $uuid"
        // todo claude: proper null handling
        val executor = SlackMessageExecutor(client!!)

        val outputs = executor.execute(
            block = block(),
            parameters = listOf(Parameter(key = "text", value = text)),
            context = context(),
        )

        assertEquals("sent", outputs["messageTs"])

        // Verify message arrived via Bot API
        // todo claude: proper null handling
        val message = client!!.findSlackMessageByText(
            botToken = cfg.botToken,
            channelId = cfg.channelId,
            textSubstring = uuid,
        )
        assertNotNull(message, "Message with UUID should be found in channel")
        assertTrue(message.text.contains(uuid), "Message text should contain UUID")
    }

    @Test
    fun `execute sends message with explicit channel parameter`() = runBlocking {
        // todo claude: proper null handling
        val cfg = config!!
        val uuid = UUID.randomUUID().toString()
        val text = "Channel param test $uuid"
        // todo claude: proper null handling
        val executor = SlackMessageExecutor(client!!)

        val outputs = executor.execute(
            block = block(),
            parameters = listOf(
                Parameter(key = "text", value = text),
                Parameter(key = "channel", value = "#ci-integration-tests"),
            ),
            context = context(),
        )

        assertEquals("sent", outputs["messageTs"])
        assertEquals("#ci-integration-tests", outputs["channel"])

        // Verify message arrived
        // todo claude: proper null handling
        val message = client!!.findSlackMessageByText(
            botToken = cfg.botToken,
            channelId = cfg.channelId,
            textSubstring = uuid,
        )
        assertTrue(message.text.contains(uuid), "Message text should contain UUID")
    }

    @Test
    fun `execute with invalid webhook URL throws`() = runBlocking {
        // todo claude: proper null handling
        val executor = SlackMessageExecutor(client!!)

        val invalidContext = ExecutionContext(
            releaseId = ReleaseId("integ-release-slack"),
            parameters = emptyList(),
            blockOutputs = emptyMap(),
            connections = mapOf(
                ConnectionId("conn-slack") to ConnectionConfig.SlackConfig(
                    webhookUrl = "https://hooks.slack.com/services/T000/B000/invalid",
                )
            ),
        )

        try {
            executor.execute(
                block = block(),
                parameters = listOf(Parameter(key = "text", value = "should fail")),
                context = invalidContext,
            )
            fail("Should have thrown RuntimeException")
        } catch (e: RuntimeException) {
            // todo claude: proper null handling
            assertTrue(e.message!!.contains("Slack webhook failed"), "Message: ${e.message}")
        }
    }
}
