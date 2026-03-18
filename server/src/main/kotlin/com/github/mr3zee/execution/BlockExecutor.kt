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
     *
     * @param scope optional callback interface for persisting intermediate state
     */
    suspend fun execute(
        block: Block.ActionBlock,
        parameters: List<Parameter>,
        context: ExecutionContext,
        scope: ExecutionScope? = null,
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
        scope: ExecutionScope? = null,
    ): Map<String, String> = execute(block, parameters, context, scope)

    /**
     * Cancel an in-progress block execution.
     * Called after the release job has been cancelled and joined.
     * Implementations should cancel external resources (builds, workflow runs).
     * Default: no-op (suitable for blocks with no external state to cancel).
     */
    suspend fun cancel(
        block: Block.ActionBlock,
        context: ExecutionContext,
    ) { /* default no-op */ }
}
