package com.github.mr3zee.model

/**
 * Recursively search for an [Block.ActionBlock] by [blockId] in this graph,
 * including inside nested container blocks.
 */
fun DagGraph.findActionBlock(blockId: BlockId): Block.ActionBlock? {
    for (block in blocks) {
        when (block) {
            is Block.ActionBlock -> if (block.id == blockId) return block
            is Block.ContainerBlock -> {
                block.children.findActionBlock(blockId)?.let { return it }
            }
        }
    }
    return null
}
