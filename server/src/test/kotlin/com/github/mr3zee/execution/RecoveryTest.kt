package com.github.mr3zee.execution

import com.github.mr3zee.WebhookConfig
import com.github.mr3zee.model.*
import com.github.mr3zee.releases.ReleasesRepository
import com.github.mr3zee.webhooks.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.yield
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

class RecoveryTest {

    private fun createTestSetup(): TestSetup {
        val releasesRepo = InMemoryReleasesRepository()
        val connectionsRepo = FakeConnectionsRepository()
        val executor = StubBlockExecutor()
        val scope = CoroutineScope(SupervisorJob())
        val engine = ExecutionEngine(releasesRepo, executor, connectionsRepo, scope)
        val statusWebhookService = StatusWebhookService(
            InMemoryStatusWebhookTokenRepository(),
            releasesRepo,
            lazyOf(engine),
            WebhookConfig(baseUrl = "http://localhost:8080"),
        )
        val recoveryService = RecoveryService(releasesRepo, engine, statusWebhookService)
        return TestSetup(releasesRepo, engine, recoveryService, executor, scope)
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

    @Test
    fun `recovery resumes RUNNING release`() = runBlocking {
        withTestSetup {
            // Create a release in RUNNING state with one SUCCEEDED block and one WAITING block
            val release = Release(
                id = ReleaseId("release-1"),
                projectTemplateId = ProjectId("proj-1"),
                status = ReleaseStatus.RUNNING,
                dagSnapshot = DagGraph(
                    blocks = listOf(
                        Block.ActionBlock(id = BlockId("a"), name = "A", type = BlockType.SLACK_MESSAGE),
                        Block.ActionBlock(id = BlockId("b"), name = "B", type = BlockType.SLACK_MESSAGE),
                    ),
                    edges = listOf(Edge(fromBlockId = BlockId("a"), toBlockId = BlockId("b"))),
                ),
                startedAt = Clock.System.now(),
            )
            releasesRepo.insertRelease(release)
            releasesRepo.upsertBlockExecution(
                BlockExecution(
                    blockId = BlockId("a"),
                    releaseId = release.id,
                    status = BlockStatus.SUCCEEDED,
                    outputs = mapOf("repositoryId" to "test", "status" to "PUBLISHED"),
                    startedAt = Clock.System.now(),
                    finishedAt = Clock.System.now(),
                )
            )
            releasesRepo.upsertBlockExecution(
                BlockExecution(
                    blockId = BlockId("b"),
                    releaseId = release.id,
                    status = BlockStatus.WAITING,
                )
            )

            // Run recovery
            recoveryService.recover()

            // Wait for execution to complete
            engine.awaitExecution(release.id)

            // Verify the release completed successfully
            val finalRelease = releasesRepo.findById(release.id)
                ?: fail("Release '${release.id}' should exist after recovery")
            assertEquals(ReleaseStatus.SUCCEEDED, finalRelease.status)

            // Verify block A was skipped (still SUCCEEDED) and B was executed
            val executions = releasesRepo.findBlockExecutions(release.id)
            val aExec = executions.find { it.blockId == BlockId("a") }
                ?: fail("Block execution for 'a' should exist")
            val bExec = executions.find { it.blockId == BlockId("b") }
                ?: fail("Block execution for 'b' should exist")
            assertEquals(BlockStatus.SUCCEEDED, aExec.status)
            assertEquals(BlockStatus.SUCCEEDED, bExec.status)
        }
    }

    @Test
    fun `recovery skips FAILED blocks`() = runBlocking {
        withTestSetup {
            val release = Release(
                id = ReleaseId("release-2"),
                projectTemplateId = ProjectId("proj-1"),
                status = ReleaseStatus.RUNNING,
                dagSnapshot = DagGraph(
                    blocks = listOf(
                        Block.ActionBlock(id = BlockId("a"), name = "A", type = BlockType.SLACK_MESSAGE),
                        Block.ActionBlock(id = BlockId("b"), name = "B", type = BlockType.SLACK_MESSAGE),
                    ),
                    edges = listOf(Edge(fromBlockId = BlockId("a"), toBlockId = BlockId("b"))),
                ),
                startedAt = Clock.System.now(),
            )
            releasesRepo.insertRelease(release)
            releasesRepo.upsertBlockExecution(
                BlockExecution(
                    blockId = BlockId("a"),
                    releaseId = release.id,
                    status = BlockStatus.FAILED,
                    error = "Previous failure",
                )
            )
            releasesRepo.upsertBlockExecution(
                BlockExecution(
                    blockId = BlockId("b"),
                    releaseId = release.id,
                    status = BlockStatus.WAITING,
                )
            )

            recoveryService.recover()
            engine.awaitExecution(release.id)

            val finalRelease = releasesRepo.findById(release.id)
                ?: fail("Release '${release.id}' should exist after recovery")
            assertEquals(ReleaseStatus.FAILED, finalRelease.status)
        }
    }

    @Test
    fun `recovery does not touch completed releases`() = runBlocking {
        withTestSetup {
            // Insert a SUCCEEDED release — should not be recovered
            val release = Release(
                id = ReleaseId("release-3"),
                projectTemplateId = ProjectId("proj-1"),
                status = ReleaseStatus.SUCCEEDED,
                dagSnapshot = DagGraph(
                    blocks = listOf(
                        Block.ActionBlock(id = BlockId("a"), name = "A", type = BlockType.SLACK_MESSAGE),
                    ),
                ),
                startedAt = Clock.System.now(),
                finishedAt = Clock.System.now(),
            )
            releasesRepo.insertRelease(release)

            recoveryService.recover()

            // Should remain SUCCEEDED, no recovery job started
            val finalRelease = releasesRepo.findById(release.id)
                ?: fail("Release '${release.id}' should exist after recovery")
            assertEquals(ReleaseStatus.SUCCEEDED, finalRelease.status)
        }
    }

    @Test
    fun `recovery re-registers gate approvals`() = runBlocking {
        withTestSetup {
            val release = Release(
                id = ReleaseId("release-4"),
                projectTemplateId = ProjectId("proj-1"),
                status = ReleaseStatus.RUNNING,
                dagSnapshot = DagGraph(
                    blocks = listOf(
                        Block.ActionBlock(id = BlockId("approval"), name = "Approve", type = BlockType.SLACK_MESSAGE, preGate = Gate()),
                    ),
                ),
                startedAt = Clock.System.now(),
            )
            releasesRepo.insertRelease(release)
            releasesRepo.upsertBlockExecution(
                BlockExecution(
                    blockId = BlockId("approval"),
                    releaseId = release.id,
                    status = BlockStatus.WAITING_FOR_INPUT,
                    startedAt = Clock.System.now(),
                    gatePhase = GatePhase.PRE,
                    gateMessage = "Approve to start 'Approve'",
                )
            )

            recoveryService.recover()

            // The block should be waiting for approval — retry until the deferred is registered
            val approved = withTimeoutOrNull(5_000.milliseconds) {
                while (!engine.approveBlock(release.id, BlockId("approval"), mapOf("ok" to "true"))) {
                    yield()
                }
                true
            }
            assertEquals(approved, true, "Should be able to approve recovered gated block")

            engine.awaitExecution(release.id)

            val finalRelease = releasesRepo.findById(release.id)
                ?: fail("Release '${release.id}' should exist after recovery")
            assertEquals(ReleaseStatus.SUCCEEDED, finalRelease.status)
        }
    }

    @Test
    fun `recovery re-registers post-gate approvals and completes with stored outputs`() = runBlocking {
        withTestSetup {
            val storedOutputs = mapOf("messageTs" to "123", "channel" to "#releases")
            val release = Release(
                id = ReleaseId("release-5"),
                projectTemplateId = ProjectId("proj-1"),
                status = ReleaseStatus.RUNNING,
                dagSnapshot = DagGraph(
                    blocks = listOf(
                        Block.ActionBlock(id = BlockId("build"), name = "Build", type = BlockType.SLACK_MESSAGE, postGate = Gate()),
                    ),
                ),
                startedAt = Clock.System.now(),
            )
            releasesRepo.insertRelease(release)
            releasesRepo.upsertBlockExecution(
                BlockExecution(
                    blockId = BlockId("build"),
                    releaseId = release.id,
                    status = BlockStatus.WAITING_FOR_INPUT,
                    outputs = storedOutputs,
                    startedAt = Clock.System.now(),
                    gatePhase = GatePhase.POST,
                    gateMessage = "'Build' completed. Review output and approve to continue.",
                )
            )

            recoveryService.recover()

            // Approve the post-gate
            val approved = withTimeoutOrNull(5_000.milliseconds) {
                while (!engine.approveBlock(release.id, BlockId("build"), emptyMap())) {
                    yield()
                }
                true
            }
            assertEquals(approved, true, "Should be able to approve recovered post-gate block")

            engine.awaitExecution(release.id)

            val finalRelease = releasesRepo.findById(release.id)
                ?: fail("Release should exist after recovery")
            assertEquals(ReleaseStatus.SUCCEEDED, finalRelease.status)

            // Outputs should be preserved from before crash, not re-executed
            val finalExec = releasesRepo.findBlockExecution(release.id, BlockId("build"))
                ?: fail("Block execution should exist")
            assertEquals(storedOutputs, finalExec.outputs)
        }
    }

    @Test
    fun `recovery auto-completes gate when threshold already met before crash`() = runBlocking {
        withTestSetup {
            val release = Release(
                id = ReleaseId("release-6"),
                projectTemplateId = ProjectId("proj-1"),
                status = ReleaseStatus.RUNNING,
                dagSnapshot = DagGraph(
                    blocks = listOf(
                        Block.ActionBlock(id = BlockId("action"), name = "Approve", type = BlockType.SLACK_MESSAGE, preGate = Gate()),
                    ),
                ),
                startedAt = Clock.System.now(),
            )
            releasesRepo.insertRelease(release)
            releasesRepo.upsertBlockExecution(
                BlockExecution(
                    blockId = BlockId("action"),
                    releaseId = release.id,
                    status = BlockStatus.WAITING_FOR_INPUT,
                    startedAt = Clock.System.now(),
                    gatePhase = GatePhase.PRE,
                    gateMessage = "Approve to start 'Approve'",
                    approvals = listOf(
                        BlockApproval(userId = "admin", username = "admin", approvedAt = Clock.System.now().toEpochMilliseconds()),
                    ),
                )
            )

            recoveryService.recover()

            // Should auto-complete because threshold (1) is already met — no manual approval needed
            engine.awaitExecution(release.id)

            val finalRelease = releasesRepo.findById(release.id)
                ?: fail("Release should exist after recovery")
            assertEquals(ReleaseStatus.SUCCEEDED, finalRelease.status)
        }
    }

    @Test
    fun `restart failed block re-executes it and dependents`() = runBlocking {
        withTestSetup {
            val release = Release(
                id = ReleaseId("release-7"),
                projectTemplateId = ProjectId("proj-1"),
                status = ReleaseStatus.RUNNING,
                dagSnapshot = DagGraph(
                    blocks = listOf(
                        Block.ActionBlock(id = BlockId("a"), name = "A", type = BlockType.TEAMCITY_BUILD),
                        Block.ActionBlock(id = BlockId("b"), name = "B", type = BlockType.SLACK_MESSAGE),
                    ),
                    edges = listOf(Edge(fromBlockId = BlockId("a"), toBlockId = BlockId("b"))),
                ),
                startedAt = Clock.System.now(),
            )
            releasesRepo.insertRelease(release)
            // A succeeded, B failed
            releasesRepo.upsertBlockExecution(
                BlockExecution(
                    blockId = BlockId("a"),
                    releaseId = release.id,
                    status = BlockStatus.SUCCEEDED,
                    outputs = mapOf("buildNumber" to "42"),
                    startedAt = Clock.System.now(),
                    finishedAt = Clock.System.now(),
                )
            )
            releasesRepo.upsertBlockExecution(
                BlockExecution(
                    blockId = BlockId("b"),
                    releaseId = release.id,
                    status = BlockStatus.FAILED,
                    error = "Slack API error",
                    startedAt = Clock.System.now(),
                    finishedAt = Clock.System.now(),
                )
            )

            // Restart block B
            val restarted = engine.restartBlock(release.id, BlockId("b"))
            assertTrue(restarted, "restartBlock should return true")

            engine.awaitExecution(release.id)

            val finalRelease = releasesRepo.findById(release.id)
                ?: fail("Release should exist after restart")
            assertEquals(ReleaseStatus.SUCCEEDED, finalRelease.status)

            // B should now be succeeded
            val bExec = releasesRepo.findBlockExecution(release.id, BlockId("b"))
                ?: fail("Block B execution should exist")
            assertEquals(BlockStatus.SUCCEEDED, bExec.status)

            // A should still have its original outputs (not re-executed)
            val aExec = releasesRepo.findBlockExecution(release.id, BlockId("a"))
                ?: fail("Block A execution should exist")
            assertEquals(BlockStatus.SUCCEEDED, aExec.status)
            assertEquals("42", aExec.outputs["buildNumber"])
        }
    }

    // --- Test helpers ---

    data class TestSetup(
        val releasesRepo: InMemoryReleasesRepository,
        val engine: ExecutionEngine,
        val recoveryService: RecoveryService,
        val executor: StubBlockExecutor,
        val scope: CoroutineScope,
    )
}

/**
 * In-memory ReleasesRepository for recovery tests.
 */
class InMemoryReleasesRepository : ReleasesRepository {
    private val lock = Any()
    private val releases = ConcurrentHashMap<ReleaseId, Release>()
    private val executions = mutableListOf<BlockExecution>()

