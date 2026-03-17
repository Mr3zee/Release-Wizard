package com.github.mr3zee.execution

import com.github.mr3zee.model.Block
import com.github.mr3zee.model.BlockId
import com.github.mr3zee.model.Parameter
import java.util.concurrent.ConcurrentHashMap

/**
 * Block executor that captures resolved parameters per block invocation,
 * then delegates to [StubBlockExecutor] for output generation.
 */
class CapturingBlockExecutor : BlockExecutor {
    private val delegate = StubBlockExecutor()
    private val captured = ConcurrentHashMap<BlockId, List<Parameter>>()

    override suspend fun execute(
        block: Block.ActionBlock,
        parameters: List<Parameter>,
        context: ExecutionContext,
        scope: ExecutionScope?,
    ): Map<String, String> {
        captured[block.id] = parameters.toList()
        return delegate.execute(block, parameters, context, scope)
    }

    fun capturedParameters(blockId: BlockId): List<Parameter>? = captured[blockId]
}
