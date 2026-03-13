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
        val result = DagTopologicalSort.sortWithCycleInfo(graphForCycleCheck)
        if (result.cycleNodes.isNotEmpty()) {
            errors.add(ValidationError.CycleDetected(result.cycleNodes))
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
}