    fun insertRelease(release: Release) {
        releases[release.id] = release
    }

    override suspend fun findAll(includeArchived: Boolean, teamId: String?, teamIds: List<String>?, offset: Int, limit: Int, search: String?, status: ReleaseStatus?, projectTemplateId: ProjectId?, releaseIds: Set<String>?): List<Release> {
        var result = releases.values.toList()
        if (!includeArchived) result = result.filter { it.status != ReleaseStatus.ARCHIVED }
        if (status != null) result = result.filter { it.status == status }
        if (projectTemplateId != null) result = result.filter { it.projectTemplateId == projectTemplateId }
        return result
    }

    override suspend fun countAll(includeArchived: Boolean, teamId: String?, teamIds: List<String>?, search: String?, status: ReleaseStatus?, projectTemplateId: ProjectId?, releaseIds: Set<String>?): Long {
        var result = releases.values.toList()
        if (!includeArchived) result = result.filter { it.status != ReleaseStatus.ARCHIVED }
        if (status != null) result = result.filter { it.status == status }
        if (projectTemplateId != null) result = result.filter { it.projectTemplateId == projectTemplateId }
        return result.size.toLong()
    }

    override suspend fun findAllWithCount(includeArchived: Boolean, teamId: String?, teamIds: List<String>?, offset: Int, limit: Int, search: String?, status: ReleaseStatus?, projectTemplateId: ProjectId?, releaseIds: Set<String>?): Pair<List<Release>, Long> {
        val filtered = findAll(includeArchived, teamId, teamIds, offset, limit, search, status, projectTemplateId, releaseIds)
        return filtered to filtered.size.toLong()
    }
    override suspend fun findById(id: ReleaseId) = releases[id]
    override suspend fun findTeamId(id: ReleaseId): String? = null
    override suspend fun findByProjectId(projectId: ProjectId) =
        releases.values.filter { it.projectTemplateId == projectId }
    override suspend fun findByStatuses(statuses: Set<ReleaseStatus>) =
        releases.values.filter { it.status in statuses }

