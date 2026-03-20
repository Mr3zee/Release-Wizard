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
    data class TooManyBlocks(val count: Int, val max: Int) : ValidationError()
    data class TooManyEdges(val count: Int, val max: Int) : ValidationError()
    data class NestingTooDeep(val depth: Int, val max: Int) : ValidationError()
    data class BlockNameTooLong(val blockId: BlockId, val length: Int, val max: Int) : ValidationError()
    data class TooManyParameters(val blockId: BlockId, val count: Int, val max: Int) : ValidationError()
    data class ParameterKeyTooLong(val blockId: BlockId, val key: String, val max: Int) : ValidationError()
    data class ParameterValueTooLong(val blockId: BlockId, val key: String, val max: Int) : ValidationError()
}

object DagValidator {

    const val MAX_BLOCKS = 500
    const val MAX_EDGES = 2000
    const val MAX_NESTING_DEPTH = 10
    const val MAX_BLOCK_NAME_LENGTH = 255
    const val MAX_PARAMETERS_PER_BLOCK = 50
    const val MAX_PARAM_KEY_LENGTH = 255
    const val MAX_PARAM_VALUE_LENGTH = 1000

    fun validate(graph: DagGraph, currentDepth: Int = 0): List<ValidationError> {
        val errors = mutableListOf<ValidationError>()

        if (currentDepth > MAX_NESTING_DEPTH) {
            errors.add(ValidationError.NestingTooDeep(currentDepth, MAX_NESTING_DEPTH))
            return errors
        }

        if (graph.blocks.size > MAX_BLOCKS) {
            errors.add(ValidationError.TooManyBlocks(graph.blocks.size, MAX_BLOCKS))
        }

        if (graph.edges.size > MAX_EDGES) {
            errors.add(ValidationError.TooManyEdges(graph.edges.size, MAX_EDGES))
        }

        val blockIds = mutableSetOf<BlockId>()

        for (block in graph.blocks) {
            if (!blockIds.add(block.id)) {
                errors.add(ValidationError.DuplicateBlockId(block.id))
            }

            if (block.name.length > MAX_BLOCK_NAME_LENGTH) {
                errors.add(ValidationError.BlockNameTooLong(block.id, block.name.length, MAX_BLOCK_NAME_LENGTH))
            }

            if (block is Block.ActionBlock) {
                if (block.parameters.size > MAX_PARAMETERS_PER_BLOCK) {
                    errors.add(ValidationError.TooManyParameters(block.id, block.parameters.size, MAX_PARAMETERS_PER_BLOCK))
                }
                for (param in block.parameters) {
                    if (param.key.length > MAX_PARAM_KEY_LENGTH) {
                        errors.add(ValidationError.ParameterKeyTooLong(block.id, param.key, MAX_PARAM_KEY_LENGTH))
                    }
                    if (param.value.length > MAX_PARAM_VALUE_LENGTH) {
                        errors.add(ValidationError.ParameterValueTooLong(block.id, param.key, MAX_PARAM_VALUE_LENGTH))
                    }
                }
            }
        }

        for (edge in graph.edges) {
            if (edge.fromBlockId == edge.toBlockId) {
                errors.add(ValidationError.SelfLoop(edge))
                continue
            }
            if (edge.fromBlockId !in blockIds) {
                errors.add(ValidationError.InvalidEdgeReference(edge, edge.fromBlockId))
            }
            if (edge.toBlockId !in blockIds) {
                errors.add(ValidationError.InvalidEdgeReference(edge, edge.toBlockId))
            }
        }

        val nonSelfLoopEdges = graph.edges.filter { it.fromBlockId != it.toBlockId }
        val graphForCycleCheck = graph.copy(edges = nonSelfLoopEdges)
        val result = DagTopologicalSort.sortWithCycleInfo(graphForCycleCheck)
        if (result.cycleNodes.isNotEmpty()) {
            errors.add(ValidationError.CycleDetected(result.cycleNodes))
        }

        for (block in graph.blocks) {
            if (block is Block.ContainerBlock && block.children.blocks.isNotEmpty()) {
                val childErrors = validate(block.children, currentDepth + 1)
                errors.addAll(childErrors)
            }
        }

        return errors
    }
}
