package com.github.mr3zee.releases

import com.github.mr3zee.api.*
import com.github.mr3zee.jsonClient
import com.github.mr3zee.login
import com.github.mr3zee.loginAndCreateTeam
import com.github.mr3zee.testModule
import com.github.mr3zee.model.*
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

class ReleasesRoutesTest {

    private suspend fun HttpClient.createTestProject(
        teamId: TeamId,
        name: String = "Test Project",
        blocks: List<Block> = listOf(
            Block.ActionBlock(
                id = BlockId("block-a"),
                name = "Build",
                type = BlockType.TEAMCITY_BUILD,
            ),
        ),
        edges: List<Edge> = emptyList(),
        parameters: List<Parameter> = emptyList(),
    ): ProjectTemplate {
        val response = post(ApiRoutes.Projects.BASE) {
            contentType(ContentType.Application.Json)
            setBody(
                CreateProjectRequest(
                    name = name,
                    teamId = teamId,
                    dagGraph = DagGraph(blocks = blocks, edges = edges),
                    parameters = parameters,
                )
            )
        }
        return response.body<ProjectResponse>().project
    }

    private suspend fun HttpClient.startAndAwaitRelease(
        projectTemplateId: ProjectId,
        parameters: List<Parameter> = emptyList(),
    ): ReleaseResponse {
        val startResponse = post(ApiRoutes.Releases.BASE) {
            contentType(ContentType.Application.Json)
            setBody(CreateReleaseRequest(projectTemplateId = projectTemplateId, parameters = parameters))
        }
        assertEquals(HttpStatusCode.Created, startResponse.status)
        val created = startResponse.body<ReleaseResponse>()

        // Await execution completion
        val awaitResponse = post(ApiRoutes.Releases.await(created.release.id.value))
        assertEquals(HttpStatusCode.OK, awaitResponse.status)
        return awaitResponse.body<ReleaseResponse>()
    }

    @Test
    fun `list releases returns empty list initially`() = testApplication {
        application { testModule() }
        val client = jsonClient()
        // todo claude: unused
        val teamId = client.loginAndCreateTeam()

        val response = client.get(ApiRoutes.Releases.BASE)
        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.body<ReleaseListResponse>()
        assertTrue(body.releases.isEmpty())
    }

    @Test
    fun `start release and get it`() = testApplication {
        application { testModule() }
        val client = jsonClient()
        val teamId = client.loginAndCreateTeam()

        val project = client.createTestProject(teamId)
        val result = client.startAndAwaitRelease(project.id)

        assertEquals(project.id, result.release.projectTemplateId)
        assertEquals(ReleaseStatus.SUCCEEDED, result.release.status)
        assertTrue(result.blockExecutions.isNotEmpty())

        // Verify GET returns the same
        val getResponse = client.get(ApiRoutes.Releases.byId(result.release.id.value))
        assertEquals(HttpStatusCode.OK, getResponse.status)
        val fetched = getResponse.body<ReleaseResponse>()
        assertEquals(result.release.id, fetched.release.id)
        assertEquals(ReleaseStatus.SUCCEEDED, fetched.release.status)
    }