    override suspend fun create(projectTemplateId: ProjectId, dagSnapshot: DagGraph, parameters: List<Parameter>, teamId: String): Release {
        val release = Release(
            id = ReleaseId(UUID.randomUUID().toString()),
            projectTemplateId = projectTemplateId,
            status = ReleaseStatus.PENDING,
            dagSnapshot = dagSnapshot,
            parameters = parameters,
        )
        releases[release.id] = release
        return release
    }

    override suspend fun updateStatus(id: ReleaseId, status: ReleaseStatus): Boolean {
        val r = releases[id] ?: return false
        releases[id] = r.copy(status = status)
        return true
    }

    override suspend fun setStarted(id: ReleaseId): Boolean {
        val r = releases[id] ?: return false
        releases[id] = r.copy(status = ReleaseStatus.RUNNING, startedAt = Clock.System.now())
        return true
    }

    override suspend fun setFinished(id: ReleaseId, status: ReleaseStatus): Boolean {
        val r = releases[id] ?: return false
        releases[id] = r.copy(status = status, finishedAt = Clock.System.now())
        return true
    }

    override suspend fun delete(id: ReleaseId): Boolean {
        synchronized(lock) { executions.removeAll { it.releaseId == id } }
        return releases.remove(id) != null
    }

    override suspend fun findBlockExecutions(releaseId: ReleaseId) =
        synchronized(lock) { executions.filter { it.releaseId == releaseId } }

