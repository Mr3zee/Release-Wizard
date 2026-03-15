package com.github.mr3zee.model

import com.github.mr3zee.AppJson
import com.github.mr3zee.api.*
import kotlin.time.Clock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SerializationTest {
    private val json = AppJson

    @Test
    fun `DAG graph round trip`() {
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
    fun `project template round trip`() {
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
    fun `block status round trip`() {
        for (status in BlockStatus.entries) {
            val encoded = json.encodeToString(BlockStatus.serializer(), status)
            val decoded = json.decodeFromString(BlockStatus.serializer(), encoded)
            assertEquals(status, decoded)
        }
    }

    @Test
    fun `connection config round trip`() {
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
    fun `create project request round trip`() {
        val request = CreateProjectRequest(
            name = "New Project",
            teamId = TeamId("test-team"),
            description = "Description",
            parameters = listOf(Parameter("key", "value")),
        )
        val encoded = json.encodeToString(CreateProjectRequest.serializer(), request)
        val decoded = json.decodeFromString(CreateProjectRequest.serializer(), encoded)
        assertEquals(request, decoded)
    }

    @Test
    fun `update project request round trip`() {
        val request = UpdateProjectRequest(name = "Updated Name")
        val encoded = json.encodeToString(UpdateProjectRequest.serializer(), request)
        val decoded = json.decodeFromString(UpdateProjectRequest.serializer(), encoded)
        assertEquals(request, decoded)
    }

    @Test
    fun `project response round trip`() {
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
    fun `project list response round trip`() {
        val response = ProjectListResponse(projects = emptyList())
        val encoded = json.encodeToString(ProjectListResponse.serializer(), response)
        val decoded = json.decodeFromString(ProjectListResponse.serializer(), encoded)
        assertEquals(response, decoded)
    }

    @Test
    fun `connection round trip`() {
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
    fun `connection DTOs round trip`() {
        val createRequest = CreateConnectionRequest(
            name = "My Slack",
            teamId = TeamId("test-team"),
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
    fun `auth DTOs round trip`() {
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
    fun `release round trip`() {
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
    fun `block execution round trip`() {
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
    fun `block execution with error round trip`() {
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
    fun `release DTOs round trip`() {
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
    fun `release event snapshot round trip`() {
        val now = Clock.System.now()
        val event: ReleaseEvent = ReleaseEvent.Snapshot(
            releaseId = ReleaseId("r1"),
            release = Release(
                id = ReleaseId("r1"),
                projectTemplateId = ProjectId("p1"),
                status = ReleaseStatus.RUNNING,
                dagSnapshot = DagGraph(),
                startedAt = now,
            ),
            blockExecutions = listOf(
                BlockExecution(
                    blockId = BlockId("b1"),
                    releaseId = ReleaseId("r1"),
                    status = BlockStatus.RUNNING,
                    startedAt = now,
                ),
            ),
        )
        val encoded = json.encodeToString(ReleaseEvent.serializer(), event)
        val decoded = json.decodeFromString(ReleaseEvent.serializer(), encoded)
        assertEquals(event, decoded)
        // Verify discriminator is present
        assertTrue(encoded.contains("\"type\":\"snapshot\""))
    }

    @Test
    fun `release event status changed round trip`() {
        val now = Clock.System.now()
        val event: ReleaseEvent = ReleaseEvent.ReleaseStatusChanged(
            releaseId = ReleaseId("r1"),
            status = ReleaseStatus.RUNNING,
            startedAt = now,
        )
        val encoded = json.encodeToString(ReleaseEvent.serializer(), event)
        val decoded = json.decodeFromString(ReleaseEvent.serializer(), encoded)
        assertEquals(event, decoded)
        assertTrue(encoded.contains("\"type\":\"release_status_changed\""))
    }

    @Test
    fun `release event block execution updated round trip`() {
        val now = Clock.System.now()
        val event: ReleaseEvent = ReleaseEvent.BlockExecutionUpdated(
            releaseId = ReleaseId("r1"),
            blockExecution = BlockExecution(
                blockId = BlockId("b1"),
                releaseId = ReleaseId("r1"),
                status = BlockStatus.SUCCEEDED,
                outputs = mapOf("buildNumber" to "42"),
                startedAt = now,
                finishedAt = now,
            ),
        )
        val encoded = json.encodeToString(ReleaseEvent.serializer(), event)
        val decoded = json.decodeFromString(ReleaseEvent.serializer(), encoded)
        assertEquals(event, decoded)
        assertTrue(encoded.contains("\"type\":\"block_execution_updated\""))
    }

    @Test
    fun `release event completed round trip`() {
        val now = Clock.System.now()
        val event: ReleaseEvent = ReleaseEvent.ReleaseCompleted(
            releaseId = ReleaseId("r1"),
            status = ReleaseStatus.SUCCEEDED,
            finishedAt = now,
        )
        val encoded = json.encodeToString(ReleaseEvent.serializer(), event)
        val decoded = json.decodeFromString(ReleaseEvent.serializer(), encoded)
        assertEquals(event, decoded)
        assertTrue(encoded.contains("\"type\":\"release_completed\""))
    }

    @Test
    fun `CreateTeamRequest round trip`() {
        val request = CreateTeamRequest(name = "Dev Team", description = "Development team")
        val encoded = json.encodeToString(CreateTeamRequest.serializer(), request)
        val decoded = json.decodeFromString(CreateTeamRequest.serializer(), encoded)
        assertEquals(request, decoded)
    }

    @Test
    fun `TeamResponse round trip`() {
        val response = TeamResponse(
            team = Team(id = TeamId("t1"), name = "Alpha", description = "First team", createdAt = 0),
            memberCount = 3,
        )
        val encoded = json.encodeToString(TeamResponse.serializer(), response)
        val decoded = json.decodeFromString(TeamResponse.serializer(), encoded)
        assertEquals(response, decoded)
    }

    @Test
    fun `InviteListResponse round trip`() {
        val response = InviteListResponse(
            invites = listOf(
                TeamInvite(
                    id = "inv-1",
                    teamId = TeamId("t1"),
                    teamName = "Alpha",
                    invitedUserId = UserId("u2"),
                    invitedUsername = "bob",
                    invitedByUserId = UserId("u1"),
                    invitedByUsername = "alice",
                    status = InviteStatus.PENDING,
                    createdAt = 0,
                ),
            ),
        )
        val encoded = json.encodeToString(InviteListResponse.serializer(), response)
        val decoded = json.decodeFromString(InviteListResponse.serializer(), encoded)
        assertEquals(response, decoded)
    }

    @Test
    fun `JoinRequestListResponse round trip`() {
        val response = JoinRequestListResponse(
            requests = listOf(
                JoinRequest(
                    id = "jr-1",
                    teamId = TeamId("t1"),
                    teamName = "Alpha",
                    userId = UserId("u3"),
                    username = "charlie",
                    status = JoinRequestStatus.PENDING,
                    createdAt = 0,
                ),
            ),
        )
        val encoded = json.encodeToString(JoinRequestListResponse.serializer(), response)
        val decoded = json.decodeFromString(JoinRequestListResponse.serializer(), encoded)
        assertEquals(response, decoded)
    }

    @Test
    fun `TeamMemberListResponse round trip`() {
        val response = TeamMemberListResponse(
            members = listOf(
                TeamMembership(
                    teamId = TeamId("t1"),
                    userId = UserId("u1"),
                    username = "alice",
                    role = TeamRole.TEAM_LEAD,
                    joinedAt = 0,
                ),
            ),
        )
        val encoded = json.encodeToString(TeamMemberListResponse.serializer(), response)
        val decoded = json.decodeFromString(TeamMemberListResponse.serializer(), encoded)
        assertEquals(response, decoded)
    }

    @Test
    fun `UpdateMemberRoleRequest round trip`() {
        val request = UpdateMemberRoleRequest(role = TeamRole.COLLABORATOR)
        val encoded = json.encodeToString(UpdateMemberRoleRequest.serializer(), request)
        val decoded = json.decodeFromString(UpdateMemberRoleRequest.serializer(), encoded)
        assertEquals(request, decoded)
    }

    @Test
    fun `release response round trip`() {
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

    @Test
    fun `gate round trip`() {
        val gate = Gate(
            message = $$"Approve release ${param.version}",
            approvalRule = ApprovalRule(
                requiredRole = UserRole.ADMIN,
                requiredUserIds = listOf("user1", "user2"),
                requiredCount = 2,
            ),
        )
        val encoded = json.encodeToString(Gate.serializer(), gate)
        val decoded = json.decodeFromString(Gate.serializer(), encoded)
        assertEquals(gate, decoded)
    }

    @Test
    fun `gate with defaults round trip`() {
        val gate = Gate()
        val encoded = json.encodeToString(Gate.serializer(), gate)
        val decoded = json.decodeFromString(Gate.serializer(), encoded)
        assertEquals(gate, decoded)
        assertEquals("", decoded.message)
        assertEquals(1, decoded.approvalRule.requiredCount)
    }

    @Test
    fun `gate phase round trip`() {
        for (phase in GatePhase.entries) {
            val encoded = json.encodeToString(GatePhase.serializer(), phase)
            val decoded = json.decodeFromString(GatePhase.serializer(), encoded)
            assertEquals(phase, decoded)
        }
    }

    @Test
    fun `action block with gates round trip`() {
        val block = Block.ActionBlock(
            id = BlockId("gated"),
            name = "Gated Build",
            type = BlockType.TEAMCITY_BUILD,
            preGate = Gate(message = "Ready to build?"),
            postGate = Gate(message = "Build OK?", approvalRule = ApprovalRule(requiredCount = 2)),
        )
        val encoded = json.encodeToString(Block.serializer(), block)
        val decoded = json.decodeFromString(Block.serializer(), encoded)
        assertEquals(block, decoded)
        assertTrue(decoded is Block.ActionBlock)
        assertEquals("Ready to build?", decoded.preGate?.message)
        assertEquals(2, decoded.postGate?.approvalRule?.requiredCount)
    }

    @Test
    fun `block execution with gate fields round trip`() {
        val exec = BlockExecution(
            blockId = BlockId("b1"),
            releaseId = ReleaseId("r1"),
            status = BlockStatus.WAITING_FOR_INPUT,
            gatePhase = GatePhase.PRE,
            gateMessage = "Approve to start 'Build'",
            approvals = listOf(
                BlockApproval(userId = "admin", username = "admin", approvedAt = 1234567890L),
            ),
        )
        val encoded = json.encodeToString(BlockExecution.serializer(), exec)
        val decoded = json.decodeFromString(BlockExecution.serializer(), encoded)
        assertEquals(exec, decoded)
        assertEquals(GatePhase.PRE, decoded.gatePhase)
        assertEquals("Approve to start 'Build'", decoded.gateMessage)
    }
}
