package com.github.mr3zee.execution

import com.github.mr3zee.model.Block
import com.github.mr3zee.model.Parameter

/**
 * Interface for executing a single action block.
 * Implementations will be provided for each block type in Phase 6 (Integrations).
 */
interface BlockExecutor {
    /**
     * Execute the block and return its outputs.
     * Throw an exception to signal failure.
     */
    suspend fun execute(
        block: Block.ActionBlock,
        parameters: List<Parameter>,
        context: ExecutionContext,
    ): Map<String, String>
}
