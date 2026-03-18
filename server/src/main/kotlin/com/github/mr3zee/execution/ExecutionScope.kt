package com.github.mr3zee.execution

import com.github.mr3zee.model.SubBuild

/**
 * Callback interface passed to executors during execution.
 * Provides methods for persisting intermediate state without
 * overloading the read-only [ExecutionContext].
 */
interface ExecutionScope {
    /**
     * Merge the given outputs into the block's persisted outputs
     * and upsert to the database. Keys starting with '_' are treated
     * as internal and filtered from WebSocket broadcasts.
     */
    suspend fun persistOutputs(outputs: Map<String, String>)

    /**
     * Update the sub-builds list for the current block.
     * // todo claude: unresolved kdoc reference
     * Writes to a dedicated DB column and emits a [BlockExecutionUpdated] event.
     */
    suspend fun updateSubBuilds(subBuilds: List<SubBuild>)
}
