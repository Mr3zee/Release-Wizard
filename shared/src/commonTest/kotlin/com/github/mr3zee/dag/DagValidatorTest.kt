package com.github.mr3zee.dag

import com.github.mr3zee.model.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DagValidatorTest {

    private fun actionBlock(id: String, name: String = id) = Block.ActionBlock(
        id = BlockId(id),
        name = name,
        type = BlockType.TEAMCITY_BUILD,
    )

    private fun edge(from: String, to: String) = Edge(BlockId(from), BlockId(to))

    @Test
    fun emptyGraphIsValid() {
        val errors = DagValidator.validate(DagGraph())
        assertTrue(errors.isEmpty())
    }

    @Test
    fun linearGraphIsValid() {
        val graph = DagGraph(
            blocks = listOf(actionBlock("a"), actionBlock("b"), actionBlock("c")),
            edges = listOf(edge("a", "b"), edge("b", "c")),
        )
        assertTrue(DagValidator.validate(graph).isEmpty())
    }

    @Test
    fun diamondGraphIsValid() {
        val graph = DagGraph(
            blocks = listOf(
                actionBlock("a"), actionBlock("b"), actionBlock("c"), actionBlock("d"),
            ),
            edges = listOf(
                edge("a", "b"), edge("a", "c"), edge("b", "d"), edge("c", "d"),
            ),
        )
        assertTrue(DagValidator.validate(graph).isEmpty())
    }

    @Test
    fun detectsSelfLoop() {
        val graph = DagGraph(
            blocks = listOf(actionBlock("a")),
            edges = listOf(edge("a", "a")),
        )
        val errors = DagValidator.validate(graph)
        assertEquals(1, errors.size)
        assertTrue(errors[0] is ValidationError.SelfLoop)
    }

    @Test
    fun detectsCycle() {
        val graph = DagGraph(
            blocks = listOf(actionBlock("a"), actionBlock("b"), actionBlock("c")),
            edges = listOf(edge("a", "b"), edge("b", "c"), edge("c", "a")),
        )
        val errors = DagValidator.validate(graph)
        assertTrue(errors.any { it is ValidationError.CycleDetected })
        val cycle = errors.filterIsInstance<ValidationError.CycleDetected>().first()
        assertEquals(setOf(BlockId("a"), BlockId("b"), BlockId("c")), cycle.involvedBlockIds)
    }

    @Test
    fun detectsDuplicateBlockId() {
        val graph = DagGraph(
            blocks = listOf(actionBlock("a"), actionBlock("a")),
        )
        val errors = DagValidator.validate(graph)
        assertTrue(errors.any { it is ValidationError.DuplicateBlockId })
    }

    @Test
    fun detectsInvalidEdgeReference() {
        val graph = DagGraph(
            blocks = listOf(actionBlock("a")),
            edges = listOf(edge("a", "missing")),
        )
        val errors = DagValidator.validate(graph)
        assertTrue(errors.any { it is ValidationError.InvalidEdgeReference })
    }

    @Test
    fun validatesContainerChildren() {
        val childGraph = DagGraph(
            blocks = listOf(actionBlock("c1"), actionBlock("c2")),
            edges = listOf(edge("c1", "c2"), edge("c2", "c1")), // cycle in children
        )
        val container = Block.ContainerBlock(
            id = BlockId("container"),
            name = "Group",
            children = childGraph,
        )
        val graph = DagGraph(blocks = listOf(container))
        val errors = DagValidator.validate(graph)
        assertTrue(errors.any { it is ValidationError.CycleDetected })
    }

    @Test
    fun disconnectedBlocksAreValid() {
        val graph = DagGraph(
            blocks = listOf(actionBlock("a"), actionBlock("b"), actionBlock("c")),
            edges = emptyList(),
        )
        assertTrue(DagValidator.validate(graph).isEmpty())
    }
}
