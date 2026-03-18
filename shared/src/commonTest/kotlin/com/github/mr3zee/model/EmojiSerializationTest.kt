package com.github.mr3zee.model

import com.github.mr3zee.AppJson
import com.github.mr3zee.api.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock

class EmojiSerializationTest {
    private val json = AppJson

    @Test
    fun `parameter with emoji key round trip`() {
        val param = Parameter(
            key = "\uD83D\uDE80 deploy_env",
            value = "production",
            description = "\uD83C\uDF1F Deployment environment",
        )
        val encoded = json.encodeToString(Parameter.serializer(), param)
        val decoded = json.decodeFromString(Parameter.serializer(), encoded)
        assertEquals(param, decoded)
    }

    @Test
    fun `parameter with emoji value round trip`() {
        val param = Parameter(
            key = "status",
            value = "\u2705 Ready",
        )
        val encoded = json.encodeToString(Parameter.serializer(), param)
        val decoded = json.decodeFromString(Parameter.serializer(), encoded)
        assertEquals(param, decoded)
    }

    @Test
    fun `block name with emoji round trip`() {
        val block: Block = Block.ActionBlock(
            id = BlockId("b1"),
            name = "\uD83D\uDEE0\uFE0F Build & Test",
            type = BlockType.TEAMCITY_BUILD,
            parameters = listOf(
                Parameter("\uD83D\uDCE6 artifact", "release.zip"),
            ),
            outputs = listOf("\uD83D\uDCCA report"),
        )
        val encoded = json.encodeToString(Block.serializer(), block)
        val decoded = json.decodeFromString(Block.serializer(), encoded)
        assertEquals(block, decoded)
        assertTrue(decoded is Block.ActionBlock)
        assertEquals("\uD83D\uDEE0\uFE0F Build & Test", decoded.name)
        assertEquals("\uD83D\uDCE6 artifact", decoded.parameters.first().key)
        assertEquals("\uD83D\uDCCA report", decoded.outputs.first())
    }

    @Test
    fun `project template with emoji name and description round trip`() {
        val now = Clock.System.now()
        val template = ProjectTemplate(
            id = ProjectId("p1"),
            name = "\uD83D\uDE80 Release Pipeline",
            description = "\uD83C\uDF89 Automated release with \uD83D\uDD25 hot deploys",
            dagGraph = DagGraph(),
            parameters = listOf(
                Parameter("version", "1.0.0", "\uD83D\uDCCB Version number"),
            ),
            createdAt = now,
            updatedAt = now,
        )
        val encoded = json.encodeToString(ProjectTemplate.serializer(), template)
        val decoded = json.decodeFromString(ProjectTemplate.serializer(), encoded)
        assertEquals(template, decoded)
    }

    @Test
    fun `connection with emoji name round trip`() {
        val now = Clock.System.now()
        val connection = Connection(
            id = ConnectionId("c1"),
            name = "\uD83D\uDCE2 Alert Channel",
            type = ConnectionType.SLACK,
            config = ConnectionConfig.SlackConfig(webhookUrl = "https://hooks.slack.com/test"),
            createdAt = now,
            updatedAt = now,
        )
        val encoded = json.encodeToString(Connection.serializer(), connection)
        val decoded = json.decodeFromString(Connection.serializer(), encoded)
        assertEquals(connection, decoded)
        assertEquals("\uD83D\uDCE2 Alert Channel", decoded.name)
    }

    @Test
    fun `create project request with emoji round trip`() {
        val request = CreateProjectRequest(
            name = "\uD83C\uDF1F Stellar Release",
            teamId = TeamId("test-team"),
            description = "Deploy with \uD83D\uDE80",
            parameters = listOf(
                Parameter("\uD83D\uDD11 api_key", "secret", "\uD83D\uDD12 API key for deployment"),
            ),
        )
        val encoded = json.encodeToString(CreateProjectRequest.serializer(), request)
        val decoded = json.decodeFromString(CreateProjectRequest.serializer(), encoded)
        assertEquals(request, decoded)
    }

    @Test
    fun `block execution error with emoji round trip`() {
        val execution = BlockExecution(
            blockId = BlockId("b1"),
            releaseId = ReleaseId("r1"),
            status = BlockStatus.FAILED,
            error = "\u274C Connection timeout \uD83D\uDE1E",
        )
        val encoded = json.encodeToString(BlockExecution.serializer(), execution)
        val decoded = json.decodeFromString(BlockExecution.serializer(), encoded)
        assertEquals(execution, decoded)
        assertEquals("\u274C Connection timeout \uD83D\uDE1E", decoded.error)
    }

    @Test
    fun `block execution outputs with emoji keys and values round trip`() {
        val now = Clock.System.now()
        val execution = BlockExecution(
            blockId = BlockId("b1"),
            releaseId = ReleaseId("r1"),
            status = BlockStatus.SUCCEEDED,
            outputs = mapOf(
                "\uD83D\uDCE6 artifact" to "release-v2.0.zip",
                "\uD83D\uDCCA report" to "\u2705 All tests passed",
            ),
            startedAt = now,
            finishedAt = now,
        )
        val encoded = json.encodeToString(BlockExecution.serializer(), execution)
        val decoded = json.decodeFromString(BlockExecution.serializer(), encoded)
        assertEquals(execution, decoded)
    }

