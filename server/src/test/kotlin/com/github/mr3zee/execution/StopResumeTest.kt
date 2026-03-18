package com.github.mr3zee.execution

import com.github.mr3zee.WebhookConfig
import com.github.mr3zee.model.*
import com.github.mr3zee.webhooks.StatusWebhookService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.yield
import java.util.concurrent.ConcurrentHashMap
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class StopResumeTest {

    private suspend fun withTestSetup(executor: BlockExecutor = StubBlockExecutor(), block: suspend EngineTestSetup.() -> Unit) {
        val releasesRepo = InMemoryReleasesRepository()
        val connectionsRepo = FakeConnectionsRepository()
        val scope = CoroutineScope(SupervisorJob())
        val engine = ExecutionEngine(releasesRepo, executor, connectionsRepo, scope)
        val statusWebhookService = StatusWebhookService(
            InMemoryStatusWebhookTokenRepository(),
            releasesRepo,
            lazyOf(engine),
            WebhookConfig(baseUrl = "http://localhost:8080"),
        )
        val recoveryService = RecoveryService(releasesRepo, engine, statusWebhookService)
        val setup = EngineTestSetup(releasesRepo, engine, recoveryService, scope)
        try {
            setup.block()
        } finally {
            scope.cancel()
            scope.coroutineContext[Job]?.join()
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    /** Polls until the block execution reaches the expected status or times out. */
    private suspend fun awaitBlockStatus(
        repo: InMemoryReleasesRepository,
        releaseId: ReleaseId,
        blockId: BlockId,
        expected: BlockStatus,
        timeoutMs: Long = 5_000,
    ) {
        val reached = withTimeoutOrNull(timeoutMs.milliseconds) {
            while (repo.findBlockExecution(releaseId, blockId)?.status != expected) {
                yield()
            }
            true
        }
        assertEquals(true, reached, "Block ${blockId.value} did not reach $expected within ${timeoutMs}ms")
    }

    /** Polls until the release reaches the expected status or times out. */
    private suspend fun awaitReleaseStatus(
        repo: InMemoryReleasesRepository,
        releaseId: ReleaseId,
        expected: ReleaseStatus,
        timeoutMs: Long = 5_000,
    ) {
        val reached = withTimeoutOrNull(timeoutMs.milliseconds) {
            while (repo.findById(releaseId)?.status != expected) {
                yield()
            }
            true
        }
        assertEquals(true, reached, "Release ${releaseId.value} did not reach $expected within ${timeoutMs}ms")
    }

    private fun singleBlockRelease(
        id: String = "release-stop-1",
        blockId: String = "block-a",
        status: ReleaseStatus = ReleaseStatus.RUNNING,
    ) = Release(
        id = ReleaseId(id),
        projectTemplateId = ProjectId("proj-1"),
        status = status,
        dagSnapshot = DagGraph(
            blocks = listOf(Block.ActionBlock(id = BlockId(blockId), name = "A", type = BlockType.SLACK_MESSAGE)),
        ),
        startedAt = Clock.System.now(),
    )

    private fun twoBlockRelease(
        id: String = "release-stop-2",
        status: ReleaseStatus = ReleaseStatus.RUNNING,
    ) = Release(
        id = ReleaseId(id),
        projectTemplateId = ProjectId("proj-1"),
        status = status,
        dagSnapshot = DagGraph(
            blocks = listOf(
                Block.ActionBlock(id = BlockId("a"), name = "A", type = BlockType.SLACK_MESSAGE),
                Block.ActionBlock(id = BlockId("b"), name = "B", type = BlockType.SLACK_MESSAGE),
            ),
            edges = listOf(Edge(fromBlockId = BlockId("a"), toBlockId = BlockId("b"))),
        ),
        startedAt = Clock.System.now(),
    )

    // ── Stop tests ───────────────────────────────────────────────────────

    @Test
    fun `stop running release marks active blocks as STOPPED and release as STOPPED`() = runBlocking {
        val executor = ControllableBlockExecutor()
        withTestSetup(executor) {
            val release = singleBlockRelease(id = "r1", blockId = "a")
            releasesRepo.insertRelease(release)
            releasesRepo.batchUpsertBlockExecutions(release.id, release.dagSnapshot.blocks)

            engine.startExecution(release)

            // Wait until block is running
            awaitBlockStatus(releasesRepo, release.id, BlockId("a"), BlockStatus.RUNNING)

            val stopped = engine.stopRelease(release.id)
            assertTrue(stopped)

            val finalRelease = releasesRepo.findById(release.id) ?: fail("Release must exist")
            assertEquals(ReleaseStatus.STOPPED, finalRelease.status)

            val exec = releasesRepo.findBlockExecution(release.id, BlockId("a")) ?: fail("Block exec must exist")
            assertEquals(BlockStatus.STOPPED, exec.status)
            assertNotNull(exec.finishedAt, "Stopped block must have finishedAt")
        }
    }

    @Test
    fun `stop preserves SUCCEEDED blocks and only stops RUNNING ones`() = runBlocking {
        withTestSetup {
            val release = twoBlockRelease(id = "r2")
            releasesRepo.insertRelease(release)
            // A already succeeded, B is running
            releasesRepo.upsertBlockExecution(
                BlockExecution(
                    blockId = BlockId("a"), releaseId = release.id,
                    status = BlockStatus.SUCCEEDED,
                    outputs = mapOf("key" to "value"),
                    startedAt = Clock.System.now(), finishedAt = Clock.System.now(),
                )
            )
            releasesRepo.upsertBlockExecution(
                BlockExecution(
                    blockId = BlockId("b"), releaseId = release.id,
                    status = BlockStatus.RUNNING,
                    startedAt = Clock.System.now(),
                )
            )

            val stopped = engine.stopRelease(release.id)
            assertTrue(stopped)

            val aExec = releasesRepo.findBlockExecution(release.id, BlockId("a")) ?: fail("Block A must exist")
            assertEquals(BlockStatus.SUCCEEDED, aExec.status, "SUCCEEDED block must not be changed")
            assertEquals(mapOf("key" to "value"), aExec.outputs, "SUCCEEDED block outputs must be preserved")

            val bExec = releasesRepo.findBlockExecution(release.id, BlockId("b")) ?: fail("Block B must exist")
            assertEquals(BlockStatus.STOPPED, bExec.status)
        }
    }

    @Test
    fun `stop with no running blocks still marks release as STOPPED`() = runBlocking {
        withTestSetup {
            val release = singleBlockRelease(id = "r3", blockId = "a")
            releasesRepo.insertRelease(release)
            // Block is WAITING (not yet started)
            releasesRepo.upsertBlockExecution(
                BlockExecution(blockId = BlockId("a"), releaseId = release.id, status = BlockStatus.WAITING)
            )

            val stopped = engine.stopRelease(release.id)
            assertTrue(stopped)

            val finalRelease = releasesRepo.findById(release.id) ?: fail("Release must exist")
            assertEquals(ReleaseStatus.STOPPED, finalRelease.status)

            // WAITING block unchanged — it never ran
            val exec = releasesRepo.findBlockExecution(release.id, BlockId("a")) ?: fail("Block exec must exist")
            assertEquals(BlockStatus.WAITING, exec.status)
        }
    }

    @Test
    fun `stop marks WAITING_FOR_INPUT blocks as STOPPED`() = runBlocking {
        withTestSetup {
            val release = singleBlockRelease(id = "r4", blockId = "gate")
            releasesRepo.insertRelease(release)
            releasesRepo.upsertBlockExecution(
                BlockExecution(
                    blockId = BlockId("gate"), releaseId = release.id,
                    status = BlockStatus.WAITING_FOR_INPUT,
                    startedAt = Clock.System.now(),
                    gatePhase = GatePhase.PRE,
                )
            )

            val stopped = engine.stopRelease(release.id)
            assertTrue(stopped)

            val exec = releasesRepo.findBlockExecution(release.id, BlockId("gate")) ?: fail("Block exec must exist")
            assertEquals(BlockStatus.STOPPED, exec.status)
        }
    }

    @Test
    fun `stop cancels external build via executor cancel()`() = runBlocking {
        val executor = ControllableBlockExecutor()
        withTestSetup(executor) {
            val release = singleBlockRelease(id = "r5", blockId = "build")
            releasesRepo.insertRelease(release)
            releasesRepo.batchUpsertBlockExecutions(release.id, release.dagSnapshot.blocks)

            engine.startExecution(release)

            // Wait until block is RUNNING — cancel() is only called for RUNNING blocks
            awaitBlockStatus(releasesRepo, release.id, BlockId("build"), BlockStatus.RUNNING)

            engine.stopRelease(release.id)

            assertTrue(executor.wasCancelled(BlockId("build")), "cancel() must be called for RUNNING block")
        }
    }

    @Test
    fun `stop is idempotent - second stop keeps release in STOPPED without corrupting state`() = runBlocking {
        val executor = ControllableBlockExecutor()
        withTestSetup(executor) {
            val release = singleBlockRelease(id = "r6", blockId = "a")
            releasesRepo.insertRelease(release)
            releasesRepo.batchUpsertBlockExecutions(release.id, release.dagSnapshot.blocks)

            engine.startExecution(release)
            awaitBlockStatus(releasesRepo, release.id, BlockId("a"), BlockStatus.RUNNING)

            val first = engine.stopRelease(release.id)
            assertTrue(first, "First stop must return true")
            assertEquals(ReleaseStatus.STOPPED, releasesRepo.findById(release.id)?.status)

            // Second stop on an already-stopped release — no active job, returns true (idempotent)
            val second = engine.stopRelease(release.id)
            assertTrue(second, "Second stop returns true (idempotent)")

            // State must remain consistent — still STOPPED, not flipped to something else
            val finalRelease = releasesRepo.findById(release.id) ?: fail("Release must exist")
            assertEquals(ReleaseStatus.STOPPED, finalRelease.status, "Release must remain STOPPED after double-stop")

            val exec = releasesRepo.findBlockExecution(release.id, BlockId("a")) ?: fail("Block exec must exist")
            assertEquals(BlockStatus.STOPPED, exec.status, "Block must remain STOPPED after double-stop")
        }
    }

    // ── Resume tests ──────────────────────────────────────────────────────

    @Test
    fun `resume stopped release re-executes stopped blocks and completes`() = runBlocking {
        withTestSetup {
            val release = twoBlockRelease(id = "r10").copy(status = ReleaseStatus.STOPPED)
            releasesRepo.insertRelease(release)
            // A succeeded before stop, B was stopped
            releasesRepo.upsertBlockExecution(
                BlockExecution(
                    blockId = BlockId("a"), releaseId = release.id,
                    status = BlockStatus.SUCCEEDED,
                    outputs = mapOf("key" to "val"),
                    startedAt = Clock.System.now(), finishedAt = Clock.System.now(),
                )
            )
            releasesRepo.upsertBlockExecution(
                BlockExecution(
                    blockId = BlockId("b"), releaseId = release.id,
                    status = BlockStatus.STOPPED,
                    startedAt = Clock.System.now(), finishedAt = Clock.System.now(),
                )
            )

            val resumed = engine.resumeRelease(release.id)
            assertTrue(resumed, "resumeRelease must return true for STOPPED release")

            engine.awaitExecution(release.id)

            val finalRelease = releasesRepo.findById(release.id) ?: fail("Release must exist")
            assertEquals(ReleaseStatus.SUCCEEDED, finalRelease.status, "Release must succeed after resume")

            val aExec = releasesRepo.findBlockExecution(release.id, BlockId("a")) ?: fail("Block A must exist")
            assertEquals(BlockStatus.SUCCEEDED, aExec.status, "Block A (already succeeded) must remain SUCCEEDED")

            val bExec = releasesRepo.findBlockExecution(release.id, BlockId("b")) ?: fail("Block B must exist")
            assertEquals(BlockStatus.SUCCEEDED, bExec.status, "Block B (was stopped) must be SUCCEEDED after resume")
        }
    }

    @Test
    fun `resume resets stopped block outputs for fresh re-execution`() = runBlocking {
        val executor = ControllableBlockExecutor()
        withTestSetup(executor) {
            val release = singleBlockRelease(id = "r11", blockId = "a").copy(status = ReleaseStatus.STOPPED)
            releasesRepo.insertRelease(release)
            releasesRepo.upsertBlockExecution(
                BlockExecution(
                    blockId = BlockId("a"), releaseId = release.id,
                    status = BlockStatus.STOPPED,
                    // Simulate internal build ID that was persisted before the stop
                    outputs = mapOf("_tcBuildId" to "build-42"),
                    startedAt = Clock.System.now(), finishedAt = Clock.System.now(),
                )
            )

            engine.resumeRelease(release.id)

            // Wait for the block to be re-triggered (proves it was reset and re-executed, not skipped)
            awaitBlockStatus(releasesRepo, release.id, BlockId("a"), BlockStatus.RUNNING)

            // Let the block complete with fresh (empty) outputs
            executor.complete(BlockId("a"))

            engine.awaitExecution(release.id)

            val finalRelease = releasesRepo.findById(release.id) ?: fail("Release must exist after completion")
            assertEquals(ReleaseStatus.SUCCEEDED, finalRelease.status, "Release must succeed after resume")

            val exec = releasesRepo.findBlockExecution(release.id, BlockId("a")) ?: fail("Block exec must exist")
            assertEquals(BlockStatus.SUCCEEDED, exec.status, "Block must be SUCCEEDED after re-execution")
            // The stale _tcBuildId from before stop must NOT carry over — proves outputs were cleared by batchResumeBlocks
            assertNull(exec.outputs["_tcBuildId"], "Stale internal build ID must be absent after fresh re-execution")
        }
    }

    @Test
    fun `resume on non-stopped release returns false`() = runBlocking {
        withTestSetup {
            val runningRelease = singleBlockRelease(id = "r12", blockId = "a")
            releasesRepo.insertRelease(runningRelease)

            val result = engine.resumeRelease(runningRelease.id)
            assertFalse(result, "resumeRelease must return false for non-STOPPED release")
        }
    }

    @Test
    fun `full stop-resume cycle completes successfully`() = runBlocking {
        val executor = ControllableBlockExecutor()
        withTestSetup(executor) {
            val release = twoBlockRelease(id = "r13")
            releasesRepo.insertRelease(release)
            releasesRepo.batchUpsertBlockExecutions(release.id, release.dagSnapshot.blocks)

            engine.startExecution(release)

            // Wait for A to start running, then let it complete
            awaitBlockStatus(releasesRepo, release.id, BlockId("a"), BlockStatus.RUNNING)
            executor.complete(BlockId("a"))

            // Wait for B to start
            awaitBlockStatus(releasesRepo, release.id, BlockId("b"), BlockStatus.RUNNING)

            // Stop while B is running
            engine.stopRelease(release.id)
            awaitReleaseStatus(releasesRepo, release.id, ReleaseStatus.STOPPED)

            val afterStop = releasesRepo.findById(release.id) ?: fail("Release must exist after stop")
            assertEquals(ReleaseStatus.STOPPED, afterStop.status)

            val bAfterStop = releasesRepo.findBlockExecution(release.id, BlockId("b")) ?: fail("Block B must exist")
            assertEquals(BlockStatus.STOPPED, bAfterStop.status)

            // Resume — B should re-execute from scratch
            engine.resumeRelease(release.id)

            // Complete B after resume
            awaitBlockStatus(releasesRepo, release.id, BlockId("b"), BlockStatus.RUNNING)
            executor.complete(BlockId("b"))

            engine.awaitExecution(release.id)

            val finalRelease = releasesRepo.findById(release.id) ?: fail("Release must exist after resume")
            assertEquals(ReleaseStatus.SUCCEEDED, finalRelease.status)

            // A must still be SUCCEEDED with its original outputs preserved — resume must NOT clear SUCCEEDED blocks
            val aFinal = releasesRepo.findBlockExecution(release.id, BlockId("a")) ?: fail("Block A must exist")
            assertEquals(BlockStatus.SUCCEEDED, aFinal.status)
            assertEquals(emptyMap<String, String>(), aFinal.outputs, "Block A outputs must be preserved across stop/resume")

            val bFinal = releasesRepo.findBlockExecution(release.id, BlockId("b")) ?: fail("Block B must exist")
            assertEquals(BlockStatus.SUCCEEDED, bFinal.status)
        }
    }

    // ── Cancel-after-stop tests ──────────────────────────────────────────

    @Test
    fun `cancel on stopped release marks stopped blocks as FAILED`() = runBlocking {
        withTestSetup {
            val release = singleBlockRelease(id = "r20", blockId = "a").copy(status = ReleaseStatus.STOPPED)
            releasesRepo.insertRelease(release)
            releasesRepo.upsertBlockExecution(
                BlockExecution(
                    blockId = BlockId("a"), releaseId = release.id,
                    status = BlockStatus.STOPPED,
                    startedAt = Clock.System.now(), finishedAt = Clock.System.now(),
                )
            )

            engine.cancelExecution(release.id)

            val finalRelease = releasesRepo.findById(release.id) ?: fail("Release must exist")
            assertEquals(ReleaseStatus.CANCELLED, finalRelease.status)

            val exec = releasesRepo.findBlockExecution(release.id, BlockId("a")) ?: fail("Block exec must exist")
            assertEquals(BlockStatus.FAILED, exec.status, "Stopped block must become FAILED when release is cancelled")
            assertEquals("Release cancelled", exec.error)
            // finishedAt must be preserved from the stop — not overwritten by cancel
            assertNotNull(exec.finishedAt, "finishedAt must be set on cancelled block")
        }
    }

    // ── Recovery-does-not-touch-STOPPED tests ───────────────────────────

    @Test
    fun `recovery does not auto-resume STOPPED releases`() = runBlocking {
        withTestSetup {
            val release = singleBlockRelease(id = "r30", blockId = "a").copy(
                status = ReleaseStatus.STOPPED,
            )
            releasesRepo.insertRelease(release)
            releasesRepo.upsertBlockExecution(
                BlockExecution(
                    blockId = BlockId("a"), releaseId = release.id,
                    status = BlockStatus.STOPPED,
                    startedAt = Clock.System.now(), finishedAt = Clock.System.now(),
                )
            )

            recoveryService.recover()

            // Give recovery a moment to process (it's async)
            withTimeoutOrNull(500.milliseconds) {
                while (releasesRepo.findById(release.id)?.status == ReleaseStatus.STOPPED) {
                    yield()
                }
            }

            val finalRelease = releasesRepo.findById(release.id) ?: fail("Release must exist")
            assertEquals(ReleaseStatus.STOPPED, finalRelease.status, "STOPPED release must not be recovered automatically")
        }
    }

    // ── stopBlock delegates to full release stop ─────────────────────────

    @Test
    fun `stopBlock stops entire release, not just the target block`() = runBlocking {
        val executor = ControllableBlockExecutor()
        withTestSetup(executor) {
            // Two parallel blocks (no edges — both start simultaneously)
            val release = Release(
                id = ReleaseId("r40"),
                projectTemplateId = ProjectId("proj-1"),
                status = ReleaseStatus.RUNNING,
                dagSnapshot = DagGraph(
                    blocks = listOf(
                        Block.ActionBlock(id = BlockId("a"), name = "A", type = BlockType.SLACK_MESSAGE),
                        Block.ActionBlock(id = BlockId("b"), name = "B", type = BlockType.SLACK_MESSAGE),
                    ),
                ),
                startedAt = Clock.System.now(),
            )
            releasesRepo.insertRelease(release)
            releasesRepo.batchUpsertBlockExecutions(release.id, release.dagSnapshot.blocks)

            engine.startExecution(release)

            // Wait for both blocks to start (parallel)
            awaitBlockStatus(releasesRepo, release.id, BlockId("a"), BlockStatus.RUNNING)
            awaitBlockStatus(releasesRepo, release.id, BlockId("b"), BlockStatus.RUNNING)

            // Stop via block A — should stop entire release (both blocks)
            val stopped = engine.stopBlock(release.id, BlockId("a"))
            assertTrue(stopped)

            val finalRelease = releasesRepo.findById(release.id) ?: fail("Release must exist")
            assertEquals(ReleaseStatus.STOPPED, finalRelease.status)

            val aExec = releasesRepo.findBlockExecution(release.id, BlockId("a")) ?: fail("Block A must exist")
            val bExec = releasesRepo.findBlockExecution(release.id, BlockId("b")) ?: fail("Block B must exist")
            assertEquals(BlockStatus.STOPPED, aExec.status, "Block A must be STOPPED")
            assertEquals(BlockStatus.STOPPED, bExec.status, "Block B must also be STOPPED (full release stop)")

            // cancel() must be called for every RUNNING block, not just the target block
            assertTrue(executor.wasCancelled(BlockId("a")), "cancel() must be called for block A")
            assertTrue(executor.wasCancelled(BlockId("b")), "cancel() must be called for block B")
        }
    }

    @Test
    fun `resume when no stopped blocks re-executes all WAITING blocks and completes`() = runBlocking {
        withTestSetup {
            // Scenario: stopRelease was called on a release with only WAITING blocks (e.g. stop right after start).
            // No blocks got stopped — only the release status changed to STOPPED.
            // On resume, the WAITING block must execute and the release must complete.
            val release = singleBlockRelease(id = "r50", blockId = "a").copy(status = ReleaseStatus.STOPPED)
            releasesRepo.insertRelease(release)
            releasesRepo.upsertBlockExecution(
                BlockExecution(blockId = BlockId("a"), releaseId = release.id, status = BlockStatus.WAITING)
            )

            val resumed = engine.resumeRelease(release.id)
            assertTrue(resumed, "resumeRelease must return true")

            engine.awaitExecution(release.id)

            val finalRelease = releasesRepo.findById(release.id) ?: fail("Release must exist")
            assertEquals(ReleaseStatus.SUCCEEDED, finalRelease.status, "Release must succeed after resume of WAITING block")

            val exec = releasesRepo.findBlockExecution(release.id, BlockId("a")) ?: fail("Block exec must exist")
            assertEquals(BlockStatus.SUCCEEDED, exec.status, "WAITING block must execute and succeed after resume")
        }
    }

    data class EngineTestSetup(
        val releasesRepo: InMemoryReleasesRepository,
        val engine: ExecutionEngine,
        val recoveryService: RecoveryService,
        val scope: CoroutineScope,
    )
}

/**
 * Blocking executor for stop/resume tests.
 *
 * Each block suspends in [execute] on a per-block [CompletableDeferred] until one of:
 *  - [complete] is called: the deferred is resolved and execute() returns the given outputs.
 *  - [fail] is called: the deferred fails with an exception, causing the block to fail.
 *  - The engine cancels the coroutine (CancellationException propagates out of await()).
 *
 * Note: [cancel] records the call for assertion purposes only. Coroutine cancellation is done
 * by the engine via job.cancel(); [cancel] is for notifying external systems (TeamCity, GitHub).
 */
class ControllableBlockExecutor : BlockExecutor {
    private val started = ConcurrentHashMap<String, Unit>()
    private val cancelled = ConcurrentHashMap<String, Unit>()
    private val gates = ConcurrentHashMap<String, CompletableDeferred<Map<String, String>>>()

    private fun gate(blockId: BlockId): CompletableDeferred<Map<String, String>> =
        gates.getOrPut(blockId.value) { CompletableDeferred() }

    /** Unblock the given block so execute() returns successfully with [outputs]. */
    fun complete(blockId: BlockId, outputs: Map<String, String> = emptyMap()) {
        gate(blockId).complete(outputs)
    }

    /** Make the given block's execute() throw, simulating an executor failure. */
    fun fail(blockId: BlockId, message: String = "Simulated executor failure") {
        gate(blockId).completeExceptionally(RuntimeException(message))
    }

    fun wasCancelled(blockId: BlockId) = cancelled.containsKey(blockId.value)

    override suspend fun execute(
        block: Block.ActionBlock,
        parameters: List<Parameter>,
        context: ExecutionContext,
        scope: ExecutionScope?,
    ): Map<String, String> {
        started[block.id.value] = Unit
        return gate(block.id).await()
    }

    override suspend fun cancel(
        block: Block.ActionBlock,
        context: ExecutionContext,
    ) {
        cancelled[block.id.value] = Unit
    }
}
