package com.github.mr3zee.execution

import com.github.mr3zee.model.Block
import com.github.mr3zee.model.Parameter

/**
 * Interface for executing a single action block.
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

    /**
     * Resume a block after server restart.
     * Default implementation calls execute() (safe for idempotent executors).
     * Non-idempotent executors should override with type-specific recovery logic.
     */
    suspend fun resume(
        block: Block.ActionBlock,
        parameters: List<Parameter>,
        context: ExecutionContext,
    ): Map<String, String> = execute(block, parameters, context)
}
