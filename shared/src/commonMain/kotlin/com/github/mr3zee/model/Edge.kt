package com.github.mr3zee.model

import kotlinx.serialization.Serializable

@Serializable
data class Edge(
    val fromBlockId: BlockId,
    val toBlockId: BlockId,
)
