package com.github.mr3zee.dag

import com.github.mr3zee.model.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DagPredecessorsTest {

    private fun actionBlock(id: String, name: String = id) = Block.ActionBlock(
        id = BlockId(id),
        name = name,
        type = BlockType.TEAMCITY_BUILD,
    )

    private fun edge(from: String, to: String) = Edge(BlockId(from), BlockId(to))

    @Test
    fun `finds direct predecessors`() {
        val graph = DagGraph(
            blocks = listOf(actionBlock("A"), actionBlock("B")),
            edges = listOf(edge("A", "B")),
        )
        val predecessors = findPredecessors(graph, BlockId("B"))
        assertEquals(1, predecessors.size)
        assertEquals(BlockId("A"), predecessors[0].id)
    }

    @Test
    fun `finds transitive predecessors`() {
        val graph = DagGraph(
            blocks = listOf(actionBlock("A"), actionBlock("B"), actionBlock("C")),
            edges = listOf(edge("A", "B"), edge("B", "C")),
        )
        val predecessors = findPredecessors(graph, BlockId("C"))
        val predecessorIds = predecessors.map { it.id }.toSet()
        assertEquals(setOf(BlockId("A"), BlockId("B")), predecessorIds)
    }

    @Test
    fun `returns empty for root node`() {
        val graph = DagGraph(
            blocks = listOf(actionBlock("A"), actionBlock("B")),
            edges = listOf(edge("A", "B")),
        )
        val predecessors = findPredecessors(graph, BlockId("A"))
        assertTrue(predecessors.isEmpty())
    }

    @Test
    fun `handles diamond DAG`() {
        val graph = DagGraph(
            blocks = listOf(
                actionBlock("A"), actionBlock("B"), actionBlock("C"), actionBlock("D"),
            ),
            edges = listOf(
                edge("A", "B"), edge("A", "C"), edge("B", "D"), edge("C", "D"),
            ),
        )
        val predecessors = findPredecessors(graph, BlockId("D"))
        val predecessorIds = predecessors.map { it.id }.toSet()
        assertEquals(setOf(BlockId("A"), BlockId("B"), BlockId("C")), predecessorIds)
    }

    @Test
    fun `handles disconnected graph`() {
        val graph = DagGraph(
            blocks = listOf(actionBlock("A"), actionBlock("B"), actionBlock("C")),
            edges = listOf(edge("A", "B")),
        )
        val predecessors = findPredecessors(graph, BlockId("C"))
        assertTrue(predecessors.isEmpty())
    }

    @Test
    fun `handles node not in graph`() {
        val graph = DagGraph(
            blocks = listOf(actionBlock("A"), actionBlock("B")),
            edges = listOf(edge("A", "B")),
        )
        val predecessors = findPredecessors(graph, BlockId("nonexistent"))
        assertTrue(predecessors.isEmpty())
    }
}
