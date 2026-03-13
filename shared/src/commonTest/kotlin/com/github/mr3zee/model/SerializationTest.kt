package com.github.mr3zee.model

import com.github.mr3zee.AppJson
import com.github.mr3zee.api.*
import kotlin.time.Clock
import kotlin.test.Test
import kotlin.test.assertEquals

class SerializationTest {
    private val json = AppJson

    @Test
    fun dagGraphRoundTrip() {
        val graph = DagGraph(
            blocks = listOf(
                Block.ActionBlock(
                    id = BlockId("b1"),
                    name = "Build",
                    type = BlockType.TEAMCITY_BUILD,
                    parameters = listOf(Parameter("buildType", "MyBuild")),
                    outputs = listOf("buildNumber", "buildUrl"),
                    timeoutSeconds = 600,
                ),
                Block.ActionBlock(
                    id = BlockId("b2"),
                    name = "Notify",
                    type = BlockType.SLACK_MESSAGE,
                ),
                Block.ContainerBlock(
                    id = BlockId("c1"),
                    name = "Deploy Group",
                    children = DagGraph(
                        blocks = listOf(
                            Block.ActionBlock(
                                id = BlockId("b3"),
                                name = "Publish Maven",
                                type = BlockType.MAVEN_CENTRAL_PUBLICATION,
                            ),
                        ),
                    ),
                ),
            ),
            edges = listOf(
                Edge(fromBlockId = BlockId("b1"), toBlockId = BlockId("c1")),
                Edge(fromBlockId = BlockId("c1"), toBlockId = BlockId("b2")),
            ),
            positions = mapOf(
                BlockId("b1") to BlockPosition(100f, 50f),
                BlockId("b2") to BlockPosition(400f, 200f),
                BlockId("c1") to BlockPosition(250f, 100f),
            ),
        )
        val encoded = json.encodeToString(DagGraph.serializer(), graph)
        val decoded = json.decodeFromString(DagGraph.serializer(), encoded)
        assertEquals(graph, decoded)
    }

    @Test
    fun projectTemplateRoundTrip() {
        val now = Clock.System.now()
        val template = ProjectTemplate(
            id = ProjectId("p1"),
            name = "My Release",
            description = "A test release",
            dagGraph = DagGraph(),
            parameters = listOf(Parameter("version", "1.0.0", "Release version")),
            createdAt = now,
            updatedAt = now,
        )
        val encoded = json.encodeToString(ProjectTemplate.serializer(), template)
        val decoded = json.decodeFromString(ProjectTemplate.serializer(), encoded)
        assertEquals(template, decoded)
    }

    @Test
    fun blockStatusRoundTrip() {
        for (status in BlockStatus.entries) {
            val encoded = json.encodeToString(BlockStatus.serializer(), status)
            val decoded = json.decodeFromString(BlockStatus.serializer(), encoded)
            assertEquals(status, decoded)
        }
    }

    @Test
    fun connectionConfigRoundTrip() {
        val configs = listOf(
            ConnectionConfig.SlackConfig(webhookUrl = "https://hooks.slack.com/test"),
            ConnectionConfig.TeamCityConfig(serverUrl = "https://tc.example.com", token = "abc123"),
            ConnectionConfig.GitHubConfig(token = "ghp_test", owner = "mr3zee", repo = "release-wizard"),
            ConnectionConfig.MavenCentralConfig(username = "user", password = "pass"),
        )
        for (config in configs) {
            val encoded = json.encodeToString(ConnectionConfig.serializer(), config)
            val decoded = json.decodeFromString(ConnectionConfig.serializer(), encoded)
            assertEquals(config, decoded)
        }
    }

    @Test
    fun createProjectRequestRoundTrip() {
        val request = CreateProjectRequest(
            name = "New Project",
            description = "Description",
            parameters = listOf(Parameter("key", "value")),
        )
        val encoded = json.encodeToString(CreateProjectRequest.serializer(), request)
        val decoded = json.decodeFromString(CreateProjectRequest.serializer(), encoded)
        assertEquals(request, decoded)
    }

    @Test
    fun updateProjectRequestRoundTrip() {
        val request = UpdateProjectRequest(name = "Updated Name")
        val encoded = json.encodeToString(UpdateProjectRequest.serializer(), request)
        val decoded = json.decodeFromString(UpdateProjectRequest.serializer(), encoded)
        assertEquals(request, decoded)
    }

    @Test
    fun projectResponseRoundTrip() {
        val now = Clock.System.now()
        val response = ProjectResponse(
            project = ProjectTemplate(
                id = ProjectId("p1"),
                name = "Test",
                createdAt = now,
                updatedAt = now,
            ),
        )
        val encoded = json.encodeToString(ProjectResponse.serializer(), response)
        val decoded = json.decodeFromString(ProjectResponse.serializer(), encoded)
        assertEquals(response, decoded)
    }

    @Test
    fun projectListResponseRoundTrip() {
        val response = ProjectListResponse(projects = emptyList())
        val encoded = json.encodeToString(ProjectListResponse.serializer(), response)
        val decoded = json.decodeFromString(ProjectListResponse.serializer(), encoded)
        assertEquals(response, decoded)
    }

