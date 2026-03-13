package com.github.mr3zee.dag

import com.github.mr3zee.model.BlockId
import com.github.mr3zee.model.DagGraph

object DagTopologicalSort {

    /**
     * Returns the topologically sorted block IDs using Kahn's algorithm,
     * or null if the graph contains a cycle.
     */
    fun sort(graph: DagGraph): List<BlockId>? {
        val blockIds = graph.blocks.map { it.id }.toSet()
        val adjacency = mutableMapOf<BlockId, MutableList<BlockId>>()
        val inDegree = mutableMapOf<BlockId, Int>()

        for (id in blockIds) {
            adjacency[id] = mutableListOf()
            inDegree[id] = 0
        }

        for (edge in graph.edges) {
            if (edge.fromBlockId in blockIds && edge.toBlockId in blockIds) {
                adjacency.getOrPut(edge.fromBlockId) { mutableListOf() }.add(edge.toBlockId)
                inDegree[edge.toBlockId] = (inDegree[edge.toBlockId] ?: 0) + 1
            }
        }

        val queue = ArrayDeque<BlockId>()
        for ((id, degree) in inDegree) {
            if (degree == 0) queue.add(id)
        }

        val sorted = mutableListOf<BlockId>()
        // todo claude: duplicated with DagValidator.kt:80
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            sorted.add(node)
            for (neighbor in adjacency[node].orEmpty()) {
                val newDegree = (inDegree[neighbor] ?: 1) - 1
                inDegree[neighbor] = newDegree
                if (newDegree == 0) queue.add(neighbor)
            }
        }

        return if (sorted.size == blockIds.size) sorted else null
    }
}
