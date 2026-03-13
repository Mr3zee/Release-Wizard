package com.github.mr3zee.dag

import com.github.mr3zee.model.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DagTopologicalSortTest {

    private fun actionBlock(id: String) = Block.ActionBlock(
        id = BlockId(id),
        name = id,
        type = BlockType.TEAMCITY_BUILD,
    )

    private fun edge(from: String, to: String) = Edge(BlockId(from), BlockId(to))

    @Test
    fun emptyGraph() {
        val result = DagTopologicalSort.sort(DagGraph())
        assertNotNull(result)
        assertTrue(result.isEmpty())
    }

    @Test
    fun singleNode() {
        val graph = DagGraph(blocks = listOf(actionBlock("a")))
        val result = DagTopologicalSort.sort(graph)
        assertNotNull(result)
        assertEquals(listOf(BlockId("a")), result)
    }

    @Test
    fun linearChain() {
        val graph = DagGraph(
            blocks = listOf(actionBlock("a"), actionBlock("b"), actionBlock("c")),
            edges = listOf(edge("a", "b"), edge("b", "c")),
        )
        val result = DagTopologicalSort.sort(graph)
        assertNotNull(result)
        assertEquals(listOf(BlockId("a"), BlockId("b"), BlockId("c")), result)
    }

    @Test
    fun diamondPreservesOrder() {
        val graph = DagGraph(
            blocks = listOf(
                actionBlock("a"), actionBlock("b"), actionBlock("c"), actionBlock("d"),
            ),
            edges = listOf(
                edge("a", "b"), edge("a", "c"), edge("b", "d"), edge("c", "d"),
            ),
        )
        val result = DagTopologicalSort.sort(graph)
        assertNotNull(result)
        assertEquals(4, result.size)
        // a must come before b, c; b and c must come before d
        assertTrue(result.indexOf(BlockId("a")) < result.indexOf(BlockId("b")))
        assertTrue(result.indexOf(BlockId("a")) < result.indexOf(BlockId("c")))
        assertTrue(result.indexOf(BlockId("b")) < result.indexOf(BlockId("d")))
        assertTrue(result.indexOf(BlockId("c")) < result.indexOf(BlockId("d")))
    }

    @Test
    fun cycleReturnsNull() {
        val graph = DagGraph(
            blocks = listOf(actionBlock("a"), actionBlock("b"), actionBlock("c")),
            edges = listOf(edge("a", "b"), edge("b", "c"), edge("c", "a")),
        )
        assertNull(DagTopologicalSort.sort(graph))
    }

    @Test
    fun disconnectedNodes() {
        val graph = DagGraph(
            blocks = listOf(actionBlock("a"), actionBlock("b"), actionBlock("c")),
        )
        val result = DagTopologicalSort.sort(graph)
        assertNotNull(result)
        assertEquals(3, result.size)
    }

    @Test
    fun partialCycleReturnsNull() {
        // a -> b -> c (valid), d -> e -> d (cycle)
        val graph = DagGraph(
            blocks = listOf(
                actionBlock("a"), actionBlock("b"), actionBlock("c"),
                actionBlock("d"), actionBlock("e"),
            ),
            edges = listOf(
                edge("a", "b"), edge("b", "c"),
                edge("d", "e"), edge("e", "d"),
            ),
        )
        assertNull(DagTopologicalSort.sort(graph))
    }

    // ---- sortWithCycleInfo ----

    @Test
    fun sortWithCycleInfoAcyclicGraphHasNoCycleNodes() {
        val graph = DagGraph(
            blocks = listOf(actionBlock("a"), actionBlock("b"), actionBlock("c")),
            edges = listOf(edge("a", "b"), edge("b", "c")),
        )

        val result = DagTopologicalSort.sortWithCycleInfo(graph)

        assertTrue(result.cycleNodes.isEmpty(), "Acyclic graph should have no cycle nodes")
        assertEquals(3, result.sorted.size)
        assertEquals(3, result.totalBlocks)
        assertTrue(result.sorted.indexOf(BlockId("a")) < result.sorted.indexOf(BlockId("b")))
        assertTrue(result.sorted.indexOf(BlockId("b")) < result.sorted.indexOf(BlockId("c")))
    }

    @Test
    fun sortWithCycleInfoFullCycleReturnsCycleParticipants() {
        val graph = DagGraph(
            blocks = listOf(actionBlock("a"), actionBlock("b"), actionBlock("c")),
            edges = listOf(edge("a", "b"), edge("b", "c"), edge("c", "a")),
        )

        val result = DagTopologicalSort.sortWithCycleInfo(graph)

        assertEquals(
            setOf(BlockId("a"), BlockId("b"), BlockId("c")),
            result.cycleNodes,
        )
        assertTrue(result.sorted.isEmpty(), "No nodes should be sortable in a full cycle")
    }

    @Test
    fun sortWithCycleInfoPartialCycleIdentifiesOnlyCycleNodes() {
        // d -> a -> b -> c -> a (cycle among a, b, c; d is not in the cycle)
        val graph = DagGraph(
            blocks = listOf(
                actionBlock("d"), actionBlock("a"), actionBlock("b"), actionBlock("c"),
            ),
            edges = listOf(
                edge("d", "a"), edge("a", "b"), edge("b", "c"), edge("c", "a"),
            ),
        )

        val result = DagTopologicalSort.sortWithCycleInfo(graph)

        assertTrue(result.sorted.contains(BlockId("d")), "D should be in sorted output")
        assertEquals(
            setOf(BlockId("a"), BlockId("b"), BlockId("c")),
            result.cycleNodes,
        )
    }

    @Test
    fun sortWithCycleInfoEmptyGraphReturnsEmptyResults() {
        val result = DagTopologicalSort.sortWithCycleInfo(DagGraph())

        assertTrue(result.cycleNodes.isEmpty())
        assertTrue(result.sorted.isEmpty())
        assertEquals(0, result.totalBlocks)
    }
}
