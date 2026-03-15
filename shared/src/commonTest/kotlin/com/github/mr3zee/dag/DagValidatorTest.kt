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
    fun `empty graph is valid`() {
        val errors = DagValidator.validate(DagGraph())
        assertTrue(errors.isEmpty())
    }

    @Test
    fun `linear graph is valid`() {
        val graph = DagGraph(
            blocks = listOf(actionBlock("a"), actionBlock("b"), actionBlock("c")),
            edges = listOf(edge("a", "b"), edge("b", "c")),
        )
        assertTrue(DagValidator.validate(graph).isEmpty())
    }

    @Test
    fun `diamond graph is valid`() {
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
    fun `detects self loop`() {
        val graph = DagGraph(
            blocks = listOf(actionBlock("a")),
            edges = listOf(edge("a", "a")),
        )
        val errors = DagValidator.validate(graph)
        assertEquals(1, errors.size)
        assertTrue(errors[0] is ValidationError.SelfLoop)
    }

    @Test
    fun `detects cycle`() {
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
    fun `detects duplicate block id`() {
        val graph = DagGraph(
            blocks = listOf(actionBlock("a"), actionBlock("a")),
        )
        val errors = DagValidator.validate(graph)
        assertTrue(errors.any { it is ValidationError.DuplicateBlockId })
    }

    @Test
    fun `detects invalid edge reference`() {
        val graph = DagGraph(
            blocks = listOf(actionBlock("a")),
            edges = listOf(edge("a", "missing")),
        )
        val errors = DagValidator.validate(graph)
        assertTrue(errors.any { it is ValidationError.InvalidEdgeReference })
    }

    @Test
    fun `validates container children`() {
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
    fun `deeply nested containers are valid`() {
        val innerChild = DagGraph(
            blocks = listOf(actionBlock("inner-a"), actionBlock("inner-b")),
            edges = listOf(edge("inner-a", "inner-b")),
        )
        val innerContainer = Block.ContainerBlock(
            id = BlockId("inner-container"),
            name = "Inner",
            children = innerChild,
        )
        val outerChild = DagGraph(
            blocks = listOf(innerContainer, actionBlock("outer-sibling")),
            edges = emptyList(),
        )
        val outerContainer = Block.ContainerBlock(
            id = BlockId("outer-container"),
            name = "Outer",
            children = outerChild,
        )
        val graph = DagGraph(blocks = listOf(outerContainer, actionBlock("root")))
        assertTrue(DagValidator.validate(graph).isEmpty())
    }

    @Test
    fun `cycle in deeply nested container detected`() {
        val innerChild = DagGraph(
            blocks = listOf(actionBlock("deep-a"), actionBlock("deep-b")),
            edges = listOf(edge("deep-a", "deep-b"), edge("deep-b", "deep-a")),
        )
        val innerContainer = Block.ContainerBlock(
            id = BlockId("inner-container"),
            name = "Inner",
            children = innerChild,
        )
        val outerChild = DagGraph(
            blocks = listOf(innerContainer),
        )
        val outerContainer = Block.ContainerBlock(
            id = BlockId("outer-container"),
            name = "Outer",
            children = outerChild,
        )
        val graph = DagGraph(blocks = listOf(outerContainer))
        val errors = DagValidator.validate(graph)
        assertTrue(errors.any { it is ValidationError.CycleDetected })
    }

    @Test
    fun `cross-level edge references detected as invalid`() {
        val childGraph = DagGraph(
            blocks = listOf(actionBlock("child-a")),
        )
        val container = Block.ContainerBlock(
            id = BlockId("container"),
            name = "Group",
            children = childGraph,
        )
        // Edge from top-level block to child-level block (cross-level reference)
        val graph = DagGraph(
            blocks = listOf(actionBlock("top"), container),
            edges = listOf(edge("top", "child-a")),
        )
        val errors = DagValidator.validate(graph)
        assertTrue(errors.any { it is ValidationError.InvalidEdgeReference })
    }

    @Test
    fun `disconnected blocks are valid`() {
        val graph = DagGraph(
            blocks = listOf(actionBlock("a"), actionBlock("b"), actionBlock("c")),
            edges = emptyList(),
        )
        assertTrue(DagValidator.validate(graph).isEmpty())
    }
}