    override suspend fun findBlockExecution(releaseId: ReleaseId, blockId: BlockId) =
        synchronized(lock) { executions.find { it.releaseId == releaseId && it.blockId == blockId } }

    override suspend fun upsertBlockExecution(execution: BlockExecution) {
        synchronized(lock) {
            val idx = executions.indexOfFirst {
                it.releaseId == execution.releaseId && it.blockId == execution.blockId
            }
            if (idx >= 0) {
                executions[idx] = execution
            } else {
                executions.add(execution)
            }
        }
    }

    override suspend fun updateSubBuilds(
        releaseId: ReleaseId,
        blockId: BlockId,
        subBuilds: List<SubBuild>,
    ): Boolean = false

    override suspend fun batchStopBlocks(releaseId: ReleaseId, blockIds: Set<BlockId>, finishedAt: Instant) {
        synchronized(lock) {
            for (i in executions.indices) {
                val exec = executions[i]
                if (exec.releaseId == releaseId && exec.blockId in blockIds) {
                    executions[i] = exec.copy(status = BlockStatus.STOPPED, finishedAt = finishedAt)
                }
            }
        }
        updateStatus(releaseId, ReleaseStatus.STOPPED)
    }

    override suspend fun batchResumeBlocks(releaseId: ReleaseId, blockIds: Set<BlockId>) {
        synchronized(lock) {
            for (i in executions.indices) {
                val exec = executions[i]
                if (exec.releaseId == releaseId && exec.blockId in blockIds) {
                    executions[i] = exec.copy(
                        status = BlockStatus.WAITING,
                        startedAt = null,
                        finishedAt = null,
                        error = null,
                        outputs = emptyMap(),
                    )
                }
            }
        }
        updateStatus(releaseId, ReleaseStatus.RUNNING)
    }

