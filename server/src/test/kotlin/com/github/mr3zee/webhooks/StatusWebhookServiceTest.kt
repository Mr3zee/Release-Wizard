package com.github.mr3zee.webhooks

import com.github.mr3zee.WebhookConfig
import com.github.mr3zee.api.StatusUpdatePayload
import com.github.mr3zee.execution.ExecutionEngine
import com.github.mr3zee.execution.FakeConnectionsRepository
import com.github.mr3zee.execution.InMemoryReleasesRepository
import com.github.mr3zee.execution.InMemoryStatusWebhookTokenRepository
import com.github.mr3zee.execution.StubBlockExecutor
import com.github.mr3zee.model.*
import com.github.mr3zee.api.ReleaseEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds

class StatusWebhookServiceTest {

    private val releasesRepo = InMemoryReleasesRepository()
    private val tokenRepo = InMemoryStatusWebhookTokenRepository()
    private val connectionsRepo = FakeConnectionsRepository()
    private val executor = StubBlockExecutor()
    private val scope = CoroutineScope(SupervisorJob())
    private val engine = ExecutionEngine(releasesRepo, executor, connectionsRepo, scope)
    private val webhookConfig = WebhookConfig(baseUrl = "https://example.com")
    private val service = StatusWebhookService(tokenRepo, releasesRepo, lazyOf(engine), webhookConfig)

    @AfterTest
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun `createToken generates unique tokens`() = runBlocking {
        val releaseId = ReleaseId("r1")
        val blockIdA = BlockId("a")
        val blockIdB = BlockId("b")

        val token1 = service.createToken(releaseId, blockIdA)
        val token2 = service.createToken(releaseId, blockIdB)

        assertNotEquals(token1, token2, "Tokens should be unique")
    }

    @Test
    fun `processStatusUpdate accepts valid update`() = runBlocking<Unit> {
        val releaseId = ReleaseId("r1")
        val blockId = BlockId("a")

        val release = Release(
            id = releaseId,
            projectTemplateId = ProjectId("proj-1"),
            status = ReleaseStatus.RUNNING,
            dagSnapshot = DagGraph(
                blocks = listOf(
                    Block.ActionBlock(id = blockId, name = "A", type = BlockType.SLACK_MESSAGE),
                ),
            ),
            startedAt = Clock.System.now(),
        )
        releasesRepo.insertRelease(release)
        releasesRepo.upsertBlockExecution(
            BlockExecution(
                blockId = blockId,
                releaseId = releaseId,
                status = BlockStatus.RUNNING,
                startedAt = Clock.System.now(),
            )
        )

        val token = service.createToken(releaseId, blockId)
        val result = service.processStatusUpdate(token, StatusUpdatePayload(status = "Building"))

        assertIs<StatusWebhookResult.Accepted>(result)
    }

    @Test
    fun `processStatusUpdate rejects unknown token`() = runBlocking<Unit> {
        val randomToken = UUID.randomUUID()
        val result = service.processStatusUpdate(randomToken, StatusUpdatePayload(status = "Building"))

        assertIs<StatusWebhookResult.NotFound>(result)
    }

    @Test
    fun `processStatusUpdate rejects deactivated token`() = runBlocking<Unit> {
        val releaseId = ReleaseId("r1")
        val blockId = BlockId("a")

        val release = Release(
            id = releaseId,
            projectTemplateId = ProjectId("proj-1"),
            status = ReleaseStatus.RUNNING,
            dagSnapshot = DagGraph(
                blocks = listOf(
                    Block.ActionBlock(id = blockId, name = "A", type = BlockType.SLACK_MESSAGE),
                ),
            ),
            startedAt = Clock.System.now(),
        )
        releasesRepo.insertRelease(release)
        releasesRepo.upsertBlockExecution(
            BlockExecution(
                blockId = blockId,
                releaseId = releaseId,
                status = BlockStatus.RUNNING,
                startedAt = Clock.System.now(),
            )
        )

        val token = service.createToken(releaseId, blockId)
        service.deactivateToken(releaseId, blockId)

        val result = service.processStatusUpdate(token, StatusUpdatePayload(status = "Building"))
        assertIs<StatusWebhookResult.NotFound>(result)
    }

