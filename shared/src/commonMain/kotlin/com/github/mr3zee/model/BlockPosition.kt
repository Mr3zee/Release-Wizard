package com.github.mr3zee.model

import kotlinx.serialization.Serializable

@Serializable
data class BlockPosition(
    val x: Float,
    val y: Float,
    val width: Float = DEFAULT_BLOCK_WIDTH,
    val height: Float = DEFAULT_BLOCK_HEIGHT,
) {
    companion object {
        const val DEFAULT_BLOCK_WIDTH = 180f
        const val DEFAULT_BLOCK_HEIGHT = 70f
        const val DEFAULT_CONTAINER_WIDTH = 400f
        const val DEFAULT_CONTAINER_HEIGHT = 200f
        const val CONTAINER_HEADER_HEIGHT = 30f
    }
}