    override suspend fun updateWebhookStatus(
        releaseId: ReleaseId,
        blockId: BlockId,
        status: String,
        description: String?,
        receivedAt: Instant,
    ): BlockExecution? {
        synchronized(lock) {
            val idx = executions.indexOfFirst {
                it.releaseId == releaseId && it.blockId == blockId && it.status == BlockStatus.RUNNING
            }
            if (idx < 0) return null
            val updated = executions[idx].copy(
                webhookStatus = WebhookStatusUpdate(
                    status = status,
                    description = description,
                    receivedAt = receivedAt,
                )
            )
            executions[idx] = updated
            return updated
        }
    }
}

/**
 * In-memory StatusWebhookTokenRepository for tests.
 */
class InMemoryStatusWebhookTokenRepository : StatusWebhookTokenRepository {
    private val tokens = mutableListOf<StatusWebhookToken>()

    /** Insert a token with a custom createdAt for TTL testing. */
    fun insertWithCreatedAt(releaseId: ReleaseId, blockId: BlockId, createdAt: Instant): UUID {
        val token = UUID.randomUUID()
        tokens.add(StatusWebhookToken(token, releaseId, blockId, active = true, createdAt = createdAt))
        return token
    }

    override suspend fun create(releaseId: ReleaseId, blockId: BlockId): UUID {
        val token = UUID.randomUUID()
        tokens.add(StatusWebhookToken(token, releaseId, blockId, active = true, createdAt = Clock.System.now()))
        return token
    }

    override suspend fun findByToken(token: UUID): StatusWebhookToken? =
        tokens.find { it.token == token }

    override suspend fun deactivate(releaseId: ReleaseId, blockId: BlockId) {
        val idx = tokens.indexOfFirst { it.releaseId == releaseId && it.blockId == blockId && it.active }
        if (idx >= 0) tokens[idx] = tokens[idx].copy(active = false)
    }

    override suspend fun deactivateExpiredBefore(cutoff: Instant): Int {
        var count = 0
        for (i in tokens.indices) {
            if (tokens[i].active && tokens[i].createdAt < cutoff) {
                tokens[i] = tokens[i].copy(active = false)
                count++
            }
        }
        return count
    }

    override suspend fun deleteInactiveBefore(cutoff: Instant): Int {
        val before = tokens.size
        tokens.removeAll { !it.active && it.createdAt < cutoff }
        return before - tokens.size
    }
}
