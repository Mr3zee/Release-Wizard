package com.github.mr3zee.model

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
data class BlockExecution(
    val blockId: BlockId,
    val releaseId: ReleaseId,
    val status: BlockStatus,
    val outputs: Map<String, String> = emptyMap(),
    val error: String? = null,
    val startedAt: Instant? = null,
    val finishedAt: Instant? = null,
)