    @Test
    fun `gate message with emoji round trip`() {
        val gate = Gate(
            message = "\u26A0\uFE0F Approve release \uD83D\uDE80 v2.0?",
            approvalRule = ApprovalRule(requiredCount = 1),
        )
        val encoded = json.encodeToString(Gate.serializer(), gate)
        val decoded = json.decodeFromString(Gate.serializer(), encoded)
        assertEquals(gate, decoded)
        assertEquals("\u26A0\uFE0F Approve release \uD83D\uDE80 v2.0?", decoded.message)
    }

    @Test
    fun `container block with emoji name round trip`() {
        val container: Block = Block.ContainerBlock(
            id = BlockId("c1"),
            name = "\uD83D\uDCE6 Deploy Group \uD83C\uDF1F",
            children = DagGraph(
                blocks = listOf(
                    Block.ActionBlock(
                        id = BlockId("b1"),
                        name = "\uD83D\uDE80 Publish",
                        type = BlockType.GITHUB_PUBLICATION,
                    ),
                ),
            ),
        )
        val encoded = json.encodeToString(Block.serializer(), container)
        val decoded = json.decodeFromString(Block.serializer(), encoded)
        assertEquals(container, decoded)
        assertTrue(decoded is Block.ContainerBlock)
        assertEquals("\uD83D\uDCE6 Deploy Group \uD83C\uDF1F", decoded.name)
    }

    @Test
    fun `team with emoji name round trip`() {
        val response = TeamResponse(
            team = Team(
                id = TeamId("t1"),
                name = "\uD83D\uDC51 Royal Devs",
                description = "\uD83D\uDCA1 Innovation team",
                createdAt = 0,
            ),
            memberCount = 5,
        )
        val encoded = json.encodeToString(TeamResponse.serializer(), response)
        val decoded = json.decodeFromString(TeamResponse.serializer(), encoded)
        assertEquals(response, decoded)
        assertEquals("\uD83D\uDC51 Royal Devs", decoded.team.name)
    }

    @Test
    fun `DAG graph with emoji block names round trip`() {
        val graph = DagGraph(
            blocks = listOf(
                Block.ActionBlock(
                    id = BlockId("b1"),
                    name = "\uD83D\uDEE0\uFE0F Build",
                    type = BlockType.TEAMCITY_BUILD,
                ),
                Block.ActionBlock(
                    id = BlockId("b2"),
                    name = "\uD83D\uDCE2 Notify",
                    type = BlockType.SLACK_MESSAGE,
                ),
                Block.ActionBlock(
                    id = BlockId("b3"),
                    name = "\uD83D\uDE80 Deploy",
                    type = BlockType.GITHUB_PUBLICATION,
                ),
            ),
            edges = listOf(
                Edge(fromBlockId = BlockId("b1"), toBlockId = BlockId("b2")),
                Edge(fromBlockId = BlockId("b1"), toBlockId = BlockId("b3")),
            ),
            positions = mapOf(
                BlockId("b1") to BlockPosition(100f, 100f),
                BlockId("b2") to BlockPosition(300f, 50f),
                BlockId("b3") to BlockPosition(300f, 150f),
            ),
        )
        val encoded = json.encodeToString(DagGraph.serializer(), graph)
        val decoded = json.decodeFromString(DagGraph.serializer(), encoded)
        assertEquals(graph, decoded)
    }

    @Test
    fun `release event with emoji content round trip`() {
        val now = Clock.System.now()
        val event: ReleaseEvent = ReleaseEvent.BlockExecutionUpdated(
            releaseId = ReleaseId("r1"),
            blockExecution = BlockExecution(
                blockId = BlockId("b1"),
                releaseId = ReleaseId("r1"),
                status = BlockStatus.SUCCEEDED,
                outputs = mapOf("\uD83D\uDCE6 artifact" to "\uD83D\uDE80 deployed"),
                startedAt = now,
                finishedAt = now,
            ),
        )
        val encoded = json.encodeToString(ReleaseEvent.serializer(), event)
        val decoded = json.decodeFromString(ReleaseEvent.serializer(), encoded)
        assertEquals(event, decoded)
    }

    @Test
    fun `approve block request with emoji comment round trip`() {
        val request = ApproveBlockRequest(
            input = mapOf(
                "approved" to "true",
                "comment" to "\uD83D\uDC4D LGTM! Ship it \uD83D\uDE80",
            ),
        )
        val encoded = json.encodeToString(ApproveBlockRequest.serializer(), request)
        val decoded = json.decodeFromString(ApproveBlockRequest.serializer(), encoded)
        assertEquals(request, decoded)
        val input = decoded.input
        assertEquals("\uD83D\uDC4D LGTM! Ship it \uD83D\uDE80", input["comment"])
    }

    @Test
    fun `complex unicode emoji sequences round trip`() {
        // Test with combining sequences, skin tones, ZWJ sequences
        val param = Parameter(
            key = "flag",
            value = "\uD83C\uDDFA\uD83C\uDDF8", // US flag (regional indicator sequence)
        )
        val encoded = json.encodeToString(Parameter.serializer(), param)
        val decoded = json.decodeFromString(Parameter.serializer(), encoded)
        assertEquals(param, decoded)
    }
}