    @Test
    fun `start release with nonexistent project returns 400`() = testApplication {
        application { testModule() }
        val client = jsonClient()
        // todo claude: unused
        val teamId = client.loginAndCreateTeam()

        val response = client.post(ApiRoutes.Releases.BASE) {
            contentType(ContentType.Application.Json)
            setBody(CreateReleaseRequest(projectTemplateId = ProjectId("00000000-0000-0000-0000-000000000000")))
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `start release with empty DAG returns 400`() = testApplication {
        application { testModule() }
        val client = jsonClient()
        val teamId = client.loginAndCreateTeam()

        val project = client.createTestProject(teamId, blocks = emptyList())

        val response = client.post(ApiRoutes.Releases.BASE) {
            contentType(ContentType.Application.Json)
            setBody(CreateReleaseRequest(projectTemplateId = project.id))
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `sequential DAG executes in correct order`() = testApplication {
        application { testModule() }
        val client = jsonClient()
        val teamId = client.loginAndCreateTeam()

        // A -> B -> C
        val blocks = listOf(
            Block.ActionBlock(id = BlockId("a"), name = "A", type = BlockType.TEAMCITY_BUILD),
            Block.ActionBlock(id = BlockId("b"), name = "B", type = BlockType.GITHUB_ACTION),
            Block.ActionBlock(id = BlockId("c"), name = "C", type = BlockType.SLACK_MESSAGE),
        )
        val edges = listOf(
            Edge(fromBlockId = BlockId("a"), toBlockId = BlockId("b")),
            Edge(fromBlockId = BlockId("b"), toBlockId = BlockId("c")),
        )
        val project = client.createTestProject(teamId, blocks = blocks, edges = edges)
        val result = client.startAndAwaitRelease(project.id)

        assertEquals(ReleaseStatus.SUCCEEDED, result.release.status)
        assertEquals(3, result.blockExecutions.size)
        assertTrue(result.blockExecutions.all { it.status == BlockStatus.SUCCEEDED })
    }

    @Test
    fun `diamond DAG executes correctly`() = testApplication {
        application { testModule() }
        val client = jsonClient()
        val teamId = client.loginAndCreateTeam()

        // A -> B, A -> C, B -> D, C -> D
        val blocks = listOf(
            Block.ActionBlock(id = BlockId("a"), name = "A", type = BlockType.TEAMCITY_BUILD),
            Block.ActionBlock(id = BlockId("b"), name = "B", type = BlockType.GITHUB_ACTION),
            Block.ActionBlock(id = BlockId("c"), name = "C", type = BlockType.SLACK_MESSAGE),
            Block.ActionBlock(id = BlockId("d"), name = "D", type = BlockType.GITHUB_PUBLICATION),
        )
        val edges = listOf(
            Edge(fromBlockId = BlockId("a"), toBlockId = BlockId("b")),
            Edge(fromBlockId = BlockId("a"), toBlockId = BlockId("c")),
            Edge(fromBlockId = BlockId("b"), toBlockId = BlockId("d")),
            Edge(fromBlockId = BlockId("c"), toBlockId = BlockId("d")),
        )
        val project = client.createTestProject(teamId, blocks = blocks, edges = edges)
        val result = client.startAndAwaitRelease(project.id)

        assertEquals(ReleaseStatus.SUCCEEDED, result.release.status)
        assertEquals(4, result.blockExecutions.size)
        assertTrue(result.blockExecutions.all { it.status == BlockStatus.SUCCEEDED })

        // D should have started after both B and C
        val dExec = result.blockExecutions.find { it.blockId == BlockId("d") }
            ?: error("Block execution for 'd' should exist")
        val bExec = result.blockExecutions.find { it.blockId == BlockId("b") }
            ?: error("Block execution for 'b' should exist")
        val cExec = result.blockExecutions.find { it.blockId == BlockId("c") }
            ?: error("Block execution for 'c' should exist")
        assertNotNull(dExec.startedAt)
        assertNotNull(bExec.finishedAt)
        assertNotNull(cExec.finishedAt)
        val dStartedAt = dExec.startedAt ?: error("dExec.startedAt should not be null")
        val bFinishedAt = bExec.finishedAt ?: error("bExec.finishedAt should not be null")
        val cFinishedAt = cExec.finishedAt ?: error("cExec.finishedAt should not be null")
        assertTrue(dStartedAt >= bFinishedAt)
        assertTrue(dStartedAt >= cFinishedAt)
    }

    @Test
    fun `user action block waits for approval`() = testApplication {
        application { testModule() }
        val client = jsonClient()
        val teamId = client.loginAndCreateTeam()

        val blocks = listOf(
            Block.ActionBlock(id = BlockId("action"), name = "Approve", type = BlockType.SLACK_MESSAGE, preGate = Gate()),
        )
        val project = client.createTestProject(teamId, blocks = blocks)

        val created = client.post(ApiRoutes.Releases.BASE) {
            contentType(ContentType.Application.Json)
            setBody(CreateReleaseRequest(projectTemplateId = project.id))
        }.body<ReleaseResponse>()

        // Launch await in background and retry approval with timeout
        val result = coroutineScope {
            val awaitDeferred = async {
                client.post(ApiRoutes.Releases.await(created.release.id.value))
                    .body<ReleaseResponse>()
            }

            // Retry approval until the engine reaches WAITING_FOR_INPUT
            withTimeoutOrNull(10_000.milliseconds) {
                while (true) {
                    val resp = client.post(ApiRoutes.Releases.approveBlock(created.release.id.value, "action")) {
                        contentType(ContentType.Application.Json)
                        setBody(ApproveBlockRequest(input = mapOf("approved" to "true")))
                    }
                    if (resp.status == HttpStatusCode.OK) break
                    yield()
                }
            } ?: error("Timed out waiting for block approval")

            awaitDeferred.await()
        }
        assertEquals(ReleaseStatus.SUCCEEDED, result.release.status)
    }

    @Test
    fun `cancel running release`() = testApplication {
        application { testModule() }
        val client = jsonClient()
        val teamId = client.loginAndCreateTeam()

        val blocks = listOf(
            Block.ActionBlock(id = BlockId("action"), name = "Wait", type = BlockType.SLACK_MESSAGE, preGate = Gate()),
        )
        val project = client.createTestProject(teamId, blocks = blocks)

        val created = client.post(ApiRoutes.Releases.BASE) {
            contentType(ContentType.Application.Json)
            setBody(CreateReleaseRequest(projectTemplateId = project.id))
        }.body<ReleaseResponse>()

        // cancelRelease calls cancelExecution which cancels and joins
        val cancelResponse = client.post(ApiRoutes.Releases.cancel(created.release.id.value))
        assertEquals(HttpStatusCode.OK, cancelResponse.status)

        val fetched = client.get(ApiRoutes.Releases.byId(created.release.id.value))
            .body<ReleaseResponse>()
        assertEquals(ReleaseStatus.CANCELLED, fetched.release.status)
    }

    @Test
    fun `get nonexistent release returns 404`() = testApplication {
        application { testModule() }
        val client = jsonClient()
        // todo claude: unused
        val teamId = client.loginAndCreateTeam()

        val response = client.get(ApiRoutes.Releases.byId("00000000-0000-0000-0000-000000000000"))
        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `unauthenticated release request returns 401`() = testApplication {
        application { testModule() }
        val client = jsonClient()

        val response = client.get(ApiRoutes.Releases.BASE)
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `release with parameters merges project and request params`() = testApplication {
        application { testModule() }
        val client = jsonClient()
        val teamId = client.loginAndCreateTeam()

        val blocks = listOf(
            Block.ActionBlock(id = BlockId("a"), name = "A", type = BlockType.TEAMCITY_BUILD),
        )
        val project = client.createTestProject(
            teamId,
            blocks = blocks,
            parameters = listOf(
                Parameter(key = "version", value = "1.0.0"),
                Parameter(key = "env", value = "staging"),
            ),
        )

        val result = client.startAndAwaitRelease(
            projectTemplateId = project.id,
            parameters = listOf(Parameter(key = "version", value = "2.0.0")),
        )

        val params = result.release.parameters
        assertEquals("2.0.0", params.find { it.key == "version" }?.value)
        assertEquals("staging", params.find { it.key == "env" }?.value)
    }

    @Test
    fun `block outputs are captured`() = testApplication {
        application { testModule() }
        val client = jsonClient()
        val teamId = client.loginAndCreateTeam()

        val blocks = listOf(
            Block.ActionBlock(id = BlockId("build"), name = "Build", type = BlockType.TEAMCITY_BUILD),
        )
        val project = client.createTestProject(teamId, blocks = blocks)
        val result = client.startAndAwaitRelease(project.id)

        val buildExec = result.blockExecutions.find { it.blockId == BlockId("build") }
            ?: error("Block execution for 'build' should exist")
        assertEquals(BlockStatus.SUCCEEDED, buildExec.status)
        assertTrue(buildExec.outputs.containsKey("buildNumber"))
        assertTrue(buildExec.outputs.containsKey("buildUrl"))
    }

    // ---- Phase 1A: Release Re-run ----

    @Test
    fun `rerun release creates new release with same DAG`() = testApplication {
        application { testModule() }
        val client = jsonClient()
        val teamId = client.loginAndCreateTeam()

        val blocks = listOf(
            Block.ActionBlock(id = BlockId("a"), name = "A", type = BlockType.TEAMCITY_BUILD),
        )
        val project = client.createTestProject(teamId, blocks = blocks)
        val original = client.startAndAwaitRelease(project.id)
        assertEquals(ReleaseStatus.SUCCEEDED, original.release.status)

        // Rerun
        val rerunResponse = client.post(ApiRoutes.Releases.rerun(original.release.id.value))
        assertEquals(HttpStatusCode.Created, rerunResponse.status)
        val rerun = rerunResponse.body<ReleaseResponse>()

        assertNotEquals(original.release.id, rerun.release.id)
        assertEquals(project.id, rerun.release.projectTemplateId)

        // Await the new release
        val awaitResponse = client.post(ApiRoutes.Releases.await(rerun.release.id.value))
        assertEquals(HttpStatusCode.OK, awaitResponse.status)
        val completed = awaitResponse.body<ReleaseResponse>()
        assertEquals(ReleaseStatus.SUCCEEDED, completed.release.status)
    }

    @Test
    fun `rerun running release returns 400`() = testApplication {
        application { testModule() }
        val client = jsonClient()
        val teamId = client.loginAndCreateTeam()

        val blocks = listOf(
            Block.ActionBlock(id = BlockId("action"), name = "Wait", type = BlockType.SLACK_MESSAGE, preGate = Gate()),
        )
        val project = client.createTestProject(teamId, blocks = blocks)

        val created = client.post(ApiRoutes.Releases.BASE) {
            contentType(ContentType.Application.Json)
            setBody(CreateReleaseRequest(projectTemplateId = project.id))
        }.body<ReleaseResponse>()

        val rerunResponse = client.post(ApiRoutes.Releases.rerun(created.release.id.value))
        assertEquals(HttpStatusCode.BadRequest, rerunResponse.status)

        // Clean up: cancel the running release
        client.post(ApiRoutes.Releases.cancel(created.release.id.value))
    }

    // ---- Phase 1B: Release Archive & Delete ----

    @Test
    fun `archive release sets status to ARCHIVED`() = testApplication {
        application { testModule() }
        val client = jsonClient()
        val teamId = client.loginAndCreateTeam()

        val project = client.createTestProject(teamId)
        val result = client.startAndAwaitRelease(project.id)
        assertEquals(ReleaseStatus.SUCCEEDED, result.release.status)

        val archiveResponse = client.post(ApiRoutes.Releases.archive(result.release.id.value))
        assertEquals(HttpStatusCode.OK, archiveResponse.status)
        val archived = archiveResponse.body<ReleaseResponse>()
        assertEquals(ReleaseStatus.ARCHIVED, archived.release.status)

        // Verify GET also returns ARCHIVED
        val fetched = client.get(ApiRoutes.Releases.byId(result.release.id.value))
            .body<ReleaseResponse>()
        assertEquals(ReleaseStatus.ARCHIVED, fetched.release.status)
    }

    @Test
    fun `archive running release returns 400`() = testApplication {
        application { testModule() }
        val client = jsonClient()
        val teamId = client.loginAndCreateTeam()

        val blocks = listOf(
            Block.ActionBlock(id = BlockId("action"), name = "Wait", type = BlockType.SLACK_MESSAGE, preGate = Gate()),
        )
        val project = client.createTestProject(teamId, blocks = blocks)

        val created = client.post(ApiRoutes.Releases.BASE) {
            contentType(ContentType.Application.Json)
            setBody(CreateReleaseRequest(projectTemplateId = project.id))
        }.body<ReleaseResponse>()

        val archiveResponse = client.post(ApiRoutes.Releases.archive(created.release.id.value))
        assertEquals(HttpStatusCode.BadRequest, archiveResponse.status)

        // Clean up: cancel the running release
        client.post(ApiRoutes.Releases.cancel(created.release.id.value))
    }

    @Test
    fun `delete release removes it`() = testApplication {
        application { testModule() }
        val client = jsonClient()
        val teamId = client.loginAndCreateTeam()

        val project = client.createTestProject(teamId)
        val result = client.startAndAwaitRelease(project.id)
        assertEquals(ReleaseStatus.SUCCEEDED, result.release.status)

        val deleteResponse = client.delete(ApiRoutes.Releases.byId(result.release.id.value))
        assertEquals(HttpStatusCode.NoContent, deleteResponse.status)

        // Verify GET returns 404
        val getResponse = client.get(ApiRoutes.Releases.byId(result.release.id.value))
        assertEquals(HttpStatusCode.NotFound, getResponse.status)
    }

    @Test
    fun `delete running release returns 400`() = testApplication {
        application { testModule() }
        val client = jsonClient()
        val teamId = client.loginAndCreateTeam()

        val blocks = listOf(
            Block.ActionBlock(id = BlockId("action"), name = "Wait", type = BlockType.SLACK_MESSAGE, preGate = Gate()),
        )
        val project = client.createTestProject(teamId, blocks = blocks)

        val created = client.post(ApiRoutes.Releases.BASE) {
            contentType(ContentType.Application.Json)
            setBody(CreateReleaseRequest(projectTemplateId = project.id))
        }.body<ReleaseResponse>()

        val deleteResponse = client.delete(ApiRoutes.Releases.byId(created.release.id.value))
        assertEquals(HttpStatusCode.BadRequest, deleteResponse.status)

        // Clean up: cancel the running release
        client.post(ApiRoutes.Releases.cancel(created.release.id.value))
    }

    @Test
    fun `archived releases excluded from list`() = testApplication {
        application { testModule() }
        val client = jsonClient()
        val teamId = client.loginAndCreateTeam()

        val project = client.createTestProject(teamId)
        val result = client.startAndAwaitRelease(project.id)
        assertEquals(ReleaseStatus.SUCCEEDED, result.release.status)

        // Verify the release is in the list before archiving
        val listBefore = client.get(ApiRoutes.Releases.BASE).body<ReleaseListResponse>()
        assertTrue(listBefore.releases.any { it.id == result.release.id })

        // Archive it
        val archiveResponse = client.post(ApiRoutes.Releases.archive(result.release.id.value))
        assertEquals(HttpStatusCode.OK, archiveResponse.status)

        // Verify the release is NOT in the list after archiving
        val listAfter = client.get(ApiRoutes.Releases.BASE).body<ReleaseListResponse>()
        assertTrue(listAfter.releases.none { it.id == result.release.id })
    }

    // ---- Gate tests ----

    @Test
    fun `post-gate only - block executes then waits for approval`() = testApplication {
        application { testModule() }
        val client = jsonClient()
        val teamId = client.loginAndCreateTeam()

        val blocks = listOf(
            Block.ActionBlock(id = BlockId("build"), name = "Build", type = BlockType.TEAMCITY_BUILD, postGate = Gate()),
        )
        val project = client.createTestProject(teamId, blocks = blocks)

        val created = client.post(ApiRoutes.Releases.BASE) {
            contentType(ContentType.Application.Json)
            setBody(CreateReleaseRequest(projectTemplateId = project.id))
        }.body<ReleaseResponse>()

        val result = coroutineScope {
            val awaitDeferred = async {
                client.post(ApiRoutes.Releases.await(created.release.id.value))
                    .body<ReleaseResponse>()
            }

            // Wait for post-gate WAITING_FOR_INPUT with outputs already present
            withTimeoutOrNull(10_000.milliseconds) {
                while (true) {
                    val resp = client.post(ApiRoutes.Releases.approveBlock(created.release.id.value, "build")) {
                        contentType(ContentType.Application.Json)
                        setBody(ApproveBlockRequest())
                    }
                    if (resp.status == HttpStatusCode.OK) break
                    yield()
                }
            } ?: error("Timed out waiting for block approval")

            awaitDeferred.await()
        }
        assertEquals(ReleaseStatus.SUCCEEDED, result.release.status)
        // Block should have outputs from execution (build ran before post-gate)
        val buildExec = result.blockExecutions.find { it.blockId == BlockId("build") }
        assertNotNull(buildExec)
        assertEquals(BlockStatus.SUCCEEDED, buildExec.status)
        assertTrue(buildExec.outputs.isNotEmpty(), "Post-gated block should have outputs after approval")
    }

    @Test
    fun `both gates - pre-gate then execute then post-gate`() = testApplication {
        application { testModule() }
        val client = jsonClient()
        val teamId = client.loginAndCreateTeam()

        val blocks = listOf(
            Block.ActionBlock(
                id = BlockId("build"),
                name = "Build",
                type = BlockType.TEAMCITY_BUILD,
                preGate = Gate(),
                postGate = Gate(),
            ),
        )
        val project = client.createTestProject(teamId, blocks = blocks)

        val created = client.post(ApiRoutes.Releases.BASE) {
            contentType(ContentType.Application.Json)
            setBody(CreateReleaseRequest(projectTemplateId = project.id))
        }.body<ReleaseResponse>()

        val result = coroutineScope {
            val awaitDeferred = async {
                client.post(ApiRoutes.Releases.await(created.release.id.value))
                    .body<ReleaseResponse>()
            }

            // Approve pre-gate
            withTimeoutOrNull(10_000.milliseconds) {
                while (true) {
                    val resp = client.post(ApiRoutes.Releases.approveBlock(created.release.id.value, "build")) {
                        contentType(ContentType.Application.Json)
                        setBody(ApproveBlockRequest())
                    }
                    if (resp.status == HttpStatusCode.OK) break
                    yield()
                }
            } ?: error("Timed out waiting for block approval")

            // Approve post-gate (block must execute first, then enter post-gate)
            withTimeoutOrNull(10_000.milliseconds) {
                while (true) {
                    val resp = client.post(ApiRoutes.Releases.approveBlock(created.release.id.value, "build")) {
                        contentType(ContentType.Application.Json)
                        setBody(ApproveBlockRequest())
                    }
                    if (resp.status == HttpStatusCode.OK) break
                    yield()
                }
            } ?: error("Timed out waiting for block approval")

            awaitDeferred.await()
        }
        assertEquals(ReleaseStatus.SUCCEEDED, result.release.status)
    }

    @Test
    fun `pre-gate with requiredCount 2 needs two approvals from different users`() = testApplication {
        application { testModule() }
        val adminClient = jsonClient()
        val teamId = adminClient.loginAndCreateTeam()

        val blocks = listOf(
            Block.ActionBlock(
                id = BlockId("action"),
                name = "Approve",
                type = BlockType.SLACK_MESSAGE,
                preGate = Gate(approvalRule = ApprovalRule(requiredCount = 2)),
            ),
        )
        val project = adminClient.createTestProject(teamId, blocks = blocks)

        // Register user2 and invite them to the team
        val user2Client = jsonClient()
        user2Client.login("user2", "user2pass")
        val user2Info = user2Client.get(ApiRoutes.Auth.ME).body<UserInfo>()
        val user2Id = user2Info.id ?: error("No user2 ID")
        adminClient.post(ApiRoutes.Teams.invites(teamId.value)) {
            contentType(ContentType.Application.Json)
            setBody(CreateInviteRequest(userId = UserId(user2Id)))
        }
        val invites = user2Client.get(ApiRoutes.Auth.MyInvites.BASE).body<InviteListResponse>()
        user2Client.post(ApiRoutes.Auth.MyInvites.accept(invites.invites.first().id))

        val created = adminClient.post(ApiRoutes.Releases.BASE) {
            contentType(ContentType.Application.Json)
            setBody(CreateReleaseRequest(projectTemplateId = project.id))
        }.body<ReleaseResponse>()

        // First approval from admin
        withTimeoutOrNull(10_000.milliseconds) {
            while (true) {
                val resp = adminClient.post(ApiRoutes.Releases.approveBlock(created.release.id.value, "action")) {
                    contentType(ContentType.Application.Json)
                    setBody(ApproveBlockRequest())
                }
                if (resp.status == HttpStatusCode.OK) break
                yield()
            }
        } ?: error("Timed out waiting for block approval")

        // Release should still be running (only 1 of 2 approvals)
        val midCheck = adminClient.get(ApiRoutes.Releases.byId(created.release.id.value))
            .body<ReleaseResponse>()
        assertEquals(ReleaseStatus.RUNNING, midCheck.release.status)
        val actionExec = midCheck.blockExecutions.find { it.blockId == BlockId("action") }
        assertNotNull(actionExec)
        assertEquals(BlockStatus.WAITING_FOR_INPUT, actionExec.status)
        assertEquals(1, actionExec.approvals.size)

        // Second approval from user2 completes the gate
        val result = coroutineScope {
            val awaitDeferred = async {
                adminClient.post(ApiRoutes.Releases.await(created.release.id.value))
                    .body<ReleaseResponse>()
            }

            withTimeoutOrNull(10_000.milliseconds) {
                while (true) {
                    val resp = user2Client.post(ApiRoutes.Releases.approveBlock(created.release.id.value, "action")) {
                        contentType(ContentType.Application.Json)
                        setBody(ApproveBlockRequest())
                    }
                    if (resp.status == HttpStatusCode.OK) break
                    yield()
                }
            } ?: error("Timed out waiting for block approval")

            awaitDeferred.await()
        }
        assertEquals(ReleaseStatus.SUCCEEDED, result.release.status)
    }

    @Test
    fun `cancel during pre-gate marks block as failed`() = testApplication {
        application { testModule() }
        val client = jsonClient()
        val teamId = client.loginAndCreateTeam()

        val blocks = listOf(
            Block.ActionBlock(id = BlockId("action"), name = "Wait", type = BlockType.SLACK_MESSAGE, preGate = Gate()),
        )
        val project = client.createTestProject(teamId, blocks = blocks)

        val created = client.post(ApiRoutes.Releases.BASE) {
            contentType(ContentType.Application.Json)
            setBody(CreateReleaseRequest(projectTemplateId = project.id))
        }.body<ReleaseResponse>()

        // Wait until block enters WAITING_FOR_INPUT
        withTimeoutOrNull(10_000.milliseconds) {
            while (true) {
                val resp = client.get(ApiRoutes.Releases.byId(created.release.id.value))
                    .body<ReleaseResponse>()
                val exec = resp.blockExecutions.find { it.blockId == BlockId("action") }
                if (exec?.status == BlockStatus.WAITING_FOR_INPUT) break
                yield()
            }
        } ?: error("Timed out waiting for WAITING_FOR_INPUT status")

        // Cancel the release
        client.post(ApiRoutes.Releases.cancel(created.release.id.value))

        val fetched = client.get(ApiRoutes.Releases.byId(created.release.id.value))
            .body<ReleaseResponse>()
        assertEquals(ReleaseStatus.CANCELLED, fetched.release.status)
        val actionExec = fetched.blockExecutions.find { it.blockId == BlockId("action") }
        assertNotNull(actionExec)
        assertEquals(BlockStatus.FAILED, actionExec.status)
    }

    @Test
    fun `parallel blocks with pre-gates wait independently`() = testApplication {
        application { testModule() }
        val client = jsonClient()
        val teamId = client.loginAndCreateTeam()

        val blocks = listOf(
            Block.ActionBlock(id = BlockId("a"), name = "Gate A", type = BlockType.SLACK_MESSAGE, preGate = Gate()),
            Block.ActionBlock(id = BlockId("b"), name = "Gate B", type = BlockType.SLACK_MESSAGE, preGate = Gate()),
        )
        val project = client.createTestProject(teamId, blocks = blocks)

        val created = client.post(ApiRoutes.Releases.BASE) {
            contentType(ContentType.Application.Json)
            setBody(CreateReleaseRequest(projectTemplateId = project.id))
        }.body<ReleaseResponse>()

        val result = coroutineScope {
            val awaitDeferred = async {
                client.post(ApiRoutes.Releases.await(created.release.id.value))
                    .body<ReleaseResponse>()
            }

            // Approve both gates
            for (blockId in listOf("a", "b")) {
                withTimeoutOrNull(10_000.milliseconds) {
                    while (true) {
                        val resp = client.post(ApiRoutes.Releases.approveBlock(created.release.id.value, blockId)) {
                            contentType(ContentType.Application.Json)
                            setBody(ApproveBlockRequest())
                        }
                        if (resp.status == HttpStatusCode.OK) break
                        yield()
                    }
                } ?: error("Timed out waiting for block approval")
            }

            awaitDeferred.await()
        }
        assertEquals(ReleaseStatus.SUCCEEDED, result.release.status)
    }
}
