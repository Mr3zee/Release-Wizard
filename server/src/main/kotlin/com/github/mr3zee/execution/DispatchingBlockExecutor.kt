package com.github.mr3zee.execution

import com.github.mr3zee.model.Block
import com.github.mr3zee.model.BlockType
import com.github.mr3zee.model.Parameter

/**
 * Routes block execution to type-specific executor implementations.
 * Each executor handles one block type's API interaction.
 */
class DispatchingBlockExecutor(
    private val executors: Map<BlockType, BlockExecutor>,
) : BlockExecutor {

    override suspend fun execute(
        block: Block.ActionBlock,
        parameters: List<Parameter>,
        context: ExecutionContext,
        scope: ExecutionScope?,
    ): Map<String, String> {
        val executor = executors[block.type]
            ?: throw IllegalStateException("No executor registered for block type: ${block.type}")
        return executor.execute(block, parameters, context, scope)
    }

    override suspend fun resume(
        block: Block.ActionBlock,
        parameters: List<Parameter>,
        context: ExecutionContext,
        scope: ExecutionScope?,
    ): Map<String, String> {
        val executor = executors[block.type]
            ?: throw IllegalStateException("No executor registered for block type: ${block.type}")
        return executor.resume(block, parameters, context, scope)
    }
}
