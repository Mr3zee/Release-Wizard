package com.github.mr3zee.execution

import com.github.mr3zee.model.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class TimeoutTest {

    private class SlowBlockExecutor : BlockExecutor {
        override suspend fun execute(
            block: Block.ActionBlock,
            parameters: List<Parameter>,
            context: ExecutionContext,
        ): Map<String, String> {
            delay(2.seconds)
            return emptyMap()
        }
    }

    @Test
    fun `block with timeout fails with descriptive message`() = runBlocking {
        val repo = InMemoryReleasesRepository()
        val engine = ExecutionEngine(repo, SlowBlockExecutor(), FakeConnectionsRepository(), CoroutineScope(SupervisorJob()))

        val release = Release(
            id = ReleaseId("r-timeout"),
            projectTemplateId = ProjectId("p1"),
            status = ReleaseStatus.PENDING,
            dagSnapshot = DagGraph(
                blocks = listOf(
                    Block.ActionBlock(
                        id = BlockId("slow"),
                        name = "Slow Block",
                        type = BlockType.TEAMCITY_BUILD,
                        timeoutSeconds = 1,
                    ),
                ),
            ),
        )
        repo.insertRelease(release)

        engine.startExecution(release)
        engine.awaitExecution(release.id)

        val finalRelease = repo.findById(release.id)
            ?: error("Release '${release.id}' should exist after execution")
        assertEquals(ReleaseStatus.FAILED, finalRelease.status)

        val blockExec = repo.findBlockExecution(release.id, BlockId("slow"))
            ?: error("Block execution for 'slow' should exist after execution")
        assertEquals(BlockStatus.FAILED, blockExec.status)
        val errorMessage = blockExec.error ?: error("Block execution error should not be null for a failed/timed-out block")
        assertTrue(errorMessage.contains("timed out"), "Error should mention timeout: $errorMessage")
    }
}
