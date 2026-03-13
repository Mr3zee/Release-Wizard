package com.github.mr3zee.execution

import com.github.mr3zee.model.Block
import com.github.mr3zee.model.BlockType
import com.github.mr3zee.model.Parameter
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

/**
 * Stub executor for tests. Returns simulated outputs based on block type.
 */
class StubBlockExecutor : BlockExecutor {
    override suspend fun execute(
        block: Block.ActionBlock,
        parameters: List<Parameter>,
        context: ExecutionContext,
    ): Map<String, String> {
        // Simulate some work
        delay(100.milliseconds)

        return when (block.type) {
            BlockType.TEAMCITY_BUILD -> mapOf(
                "buildNumber" to "42",
                "buildUrl" to "https://tc.example.com/build/42",
                "buildStatus" to "SUCCESS",
            )
            BlockType.GITHUB_ACTION -> mapOf(
                "runId" to "123456",
                "runUrl" to "https://github.com/owner/repo/actions/runs/123456",
            )
            BlockType.GITHUB_PUBLICATION -> mapOf(
                "releaseUrl" to "https://github.com/owner/repo/releases/tag/v1.0.0",
                "tagName" to "v1.0.0",
            )
            BlockType.MAVEN_CENTRAL_PUBLICATION -> mapOf(
                "repositoryId" to "comexample-1001",
                "status" to "PUBLISHED",
            )
            BlockType.SLACK_MESSAGE -> mapOf(
                "messageTs" to "1234567890.123456",
                "channel" to "#releases",
            )
            BlockType.USER_ACTION -> emptyMap() // Handled separately by ExecutionEngine
        }
    }
}