    @Test
    fun `processStatusUpdate rejects empty status`() = runBlocking<Unit> {
        val releaseId = ReleaseId("r1")
        val blockId = BlockId("a")

        val release = Release(
            id = releaseId,
            projectTemplateId = ProjectId("proj-1"),
            status = ReleaseStatus.RUNNING,
            dagSnapshot = DagGraph(
                blocks = listOf(
                    Block.ActionBlock(id = blockId, name = "A", type = BlockType.SLACK_MESSAGE),
                ),
            ),
            startedAt = Clock.System.now(),
        )
        releasesRepo.insertRelease(release)
        releasesRepo.upsertBlockExecution(
            BlockExecution(
                blockId = blockId,
                releaseId = releaseId,
                status = BlockStatus.RUNNING,
                startedAt = Clock.System.now(),
            )
        )

        val token = service.createToken(releaseId, blockId)
        val result = service.processStatusUpdate(token, StatusUpdatePayload(status = ""))

        assertIs<StatusWebhookResult.BadRequest>(result)
    }

    @Test
    fun `processStatusUpdate truncates long status`() = runBlocking {
        val releaseId = ReleaseId("r1")
        val blockId = BlockId("a")

        val release = Release(
            id = releaseId,
            projectTemplateId = ProjectId("proj-1"),
            status = ReleaseStatus.RUNNING,
            dagSnapshot = DagGraph(
                blocks = listOf(
                    Block.ActionBlock(id = blockId, name = "A", type = BlockType.SLACK_MESSAGE),
                ),
            ),
            startedAt = Clock.System.now(),
        )
        releasesRepo.insertRelease(release)
        releasesRepo.upsertBlockExecution(
            BlockExecution(
                blockId = blockId,
                releaseId = releaseId,
                status = BlockStatus.RUNNING,
                startedAt = Clock.System.now(),
            )
        )

        val token = service.createToken(releaseId, blockId)
        val longStatus = "x".repeat(500)
        val result = service.processStatusUpdate(token, StatusUpdatePayload(status = longStatus))

        assertIs<StatusWebhookResult.Accepted>(result)

        val execution = releasesRepo.findBlockExecution(releaseId, blockId)
            ?: error("Block execution should exist after status update")
        val webhookStatus = assertNotNull(execution.webhookStatus, "webhookStatus should be set")
        assertEquals(200, webhookStatus.status.length, "Status should be truncated to 200 chars")
    }

    @Test
    fun `processStatusUpdate returns NotFound for non-RUNNING block`() = runBlocking<Unit> {
        val releaseId = ReleaseId("r1")
        val blockId = BlockId("a")

        val release = Release(
            id = releaseId,
            projectTemplateId = ProjectId("proj-1"),
            status = ReleaseStatus.RUNNING,
            dagSnapshot = DagGraph(
                blocks = listOf(
                    Block.ActionBlock(id = blockId, name = "A", type = BlockType.SLACK_MESSAGE),
                ),
            ),
            startedAt = Clock.System.now(),
        )
        releasesRepo.insertRelease(release)
        releasesRepo.upsertBlockExecution(
            BlockExecution(
                blockId = blockId,
                releaseId = releaseId,
                status = BlockStatus.SUCCEEDED,
                startedAt = Clock.System.now(),
                finishedAt = Clock.System.now(),
            )
        )

        val token = service.createToken(releaseId, blockId)
        val result = service.processStatusUpdate(token, StatusUpdatePayload(status = "Building"))

        assertIs<StatusWebhookResult.NotFound>(result)
    }

    @Test
    fun `deactivateToken prevents further updates`() = runBlocking<Unit> {
        val releaseId = ReleaseId("r1")
        val blockId = BlockId("a")

        val release = Release(
            id = releaseId,
            projectTemplateId = ProjectId("proj-1"),
            status = ReleaseStatus.RUNNING,
            dagSnapshot = DagGraph(
                blocks = listOf(
                    Block.ActionBlock(id = blockId, name = "A", type = BlockType.SLACK_MESSAGE),
                ),
            ),
            startedAt = Clock.System.now(),
        )
        releasesRepo.insertRelease(release)
        releasesRepo.upsertBlockExecution(
            BlockExecution(
                blockId = blockId,
                releaseId = releaseId,
                status = BlockStatus.RUNNING,
                startedAt = Clock.System.now(),
            )
        )

        val token = service.createToken(releaseId, blockId)

        // First update should succeed
        val firstResult = service.processStatusUpdate(token, StatusUpdatePayload(status = "Building"))
        assertIs<StatusWebhookResult.Accepted>(firstResult)

        // Deactivate the token
        service.deactivateToken(releaseId, blockId)

        // Second update should be rejected
        val secondResult = service.processStatusUpdate(token, StatusUpdatePayload(status = "Testing"))
        assertIs<StatusWebhookResult.NotFound>(secondResult)
    }

