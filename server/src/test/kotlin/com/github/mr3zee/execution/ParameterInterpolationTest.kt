package com.github.mr3zee.execution

import com.github.mr3zee.model.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.fail
import kotlin.time.Duration.Companion.milliseconds

class ParameterInterpolationTest {

    private fun createTestSetup(): TestSetup {
        val releasesRepo = InMemoryReleasesRepository()
        val executor = CapturingBlockExecutor()
        val connectionsRepo = FakeConnectionsRepository()
        val scope = CoroutineScope(SupervisorJob())
        val engine = ExecutionEngine(releasesRepo, executor, connectionsRepo, scope)
        return TestSetup(releasesRepo, executor, engine, scope)
    }

    private suspend fun <T> withTestSetup(block: suspend TestSetup.() -> T): T {
        val setup = createTestSetup()
        try {
            return setup.block()
        } finally {
            setup.scope.cancel()
            setup.scope.coroutineContext[Job]?.join()
        }
    }

    private fun createRelease(
        dagGraph: DagGraph,
        parameters: List<Parameter> = emptyList(),
        id: String = "release-1",
    ): Release = Release(
        id = ReleaseId(id),
        projectTemplateId = ProjectId("proj-1"),
        status = ReleaseStatus.PENDING,
        dagSnapshot = dagGraph,
        parameters = parameters,
    )

    // ── Test 1: param expressions ──────────────────────────────────────

    @Test
    fun `param expressions resolved before executor receives them`() = runBlocking {
        withTestSetup {
            val release = createRelease(
                dagGraph = DagGraph(
                    blocks = listOf(
                        Block.ActionBlock(
                            id = BlockId("build"),
                            name = "Build",
                            type = BlockType.TEAMCITY_BUILD,
                            parameters = listOf(Parameter(key = "text", value = $$"${param.version}")),
                        ),
                    ),
                ),
                parameters = listOf(Parameter(key = "version", value = "1.0.0")),
            )
            releasesRepo.insertRelease(release)
            engine.startExecution(release)
            engine.awaitExecution(release.id)

            val captured = executor.capturedParameters(BlockId("build"))
            assertNotNull(captured, "Executor should have been called for block 'build'")
            val textParam = captured.find { it.key == "text" }
            assertNotNull(textParam, "Parameter 'text' should be present")
            assertEquals("1.0.0", textParam.value)
        }
    }

    // ── Test 2: block output from predecessor ──────────────────────────

    @Test
    fun `block output expressions resolved from predecessor`() = runBlocking {
        withTestSetup {
            val release = createRelease(
                dagGraph = DagGraph(
                    blocks = listOf(
                        Block.ActionBlock(id = BlockId("a"), name = "A", type = BlockType.TEAMCITY_BUILD),
                        Block.ActionBlock(
                            id = BlockId("b"),
                            name = "B",
                            type = BlockType.SLACK_MESSAGE,
                            parameters = listOf(Parameter(key = "message", value = $$"${block.a.buildNumber}")),
                        ),
                    ),
                    edges = listOf(Edge(fromBlockId = BlockId("a"), toBlockId = BlockId("b"))),
                ),
            )
            releasesRepo.insertRelease(release)
            engine.startExecution(release)
            engine.awaitExecution(release.id)

            val captured = executor.capturedParameters(BlockId("b"))
            assertNotNull(captured, "Executor should have been called for block 'b'")
            val messageParam = captured.find { it.key == "message" }
            assertNotNull(messageParam, "Parameter 'message' should be present")
            assertEquals("42", messageParam.value)
        }
    }

    // ── Test 3: non-immediate predecessor in chain ─────────────────────

    @Test
    fun `non-immediate predecessor outputs available in chain`() = runBlocking {
        withTestSetup {
            val release = createRelease(
                dagGraph = DagGraph(
                    blocks = listOf(
                        Block.ActionBlock(id = BlockId("a"), name = "A", type = BlockType.TEAMCITY_BUILD),
                        Block.ActionBlock(id = BlockId("b"), name = "B", type = BlockType.GITHUB_ACTION),
                        Block.ActionBlock(
                            id = BlockId("c"),
                            name = "C",
                            type = BlockType.SLACK_MESSAGE,
                            parameters = listOf(Parameter(key = "buildRef", value = $$"${block.a.buildNumber}")),
                        ),
                    ),
                    edges = listOf(
                        Edge(fromBlockId = BlockId("a"), toBlockId = BlockId("b")),
                        Edge(fromBlockId = BlockId("b"), toBlockId = BlockId("c")),
                    ),
                ),
            )
            releasesRepo.insertRelease(release)
            engine.startExecution(release)
            engine.awaitExecution(release.id)

            val captured = executor.capturedParameters(BlockId("c"))
            assertNotNull(captured, "Executor should have been called for block 'c'")
            val buildRefParam = captured.find { it.key == "buildRef" }
            assertNotNull(buildRefParam, "Parameter 'buildRef' should be present")
            assertEquals("42", buildRefParam.value)
        }
    }

