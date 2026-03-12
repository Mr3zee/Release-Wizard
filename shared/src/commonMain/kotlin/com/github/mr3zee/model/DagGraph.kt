package com.github.mr3zee.model

import kotlinx.serialization.Serializable

@Serializable
data class DagGraph(
    val blocks: List<Block> = emptyList(),
    val edges: List<Edge> = emptyList(),
    val positions: Map<BlockId, BlockPosition> = emptyMap(),
)