    @Test
    fun `webhookUrl builds correct URL`() {
        val url = service.webhookUrl()
        assertEquals("https://example.com/api/v1/webhooks/status", url)
    }

    @Test
    fun `processStatusUpdate rejects expired token (TTL exceeded)`() = runBlocking<Unit> {
        val releaseId = ReleaseId("r1")
        val blockId = BlockId("a")

        releasesRepo.insertRelease(Release(
            id = releaseId,
            projectTemplateId = ProjectId("proj-1"),
            status = ReleaseStatus.RUNNING,
            dagSnapshot = DagGraph(blocks = listOf(Block.ActionBlock(id = blockId, name = "A", type = BlockType.SLACK_MESSAGE))),
            startedAt = Clock.System.now(),
        ))
        releasesRepo.upsertBlockExecution(BlockExecution(blockId = blockId, releaseId = releaseId, status = BlockStatus.RUNNING, startedAt = Clock.System.now()))

        // Insert a token created 25 hours ago (exceeds 24h TTL)
        val expiredToken = tokenRepo.insertWithCreatedAt(releaseId, blockId, Clock.System.now() - 25.hours)

        val result = service.processStatusUpdate(expiredToken, StatusUpdatePayload(status = "Building"))
        assertIs<StatusWebhookResult.NotFound>(result)
    }

    @Test
    fun `processStatusUpdate accepts token within TTL`() = runBlocking<Unit> {
        val releaseId = ReleaseId("r1")
        val blockId = BlockId("a")

        releasesRepo.insertRelease(Release(
            id = releaseId,
            projectTemplateId = ProjectId("proj-1"),
            status = ReleaseStatus.RUNNING,
            dagSnapshot = DagGraph(blocks = listOf(Block.ActionBlock(id = blockId, name = "A", type = BlockType.SLACK_MESSAGE))),
            startedAt = Clock.System.now(),
        ))
        releasesRepo.upsertBlockExecution(BlockExecution(blockId = blockId, releaseId = releaseId, status = BlockStatus.RUNNING, startedAt = Clock.System.now()))

        // Insert a token created 23 hours ago (within 24h TTL)
        val validToken = tokenRepo.insertWithCreatedAt(releaseId, blockId, Clock.System.now() - 23.hours)

        val result = service.processStatusUpdate(validToken, StatusUpdatePayload(status = "Building"))
        assertIs<StatusWebhookResult.Accepted>(result)
    }

    @Test
    fun `cleanupExpiredTokens deactivates expired and deletes old inactive`() = runBlocking {
        val releaseId = ReleaseId("r1")
        val blockA = BlockId("a")
        val blockB = BlockId("b")
        val blockC = BlockId("c")

        // Token A: active, created 25h ago → should be deactivated
        val tokenA = tokenRepo.insertWithCreatedAt(releaseId, blockA, Clock.System.now() - 25.hours)
        // Token B: active, created 1h ago → should remain active
        val tokenB = tokenRepo.insertWithCreatedAt(releaseId, blockB, Clock.System.now() - 1.hours)
        // Token C: manually deactivate, created 31 days ago → should be deleted
        val tokenC = tokenRepo.insertWithCreatedAt(releaseId, blockC, Clock.System.now() - (31 * 24).hours)
        tokenRepo.deactivate(releaseId, blockC)

        service.cleanupExpiredTokens()

        // Token A should be deactivated
        val recordA = tokenRepo.findByToken(tokenA)
        assertNotNull(recordA)
        assertEquals(false, recordA.active, "Expired token should be deactivated")

        // Token B should still be active
        val recordB = tokenRepo.findByToken(tokenB)
        assertNotNull(recordB)
        assertEquals(true, recordB.active, "Valid token should remain active")

        // Token C should be deleted
        val recordC = tokenRepo.findByToken(tokenC)
        assertEquals(null, recordC, "Old inactive token should be deleted")
    }

