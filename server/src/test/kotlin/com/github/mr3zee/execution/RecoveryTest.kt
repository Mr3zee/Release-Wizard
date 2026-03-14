package com.github.mr3zee.execution

import com.github.mr3zee.model.*
import com.github.mr3zee.releases.ReleasesRepository
import com.github.mr3zee.webhooks.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days

class RecoveryTest {

    private fun createTestSetup(): TestSetup {
        val releasesRepo = InMemoryReleasesRepository()
        val webhookRepo = InMemoryWebhookRepo()
        val connectionsRepo = FakeConnectionsRepository()
        val executor = StubBlockExecutor()
        val engine = ExecutionEngine(releasesRepo, executor, connectionsRepo, CoroutineScope(SupervisorJob()))
        val recoveryService = RecoveryService(releasesRepo, webhookRepo, engine)
        return TestSetup(releasesRepo, webhookRepo, engine, recoveryService, executor)
    }

    @Test
    fun `recovery resumes RUNNING release`() = runBlocking {
        val setup = createTestSetup()

        // Create a release in RUNNING state with one SUCCEEDED block and one WAITING block
        val release = Release(
            id = ReleaseId("release-1"),
            projectTemplateId = ProjectId("proj-1"),
            status = ReleaseStatus.RUNNING,
            dagSnapshot = DagGraph(
                blocks = listOf(
                    Block.ActionBlock(id = BlockId("a"), name = "A", type = BlockType.MAVEN_CENTRAL_PUBLICATION),
                    Block.ActionBlock(id = BlockId("b"), name = "B", type = BlockType.MAVEN_CENTRAL_PUBLICATION),
                ),
                edges = listOf(Edge(fromBlockId = BlockId("a"), toBlockId = BlockId("b"))),
            ),
            startedAt = Clock.System.now(),
        )
        setup.releasesRepo.insertRelease(release)
        setup.releasesRepo.upsertBlockExecution(
            BlockExecution(
                blockId = BlockId("a"),
                releaseId = release.id,
                status = BlockStatus.SUCCEEDED,
                outputs = mapOf("repositoryId" to "test", "status" to "PUBLISHED"),
                startedAt = Clock.System.now(),
                finishedAt = Clock.System.now(),
            )
        )
        setup.releasesRepo.upsertBlockExecution(
            BlockExecution(
                blockId = BlockId("b"),
                releaseId = release.id,
                status = BlockStatus.WAITING,
            )
        )

        // Run recovery
        setup.recoveryService.recover()

        // Wait for execution to complete
        setup.engine.awaitExecution(release.id)

        // Verify the release completed successfully
        val finalRelease = setup.releasesRepo.findById(release.id)
            ?: error("Release '${release.id}' should exist after recovery")
        assertEquals(ReleaseStatus.SUCCEEDED, finalRelease.status)

        // Verify block A was skipped (still SUCCEEDED) and B was executed
        val executions = setup.releasesRepo.findBlockExecutions(release.id)
        val aExec = executions.find { it.blockId == BlockId("a") }
            ?: error("Block execution for 'a' should exist")
        val bExec = executions.find { it.blockId == BlockId("b") }
            ?: error("Block execution for 'b' should exist")
        assertEquals(BlockStatus.SUCCEEDED, aExec.status)
        assertEquals(BlockStatus.SUCCEEDED, bExec.status)
    }

    @Test
    fun `recovery skips FAILED blocks`() = runBlocking {
        val setup = createTestSetup()

        val release = Release(
            id = ReleaseId("release-2"),
            projectTemplateId = ProjectId("proj-1"),
            status = ReleaseStatus.RUNNING,
            dagSnapshot = DagGraph(
                blocks = listOf(
                    Block.ActionBlock(id = BlockId("a"), name = "A", type = BlockType.MAVEN_CENTRAL_PUBLICATION),
                    Block.ActionBlock(id = BlockId("b"), name = "B", type = BlockType.MAVEN_CENTRAL_PUBLICATION),
                ),
                edges = listOf(Edge(fromBlockId = BlockId("a"), toBlockId = BlockId("b"))),
            ),
            startedAt = Clock.System.now(),
        )
        setup.releasesRepo.insertRelease(release)
        setup.releasesRepo.upsertBlockExecution(
            BlockExecution(
                blockId = BlockId("a"),
                releaseId = release.id,
                status = BlockStatus.FAILED,
                error = "Previous failure",
            )
        )
        setup.releasesRepo.upsertBlockExecution(
            BlockExecution(
                blockId = BlockId("b"),
                releaseId = release.id,
                status = BlockStatus.WAITING,
            )
        )

        setup.recoveryService.recover()
        setup.engine.awaitExecution(release.id)

        val finalRelease = setup.releasesRepo.findById(release.id)
            ?: error("Release '${release.id}' should exist after recovery")
        assertEquals(ReleaseStatus.FAILED, finalRelease.status)
    }

