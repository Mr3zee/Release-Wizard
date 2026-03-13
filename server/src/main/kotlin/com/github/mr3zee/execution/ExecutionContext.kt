package com.github.mr3zee.execution

import com.github.mr3zee.model.*

/**
 * Context available to block executors during execution.
 */
data class ExecutionContext(
    val releaseId: ReleaseId,
    val parameters: List<Parameter>,
    val blockOutputs: Map<BlockId, Map<String, String>>,
    val connections: Map<ConnectionId, ConnectionConfig>,
)