    @Test
    fun `processStatusUpdate truncates long description`() = runBlocking {
        val releaseId = ReleaseId("r1")
        val blockId = BlockId("a")

        releasesRepo.insertRelease(Release(
            id = releaseId,
            projectTemplateId = ProjectId("proj-1"),
            status = ReleaseStatus.RUNNING,
            dagSnapshot = DagGraph(blocks = listOf(Block.ActionBlock(id = blockId, name = "A", type = BlockType.SLACK_MESSAGE))),
            startedAt = Clock.System.now(),
        ))
        releasesRepo.upsertBlockExecution(BlockExecution(blockId = blockId, releaseId = releaseId, status = BlockStatus.RUNNING, startedAt = Clock.System.now()))

        val token = service.createToken(releaseId, blockId)
        val longDescription = "d".repeat(2000)
        val result = service.processStatusUpdate(token, StatusUpdatePayload(status = "OK", description = longDescription))

        assertIs<StatusWebhookResult.Accepted>(result)

        val execution = releasesRepo.findBlockExecution(releaseId, blockId) ?: error("Should exist")
        val ws = assertNotNull(execution.webhookStatus)
        assertEquals(1000, ws.description?.length, "Description should be truncated to 1000 chars")
    }

    @Test
    fun `processStatusUpdate emits block update event`() = runBlocking {
        val releaseId = ReleaseId("r1")
        val blockId = BlockId("a")

        releasesRepo.insertRelease(Release(
            id = releaseId,
            projectTemplateId = ProjectId("proj-1"),
            status = ReleaseStatus.RUNNING,
            dagSnapshot = DagGraph(blocks = listOf(Block.ActionBlock(id = blockId, name = "A", type = BlockType.SLACK_MESSAGE))),
            startedAt = Clock.System.now(),
        ))
        releasesRepo.upsertBlockExecution(BlockExecution(blockId = blockId, releaseId = releaseId, status = BlockStatus.RUNNING, startedAt = Clock.System.now()))

        val token = service.createToken(releaseId, blockId)

        // Subscribe to engine events BEFORE calling processStatusUpdate to prevent race
        val eventDeferred = async(start = CoroutineStart.UNDISPATCHED) {
            withTimeoutOrNull(5000.milliseconds) {
                engine.events.first { it is ReleaseEvent.BlockExecutionUpdated }
            }
        }

        val result = service.processStatusUpdate(token, StatusUpdatePayload(status = "Building"))
        assertIs<StatusWebhookResult.Accepted>(result)

        val event = eventDeferred.await()
        assertNotNull(event, "Should have received a block update event within timeout")
        assertIs<ReleaseEvent.BlockExecutionUpdated>(event)
        assertEquals(releaseId, event.releaseId)
        val webhookStatus = assertNotNull(event.blockExecution.webhookStatus, "Event should carry webhookStatus")
        assertEquals("Building", webhookStatus.status)
    }

    @Test
    fun `processStatusUpdate rejects whitespace-only status`() = runBlocking<Unit> {
        val releaseId = ReleaseId("r1")
        val blockId = BlockId("a")

        releasesRepo.insertRelease(Release(
            id = releaseId,
            projectTemplateId = ProjectId("proj-1"),
            status = ReleaseStatus.RUNNING,
            dagSnapshot = DagGraph(blocks = listOf(Block.ActionBlock(id = blockId, name = "A", type = BlockType.SLACK_MESSAGE))),
            startedAt = Clock.System.now(),
        ))
        releasesRepo.upsertBlockExecution(BlockExecution(blockId = blockId, releaseId = releaseId, status = BlockStatus.RUNNING, startedAt = Clock.System.now()))

        val token = service.createToken(releaseId, blockId)
        val result = service.processStatusUpdate(token, StatusUpdatePayload(status = "   "))

        assertIs<StatusWebhookResult.BadRequest>(result)
    }
}