    @Test
    fun `recovery does not touch completed releases`() = runBlocking {
        val setup = createTestSetup()

        // Insert a SUCCEEDED release — should not be recovered
        val release = Release(
            id = ReleaseId("release-3"),
            projectTemplateId = ProjectId("proj-1"),
            status = ReleaseStatus.SUCCEEDED,
            dagSnapshot = DagGraph(
                blocks = listOf(
                    Block.ActionBlock(id = BlockId("a"), name = "A", type = BlockType.MAVEN_CENTRAL_PUBLICATION),
                ),
            ),
            startedAt = Clock.System.now(),
            finishedAt = Clock.System.now(),
        )
        setup.releasesRepo.insertRelease(release)

        setup.recoveryService.recover()

        // Should remain SUCCEEDED, no recovery job started
        val finalRelease = setup.releasesRepo.findById(release.id)
            ?: error("Release '${release.id}' should exist after recovery")
        assertEquals(ReleaseStatus.SUCCEEDED, finalRelease.status)
    }

    @Test
    fun `stale webhook cleanup removes completed webhooks older than 7 days`() = runBlocking {
        val setup = createTestSetup()
        val oldTime = Clock.System.now() - 10.days

        setup.webhookRepo.insertCompleted(
            id = "wh-1",
            externalId = "ext-1",
            blockId = BlockId("a"),
            releaseId = ReleaseId("r1"),
            connectionId = ConnectionId("c1"),
            type = WebhookType.TEAMCITY,
            updatedAt = oldTime,
        )

        val recentTime = Clock.System.now()
        setup.webhookRepo.insertCompleted(
            id = "wh-2",
            externalId = "ext-2",
            blockId = BlockId("b"),
            releaseId = ReleaseId("r1"),
            connectionId = ConnectionId("c1"),
            type = WebhookType.GITHUB,
            updatedAt = recentTime,
        )

        setup.recoveryService.recover()

        // Old completed webhook should be deleted, recent one kept
        assertEquals(1, setup.webhookRepo.size())
    }

    @Test
    fun `recovery re-registers user action approvals`() = runBlocking {
        val setup = createTestSetup()

        val release = Release(
            id = ReleaseId("release-4"),
            projectTemplateId = ProjectId("proj-1"),
            status = ReleaseStatus.RUNNING,
            dagSnapshot = DagGraph(
                blocks = listOf(
                    Block.ActionBlock(id = BlockId("approval"), name = "Approve", type = BlockType.USER_ACTION),
                ),
            ),
            startedAt = Clock.System.now(),
        )
        setup.releasesRepo.insertRelease(release)
        setup.releasesRepo.upsertBlockExecution(
            BlockExecution(
                blockId = BlockId("approval"),
                releaseId = release.id,
                status = BlockStatus.WAITING_FOR_INPUT,
                startedAt = Clock.System.now(),
            )
        )

        setup.recoveryService.recover()

        // The block should be waiting for approval — retry until the deferred is registered
        var approved = false
        repeat(50) {
            kotlinx.coroutines.yield()
            if (setup.engine.approveBlock(release.id, BlockId("approval"), mapOf("ok" to "true"))) {
                approved = true
                return@repeat
            }
        }
        assertTrue(approved, "Should be able to approve recovered user action block")

        setup.engine.awaitExecution(release.id)

        val finalRelease = setup.releasesRepo.findById(release.id)
            ?: error("Release '${release.id}' should exist after recovery")
        assertEquals(ReleaseStatus.SUCCEEDED, finalRelease.status)
    }

    // --- Test helpers ---

    data class TestSetup(
        val releasesRepo: InMemoryReleasesRepository,
        val webhookRepo: InMemoryWebhookRepo,
        val engine: ExecutionEngine,
        val recoveryService: RecoveryService,
        val executor: StubBlockExecutor,
    )
}

/**
 * In-memory ReleasesRepository for recovery tests.
 */
class InMemoryReleasesRepository : ReleasesRepository {
    private val releases = mutableMapOf<ReleaseId, Release>()
    private val executions = mutableListOf<BlockExecution>()

    fun insertRelease(release: Release) {
        releases[release.id] = release
    }

