package com.github.mr3zee.execution

import com.github.mr3zee.model.*
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DispatchingBlockExecutorTest {

    private class FakeExecutor(private val outputs: Map<String, String> = mapOf("result" to "ok")) : BlockExecutor {
        var executeCalled = false
        override suspend fun execute(
            block: Block.ActionBlock,
            parameters: List<Parameter>,
            context: ExecutionContext,
        ): Map<String, String> {
            executeCalled = true
            return outputs
        }
    }

    private fun block(type: BlockType) = Block.ActionBlock(
        id = BlockId("test-block"),
        name = "Test",
        type = type,
    )

    private val context = ExecutionContext(
        releaseId = ReleaseId("r1"),
        parameters = emptyList(),
        blockOutputs = emptyMap(),
        connections = emptyMap(),
    )

    @Test
    fun `routes to correct executor for TEAMCITY_BUILD`() = runBlocking {
        val tcExecutor = FakeExecutor(mapOf("buildNumber" to "42"))
        val dispatcher = DispatchingBlockExecutor(mapOf(BlockType.TEAMCITY_BUILD to tcExecutor))

        val outputs = dispatcher.execute(block(BlockType.TEAMCITY_BUILD), emptyList(), context)
        assertTrue(tcExecutor.executeCalled)
        assertEquals("42", outputs["buildNumber"])
    }

    @Test
    fun `routes to correct executor for SLACK_MESSAGE`() = runBlocking {
        val slackExecutor = FakeExecutor(mapOf("channel" to "#releases"))
        val dispatcher = DispatchingBlockExecutor(mapOf(BlockType.SLACK_MESSAGE to slackExecutor))

        val outputs = dispatcher.execute(block(BlockType.SLACK_MESSAGE), emptyList(), context)
        assertTrue(slackExecutor.executeCalled)
        assertEquals("#releases", outputs["channel"])
    }

    @Test
    fun `routes to correct executor for GITHUB_ACTION`() = runBlocking {
        val ghExecutor = FakeExecutor(mapOf("runId" to "789"))
        val dispatcher = DispatchingBlockExecutor(mapOf(BlockType.GITHUB_ACTION to ghExecutor))

        val outputs = dispatcher.execute(block(BlockType.GITHUB_ACTION), emptyList(), context)
        assertTrue(ghExecutor.executeCalled)
        assertEquals("789", outputs["runId"])
    }

    @Test
    fun `routes to correct executor for GITHUB_PUBLICATION`() = runBlocking {
        val ghPubExecutor = FakeExecutor(mapOf("releaseUrl" to "https://github.com"))
        val dispatcher = DispatchingBlockExecutor(mapOf(BlockType.GITHUB_PUBLICATION to ghPubExecutor))

        // todo claude: unused
        val outputs = dispatcher.execute(block(BlockType.GITHUB_PUBLICATION), emptyList(), context)
        assertTrue(ghPubExecutor.executeCalled)
    }

    @Test
    fun `routes to correct executor for MAVEN_CENTRAL_PUBLICATION`() = runBlocking {
        val mavenExecutor = FakeExecutor(mapOf("status" to "PUBLISHED"))
        val dispatcher = DispatchingBlockExecutor(mapOf(BlockType.MAVEN_CENTRAL_PUBLICATION to mavenExecutor))

        val outputs = dispatcher.execute(block(BlockType.MAVEN_CENTRAL_PUBLICATION), emptyList(), context)
        assertTrue(mavenExecutor.executeCalled)
        assertEquals("PUBLISHED", outputs["status"])
    }

    @Test
    fun `unknown block type throws IllegalStateException`() = runBlocking {
        // Register only SLACK_MESSAGE executor, then try TEAMCITY_BUILD
        val dispatcher = DispatchingBlockExecutor(mapOf(BlockType.SLACK_MESSAGE to FakeExecutor()))

        val e = assertFailsWith<IllegalStateException> {
            dispatcher.execute(block(BlockType.TEAMCITY_BUILD), emptyList(), context)
        }
        val message = e.message ?: error("IllegalStateException should have a message")
        assertTrue(message.contains("No executor registered"))
        assertTrue(message.contains("TEAMCITY_BUILD"))
    }

    @Test
    fun `does not call wrong executor`() = runBlocking {
        val slackExecutor = FakeExecutor()
        val tcExecutor = FakeExecutor()
        val dispatcher = DispatchingBlockExecutor(mapOf(
            BlockType.SLACK_MESSAGE to slackExecutor,
            BlockType.TEAMCITY_BUILD to tcExecutor,
        ))

        dispatcher.execute(block(BlockType.SLACK_MESSAGE), emptyList(), context)
        assertTrue(slackExecutor.executeCalled, "Slack executor should be called")
        assertTrue(!tcExecutor.executeCalled, "TeamCity executor should NOT be called")
    }

    @Test
    fun `resume routes to correct executor`() = runBlocking {
        val tcExecutor = FakeExecutor(mapOf("resumed" to "true"))
        val dispatcher = DispatchingBlockExecutor(mapOf(BlockType.TEAMCITY_BUILD to tcExecutor))

        val outputs = dispatcher.resume(block(BlockType.TEAMCITY_BUILD), emptyList(), context)
        assertTrue(tcExecutor.executeCalled)
        assertEquals("true", outputs["resumed"])
    }
}