    // ── Test 4a: pre-gate message interpolation ────────────────────────

    @Test
    fun `pre-gate message with param interpolation`() = runBlocking {
        withTestSetup {
            val blockId = BlockId("gated")

            val release = createRelease(
                dagGraph = DagGraph(
                    blocks = listOf(
                        Block.ActionBlock(
                            id = blockId,
                            name = "Deploy",
                            type = BlockType.SLACK_MESSAGE,
                            preGate = Gate(message = $$"Release ${param.version} ready?"),
                        ),
                    ),
                ),
                parameters = listOf(Parameter(key = "version", value = "2.0.0")),
            )
            releasesRepo.insertRelease(release)
            engine.startExecution(release)

            val gateExec = awaitGateWaiting(releasesRepo, release.id, blockId)
            assertEquals(GatePhase.PRE, gateExec.gatePhase)
            assertEquals("Release 2.0.0 ready?", gateExec.gateMessage)

            approveGate(engine, release.id, blockId)
            engine.awaitExecution(release.id)

            val finalRelease = releasesRepo.findById(release.id)
                ?: fail("Release should exist after execution")
            assertEquals(ReleaseStatus.SUCCEEDED, finalRelease.status)
        }
    }

    // ── Test 4b: post-gate message with block output ───────────────────

    @Test
    fun `post-gate message with block output interpolation`() = runBlocking {
        withTestSetup {
            val notifyId = BlockId("notify")

            val release = createRelease(
                dagGraph = DagGraph(
                    blocks = listOf(
                        Block.ActionBlock(id = BlockId("build"), name = "Build", type = BlockType.TEAMCITY_BUILD),
                        Block.ActionBlock(
                            id = notifyId,
                            name = "Notify",
                            type = BlockType.SLACK_MESSAGE,
                            postGate = Gate(message = $$"Build #${block.build.buildNumber} done"),
                        ),
                    ),
                    edges = listOf(Edge(fromBlockId = BlockId("build"), toBlockId = notifyId)),
                ),
            )
            releasesRepo.insertRelease(release)
            engine.startExecution(release)

            val gateExec = awaitGateWaiting(releasesRepo, release.id, notifyId)
            assertEquals(GatePhase.POST, gateExec.gatePhase)
            assertEquals("Build #42 done", gateExec.gateMessage)

            approveGate(engine, release.id, notifyId)
            engine.awaitExecution(release.id)

            val finalRelease = releasesRepo.findById(release.id)
                ?: fail("Release should exist after execution")
            assertEquals(ReleaseStatus.SUCCEEDED, finalRelease.status)
        }
    }

    // ── Test 5: unresolved expressions pass through ────────────────────

    @Test
    fun `unresolved expressions pass through during execution`() = runBlocking {
        withTestSetup {
            val release = createRelease(
                dagGraph = DagGraph(
                    blocks = listOf(
                        Block.ActionBlock(
                            id = BlockId("build"),
                            name = "Build",
                            type = BlockType.TEAMCITY_BUILD,
                            parameters = listOf(Parameter(key = "ref", value = $$"${param.nonexistent}")),
                        ),
                    ),
                ),
            )
            releasesRepo.insertRelease(release)
            engine.startExecution(release)
            engine.awaitExecution(release.id)

            val captured = executor.capturedParameters(BlockId("build"))
            assertNotNull(captured, "Executor should have been called for block 'build'")
            val refParam = captured.find { it.key == "ref" }
            assertNotNull(refParam, "Parameter 'ref' should be present")
            assertEquals($$"${param.nonexistent}", refParam.value)
        }
    }

    // ── Test 6: mixed param and block output in single value ───────────

