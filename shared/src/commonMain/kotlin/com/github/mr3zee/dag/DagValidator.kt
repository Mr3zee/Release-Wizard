package com.github.mr3zee.dag

import com.github.mr3zee.model.Block
import com.github.mr3zee.model.BlockId
import com.github.mr3zee.model.DagGraph
import com.github.mr3zee.model.Edge

sealed class ValidationError {
    data class DuplicateBlockId(val blockId: BlockId) : ValidationError()
    data class SelfLoop(val edge: Edge) : ValidationError()
    data class InvalidEdgeReference(val edge: Edge, val missingBlockId: BlockId) : ValidationError()
    data class CycleDetected(val involvedBlockIds: Set<BlockId>) : ValidationError()
}

object DagValidator {

    fun validate(graph: DagGraph): List<ValidationError> {
        val errors = mutableListOf<ValidationError>()
        val blockIds = mutableSetOf<BlockId>()

        // Check duplicate block IDs
        for (block in graph.blocks) {
            if (!blockIds.add(block.id)) {
                errors.add(ValidationError.DuplicateBlockId(block.id))
            }
        }

        for (edge in graph.edges) {
            // Check self-loops
            if (edge.fromBlockId == edge.toBlockId) {
                errors.add(ValidationError.SelfLoop(edge))
                continue
            }
            // Check edge references
            if (edge.fromBlockId !in blockIds) {
                errors.add(ValidationError.InvalidEdgeReference(edge, edge.fromBlockId))
            }
            if (edge.toBlockId !in blockIds) {
                errors.add(ValidationError.InvalidEdgeReference(edge, edge.toBlockId))
            }
        }

        // Check for cycles using topological sort (exclude self-loops already reported)
        val nonSelfLoopEdges = graph.edges.filter { it.fromBlockId != it.toBlockId }
        val graphForCycleCheck = graph.copy(edges = nonSelfLoopEdges)
        val cycleNodes = findCycleNodes(graphForCycleCheck)
        if (cycleNodes.isNotEmpty()) {
            errors.add(ValidationError.CycleDetected(cycleNodes))
        }

        // Validate container sub-graphs recursively
        for (block in graph.blocks) {
            if (block is Block.ContainerBlock && block.children.blocks.isNotEmpty()) {
                val childErrors = validate(block.children)
                errors.addAll(childErrors)
            }
        }

        return errors
    }

    private fun findCycleNodes(graph: DagGraph): Set<BlockId> {
        val sorted = DagTopologicalSort.sort(graph)
        return if (sorted == null) {
            // Topological sort failed — find nodes involved in cycles
            val blockIds = graph.blocks.map { it.id }.toSet()
            val adjacency = graph.edges.groupBy({ it.fromBlockId }, { it.toBlockId })
            val inDegree = mutableMapOf<BlockId, Int>()
            for (id in blockIds) inDegree[id] = 0
            for (edge in graph.edges) {
                if (edge.toBlockId in blockIds) {
                    inDegree[edge.toBlockId] = (inDegree[edge.toBlockId] ?: 0) + 1
                }
            }
            val queue = ArrayDeque<BlockId>()
            for ((id, degree) in inDegree) {
                if (degree == 0) queue.add(id)
            }
            val processed = mutableSetOf<BlockId>()
            while (queue.isNotEmpty()) {
                val node = queue.removeFirst()
                processed.add(node)
                for (neighbor in adjacency[node].orEmpty()) {
                    val newDegree = (inDegree[neighbor] ?: 1) - 1
                    inDegree[neighbor] = newDegree
                    if (newDegree == 0) queue.add(neighbor)
                }
            }
            blockIds - processed
        } else {
            emptySet()
        }
    }
}
