package com.github.mr3zee.model

import kotlinx.serialization.Serializable

@Serializable
data class BlockPosition(
    val x: Float,
    val y: Float,
)
