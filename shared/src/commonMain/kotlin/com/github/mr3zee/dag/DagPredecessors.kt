package com.github.mr3zee.dag

import com.github.mr3zee.model.Block
import com.github.mr3zee.model.BlockId
import com.github.mr3zee.model.DagGraph

/**
 * Finds all transitive predecessors of [blockId] in the DAG.
 * Returns blocks that complete before [blockId] runs, making their outputs valid references.
 */
fun findPredecessors(graph: DagGraph, blockId: BlockId): List<Block> {
    val adjacency = mutableMapOf<BlockId, MutableList<BlockId>>()
    for (edge in graph.edges) {
        adjacency.getOrPut(edge.toBlockId) { mutableListOf() }.add(edge.fromBlockId)
    }

    val visited = mutableSetOf<BlockId>()
    val queue = ArrayDeque<BlockId>()

    // Seed with direct predecessors
    adjacency[blockId]?.forEach { pred ->
        if (visited.add(pred)) {
            queue.add(pred)
        }
    }

    // BFS to find transitive predecessors
    while (queue.isNotEmpty()) {
        val current = queue.removeFirst()
        adjacency[current]?.forEach { pred ->
            if (visited.add(pred)) {
                queue.add(pred)
            }
        }
    }

    val blockMap = graph.blocks.associateBy { it.id }
    return visited.mapNotNull { blockMap[it] }
}