    @Test
    fun connectionRoundTrip() {
        val now = Clock.System.now()
        val connection = Connection(
            id = ConnectionId("c1"),
            name = "My GitHub",
            type = ConnectionType.GITHUB,
            config = ConnectionConfig.GitHubConfig(token = "ghp_test", owner = "mr3zee", repo = "release-wizard"),
            createdAt = now,
            updatedAt = now,
        )
        val encoded = json.encodeToString(Connection.serializer(), connection)
        val decoded = json.decodeFromString(Connection.serializer(), encoded)
        assertEquals(connection, decoded)
    }

    @Test
    fun connectionDtosRoundTrip() {
        val createRequest = CreateConnectionRequest(
            name = "My Slack",
            type = ConnectionType.SLACK,
            config = ConnectionConfig.SlackConfig(webhookUrl = "https://hooks.slack.com/test"),
        )
        val encoded1 = json.encodeToString(CreateConnectionRequest.serializer(), createRequest)
        val decoded1 = json.decodeFromString(CreateConnectionRequest.serializer(), encoded1)
        assertEquals(createRequest, decoded1)

        val updateRequest = UpdateConnectionRequest(name = "Updated")
        val encoded2 = json.encodeToString(UpdateConnectionRequest.serializer(), updateRequest)
        val decoded2 = json.decodeFromString(UpdateConnectionRequest.serializer(), encoded2)
        assertEquals(updateRequest, decoded2)
    }

    @Test
    fun authDtosRoundTrip() {
        val loginRequest = LoginRequest(username = "admin", password = "pass")
        val encoded1 = json.encodeToString(LoginRequest.serializer(), loginRequest)
        val decoded1 = json.decodeFromString(LoginRequest.serializer(), encoded1)
        assertEquals(loginRequest, decoded1)

        val userInfo = UserInfo(username = "admin")
        val encoded2 = json.encodeToString(UserInfo.serializer(), userInfo)
        val decoded2 = json.decodeFromString(UserInfo.serializer(), encoded2)
        assertEquals(userInfo, decoded2)
    }

    @Test
    fun releaseRoundTrip() {
        val now = Clock.System.now()
        val release = Release(
            id = ReleaseId("r1"),
            projectTemplateId = ProjectId("p1"),
            status = ReleaseStatus.RUNNING,
            dagSnapshot = DagGraph(),
            startedAt = now,
        )
        val encoded = json.encodeToString(Release.serializer(), release)
        val decoded = json.decodeFromString(Release.serializer(), encoded)
        assertEquals(release, decoded)
    }

    @Test
    fun blockExecutionRoundTrip() {
        val now = Clock.System.now()
        val execution = BlockExecution(
            blockId = BlockId("b1"),
            releaseId = ReleaseId("r1"),
            status = BlockStatus.SUCCEEDED,
            outputs = mapOf("buildNumber" to "42", "buildUrl" to "https://example.com"),
            startedAt = now,
            finishedAt = now,
        )
        val encoded = json.encodeToString(BlockExecution.serializer(), execution)
        val decoded = json.decodeFromString(BlockExecution.serializer(), encoded)
        assertEquals(execution, decoded)
    }

    @Test
    fun blockExecutionWithErrorRoundTrip() {
        val execution = BlockExecution(
            blockId = BlockId("b1"),
            releaseId = ReleaseId("r1"),
            status = BlockStatus.FAILED,
            error = "Connection timeout",
        )
        val encoded = json.encodeToString(BlockExecution.serializer(), execution)
        val decoded = json.decodeFromString(BlockExecution.serializer(), encoded)
        assertEquals(execution, decoded)
    }

    @Test
    fun releaseDtosRoundTrip() {
        val createRequest = CreateReleaseRequest(
            projectTemplateId = ProjectId("p1"),
            parameters = listOf(Parameter("version", "1.0.0")),
        )
        val encoded1 = json.encodeToString(CreateReleaseRequest.serializer(), createRequest)
        val decoded1 = json.decodeFromString(CreateReleaseRequest.serializer(), encoded1)
        assertEquals(createRequest, decoded1)

        val approveRequest = ApproveBlockRequest(
            input = mapOf("approved" to "true", "comment" to "LGTM"),
        )
        val encoded2 = json.encodeToString(ApproveBlockRequest.serializer(), approveRequest)
        val decoded2 = json.decodeFromString(ApproveBlockRequest.serializer(), encoded2)
        assertEquals(approveRequest, decoded2)
    }

    @Test
    fun releaseResponseRoundTrip() {
        val now = Clock.System.now()
        val response = ReleaseResponse(
            release = Release(
                id = ReleaseId("r1"),
                projectTemplateId = ProjectId("p1"),
                status = ReleaseStatus.SUCCEEDED,
                dagSnapshot = DagGraph(),
                startedAt = now,
                finishedAt = now,
            ),
            blockExecutions = listOf(
                BlockExecution(
                    blockId = BlockId("b1"),
                    releaseId = ReleaseId("r1"),
                    status = BlockStatus.SUCCEEDED,
                    outputs = mapOf("key" to "value"),
                    startedAt = now,
                    finishedAt = now,
                ),
            ),
        )
        val encoded = json.encodeToString(ReleaseResponse.serializer(), response)
        val decoded = json.decodeFromString(ReleaseResponse.serializer(), encoded)
        assertEquals(response, decoded)
    }
}