    @Test
    fun `mixed param and block output in single value`() = runBlocking {
        withTestSetup {
            val release = createRelease(
                dagGraph = DagGraph(
                    blocks = listOf(
                        Block.ActionBlock(id = BlockId("a"), name = "A", type = BlockType.TEAMCITY_BUILD),
                        Block.ActionBlock(
                            id = BlockId("b"),
                            name = "B",
                            type = BlockType.SLACK_MESSAGE,
                            parameters = listOf(
                                Parameter(key = "text", value = $$"v${param.version} build #${block.a.buildNumber}"),
                            ),
                        ),
                    ),
                    edges = listOf(Edge(fromBlockId = BlockId("a"), toBlockId = BlockId("b"))),
                ),
                parameters = listOf(Parameter(key = "version", value = "1.0.0")),
            )
            releasesRepo.insertRelease(release)
            engine.startExecution(release)
            engine.awaitExecution(release.id)

            val captured = executor.capturedParameters(BlockId("b"))
            assertNotNull(captured, "Executor should have been called for block 'b'")
            val textParam = captured.find { it.key == "text" }
            assertNotNull(textParam, "Parameter 'text' should be present")
            assertEquals("v1.0.0 build #42", textParam.value)
        }
    }

    // ── Test 7: diamond DAG parallel predecessors ──────────────────────

    @Test
    fun `diamond DAG parallel predecessors outputs available to downstream`() = runBlocking {
        withTestSetup {
            // Diamond: A -> B, A -> C, B -> D, C -> D
            val release = createRelease(
                dagGraph = DagGraph(
                    blocks = listOf(
                        Block.ActionBlock(id = BlockId("a"), name = "A", type = BlockType.TEAMCITY_BUILD),
                        Block.ActionBlock(id = BlockId("b"), name = "B", type = BlockType.GITHUB_ACTION),
                        Block.ActionBlock(id = BlockId("c"), name = "C", type = BlockType.SLACK_MESSAGE),
                        Block.ActionBlock(
                            id = BlockId("d"),
                            name = "D",
                            type = BlockType.GITHUB_PUBLICATION,
                            parameters = listOf(
                                Parameter(key = "runRef", value = $$"${block.b.runId}"),
                                Parameter(key = "slackTs", value = $$"${block.c.messageTs}"),
                            ),
                        ),
                    ),
                    edges = listOf(
                        Edge(fromBlockId = BlockId("a"), toBlockId = BlockId("b")),
                        Edge(fromBlockId = BlockId("a"), toBlockId = BlockId("c")),
                        Edge(fromBlockId = BlockId("b"), toBlockId = BlockId("d")),
                        Edge(fromBlockId = BlockId("c"), toBlockId = BlockId("d")),
                    ),
                ),
            )
            releasesRepo.insertRelease(release)
            engine.startExecution(release)
            engine.awaitExecution(release.id)

            val captured = executor.capturedParameters(BlockId("d"))
            assertNotNull(captured, "Executor should have been called for block 'd'")

            // B is GITHUB_ACTION → outputs runId = "123456"
            val runRefParam = captured.find { it.key == "runRef" }
            assertNotNull(runRefParam, "Parameter 'runRef' should be present")
            assertEquals("123456", runRefParam.value)

            // C is SLACK_MESSAGE → outputs messageTs = "1234567890.123456"
            val slackTsParam = captured.find { it.key == "slackTs" }
            assertNotNull(slackTsParam, "Parameter 'slackTs' should be present")
            assertEquals("1234567890.123456", slackTsParam.value)
        }
    }

    // ── Test helpers ───────────────────────────────────────────────────

    private suspend fun awaitGateWaiting(
        repo: InMemoryReleasesRepository,
        releaseId: ReleaseId,
        blockId: BlockId,
    ): BlockExecution {
        var result: BlockExecution? = null
        withTimeoutOrNull(5_000.milliseconds) {
            while (true) {
                val exec = repo.findBlockExecution(releaseId, blockId)
                if (exec != null && exec.status == BlockStatus.WAITING_FOR_INPUT) {
                    result = exec
                    break
                }
                yield()
            }
        } ?: fail("Block ${blockId.value} did not enter WAITING_FOR_INPUT within timeout")
        return result ?: fail("Block ${blockId.value} execution should exist")
    }

    private suspend fun approveGate(
        engine: ExecutionEngine,
        releaseId: ReleaseId,
        blockId: BlockId,
    ) {
        val approved = withTimeoutOrNull(5_000.milliseconds) {
            while (!engine.approveBlock(releaseId, blockId, emptyMap())) {
                yield()
            }
            true
        }
        assertEquals(true, approved, "Should be able to approve block ${blockId.value}")
    }

    data class TestSetup(
        val releasesRepo: InMemoryReleasesRepository,
        val executor: CapturingBlockExecutor,
        val engine: ExecutionEngine,
        val scope: CoroutineScope,
    )
}