    override suspend fun findAll(includeArchived: Boolean, ownerId: String?, offset: Int, limit: Int, search: String?, status: ReleaseStatus?, projectTemplateId: ProjectId?, releaseIds: Set<String>?) = releases.values.toList()
    override suspend fun countAll(includeArchived: Boolean, ownerId: String?, search: String?, status: ReleaseStatus?, projectTemplateId: ProjectId?, releaseIds: Set<String>?) = releases.size.toLong()
    override suspend fun findAllWithCount(includeArchived: Boolean, ownerId: String?, offset: Int, limit: Int, search: String?, status: ReleaseStatus?, projectTemplateId: ProjectId?, releaseIds: Set<String>?) = releases.values.toList() to releases.size.toLong()
    override suspend fun findById(id: ReleaseId) = releases[id]
    override suspend fun findOwner(id: ReleaseId): String? = null
    override suspend fun findByProjectId(projectId: ProjectId) =
        releases.values.filter { it.projectTemplateId == projectId }
    override suspend fun findByStatuses(statuses: Set<ReleaseStatus>) =
        releases.values.filter { it.status in statuses }

    override suspend fun create(projectTemplateId: ProjectId, dagSnapshot: DagGraph, parameters: List<Parameter>, ownerId: String): Release {
        val release = Release(
            id = ReleaseId(java.util.UUID.randomUUID().toString()),
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
        executions.removeAll { it.releaseId == id }
        return releases.remove(id) != null
    }

    override suspend fun findBlockExecutions(releaseId: ReleaseId) =
        executions.filter { it.releaseId == releaseId }

    override suspend fun findBlockExecution(releaseId: ReleaseId, blockId: BlockId) =
        executions.find { it.releaseId == releaseId && it.blockId == blockId }

    override suspend fun upsertBlockExecution(execution: BlockExecution) {
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

/**
 * In-memory PendingWebhookRepository for recovery tests.
 */
class InMemoryWebhookRepo : PendingWebhookRepository {
    private val webhooks = mutableListOf<PendingWebhook>()

    fun insertCompleted(
        id: String,
        externalId: String,
        blockId: BlockId,
        releaseId: ReleaseId,
        connectionId: ConnectionId,
        type: WebhookType,
        updatedAt: kotlin.time.Instant,
    ) {
        webhooks.add(
            PendingWebhook(
                id = id,
                externalId = externalId,
                blockId = blockId,
                releaseId = releaseId,
                connectionId = connectionId,
                type = type,
                status = WebhookStatus.COMPLETED,
                payload = "{}",
                createdAt = updatedAt,
                updatedAt = updatedAt,
            )
        )
    }

    fun size() = webhooks.size

    override suspend fun create(
        externalId: String, blockId: BlockId, releaseId: ReleaseId,
        connectionId: ConnectionId, type: WebhookType,
    ): PendingWebhook {
        val now = Clock.System.now()
        val wh = PendingWebhook(
            id = "wh-${webhooks.size}", externalId = externalId,
            blockId = blockId, releaseId = releaseId,
            connectionId = connectionId, type = type,
            status = WebhookStatus.PENDING, createdAt = now, updatedAt = now,
        )
        webhooks.add(wh)
        return wh
    }

    override suspend fun findByExternalIdAndType(externalId: String, type: WebhookType) =
        webhooks.find { it.externalId == externalId && it.type == type }

    override suspend fun findPendingByExternalIdAndType(externalId: String, type: WebhookType) =
        webhooks.find { it.externalId == externalId && it.type == type && it.status == WebhookStatus.PENDING }

    override suspend fun findByConnectionIdAndType(connectionId: ConnectionId, type: WebhookType) =
        webhooks.filter { it.connectionId == connectionId && it.type == type && it.status == WebhookStatus.PENDING }

    override suspend fun findPendingByReleaseId(releaseId: ReleaseId) =
        webhooks.filter { it.releaseId == releaseId && it.status == WebhookStatus.PENDING }

    override suspend fun findByReleaseIdAndBlockId(releaseId: ReleaseId, blockId: BlockId) =
        webhooks.find { it.releaseId == releaseId && it.blockId == blockId }

    override suspend fun updateStatus(id: String, status: WebhookStatus, payload: String?): Boolean {
        val idx = webhooks.indexOfFirst { it.id == id }
        if (idx < 0) return false
        webhooks[idx] = webhooks[idx].copy(status = status, payload = payload)
        return true
    }

    override suspend fun updateExternalId(id: String, externalId: String): Boolean {
        val idx = webhooks.indexOfFirst { it.id == id }
        if (idx < 0) return false
        webhooks[idx] = webhooks[idx].copy(externalId = externalId)
        return true
    }

    override suspend fun deleteCompletedOlderThan(cutoff: kotlin.time.Instant): Int {
        val before = webhooks.size
        webhooks.removeAll { it.status == WebhookStatus.COMPLETED && it.updatedAt < cutoff }
        return before - webhooks.size
    }
}
